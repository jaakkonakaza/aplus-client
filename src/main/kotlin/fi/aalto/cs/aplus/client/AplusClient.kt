package fi.aalto.cs.aplus.client

import fi.aalto.cs.aplus.client.dto.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A typed client for the A+ LMS REST API at `api/v2`.
 *
 * A supplied [HttpClient] keeps its existing configuration. The client adds authorization
 * headers and decodes responses with [AplusJson]. Use [create] for a configured OkHttp client.
 * For another engine, configure an [HttpClient] with [aplusDefaults] and pass it here.
 *
 * ```kotlin
 * val aplus = AplusClient.create(
 *     baseUrl = "https://plus.cs.aalto.fi",
 *     tokenProvider = { settings.apiToken },
 * )
 * val points = aplus.points(courseId = 3)
 * ```
 *
 * Non-2xx responses raise [AplusException].
 *
 * Listings check every page against the first page's `count` and raise
 * [AplusException.Pagination] on inconsistent or incomplete listings. Numbered pages after the first
 * are fetched concurrently within [AplusLimits.maxConcurrentPages] and transport limits.
 *
 * Every `maxBytes` argument must be positive. It defaults to [AplusLimits.maxResponseBytes] and
 * may exceed it, for callers that budget a download themselves.
 *
 * @sample fi.aalto.cs.aplus.client.samples.readCourse
 * @sample fi.aalto.cs.aplus.client.samples.readRoster
 * @property httpClient the underlying Ktor client.
 * @property baseUrl the API root, ending in `/api/v2` without a trailing slash.
 * @param linkResolver rewrites every absolute URL the client is about to request, whether passed
 *   in, a redirect location or a `next` link, before the same-origin check. A deployment that
 *   serves A+ under a second origin can map it to [AplusClient.baseUrl]'s origin here. It cannot widen the check.
 */
public class AplusClient(
    public val httpClient: HttpClient,
    baseUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val ownsHttpClient: Boolean = false,
    public val limits: AplusLimits = AplusLimits(),
    private val linkResolver: (String) -> String = { it },
) : AutoCloseable {

    public val baseUrl: String = normalizeBaseUrl(baseUrl)

    private val base: Url = Url(this.baseUrl)

    /**
     * An immutable authentication view sharing this client's transport and limits.
     * Concurrent views never change each other's credentials. Null or blank means anonymous.
     * Closing a view leaves the transport open. Its owning client must outlive all views.
     * @sample fi.aalto.cs.aplus.client.samples.authenticationViews
     */
    public fun withToken(token: String?): AplusClient =
        AplusClient(httpClient, baseUrl, { token }, ownsHttpClient = false, limits, linkResolver)

    /** An immutable budget view sharing this client's transport and authentication provider. */
    public fun withLimits(limits: AplusLimits): AplusClient =
        AplusClient(httpClient, baseUrl, tokenProvider, ownsHttpClient = false, limits, linkResolver)

    /**
     * Reads a JSON resource within this API root, retaining the body size for caller-owned budgets.
     * Relative URLs resolve against the API root. Redirects are not followed.
     */
    public suspend fun <T> readJson(
        url: String,
        deserializer: DeserializationStrategy<T>,
        maxBytes: Int = limits.maxResponseBytes,
    ): AplusResponse<T> {
        requireByteLimit(maxBytes)
        return readGet(url, maxRedirects = 0, apiPathOnly = true) { decodeJson(it, deserializer, maxBytes) }
    }

    /**
     * Downloads a file from the configured A+ origin into a bounded byte array.
     * URLs may be outside the API path, but every redirect must keep the configured origin.
     * Redirects are opt-in and GET-only. The token is captured once, including redirects.
     * Public SDK/module downloads from other origins must use a separate unauthenticated client.
     */
    public suspend fun readBytes(
        url: String,
        maxBytes: Int = limits.maxResponseBytes,
        maxRedirects: Int = 0,
    ): ByteArray {
        requireByteLimit(maxBytes)
        return readGet(url, maxRedirects, apiPathOnly = false) { response ->
            if (!response.status.isSuccess()) throw failure(response)
            if (response.headers[HttpHeaders.ContentEncoding] == null && (response.contentLength() ?: 0) > maxBytes) {
                response.bodyAsChannel().cancel()
                throw AplusException.LimitExceeded(
                    response.status,
                    response.call.request.url.toString(),
                    "A+ file exceeds $maxBytes bytes"
                )
            }
            response.readBoundedBytes(maxBytes)
        }
    }

    /** Reads a bounded text resource on the configured origin, retaining its final URL, with [readBytes]' redirects. */
    public suspend fun readText(
        url: String,
        maxBytes: Int = limits.maxResponseBytes,
        maxRedirects: Int = 0,
    ): AplusResponse<String> {
        requireByteLimit(maxBytes)
        return readGet(url, maxRedirects, apiPathOnly = false) { response ->
            val body = ensureSuccess(response, maxBytes)
            AplusResponse(body.text, response.call.request.url.toString(), body.bytes)
        }
    }

    /** `GET /submissions/{submissionId}/files/{fileId}`, an attachment. Stock A+ answers the bytes directly. */
    public suspend fun submissionFile(
        submissionId: Long,
        fileId: Long,
        maxBytes: Int = limits.maxResponseBytes,
    ): ByteArray {
        require(submissionId > 0 && fileId > 0) { "Submission and file ids must be positive" }
        val file = AplusRoutes.Submission.File(AplusRoutes.Submission(submissionId), fileId)
        return readBytes(baseUrl + AplusRoutes.path(file), maxBytes)
    }

    private sealed interface ReadResult<out T> {
        data class Value<T>(val value: T) : ReadResult<T>
        data class Redirect(val url: String) : ReadResult<Nothing>
    }

    private suspend fun <T> readGet(
        url: String,
        maxRedirects: Int,
        apiPathOnly: Boolean,
        handle: suspend (HttpResponse) -> T,
    ): T {
        require(maxRedirects >= 0) { "Redirect limit must not be negative" }
        var current = requireSameOrigin(url, "$baseUrl/", base, apiPathOnly, linkResolver)
        val token = operationToken()
        var redirects = 0
        while (true) {
            val result = execute(HttpMethod.Get, current, token) { response ->
                val location = response.headers[HttpHeaders.Location]
                if (response.status.value in REDIRECT_STATUSES && location != null && redirects < maxRedirects) {
                    ReadResult.Redirect(requireSameOrigin(location, current, base, apiPathOnly, linkResolver))
                } else ReadResult.Value(handle(response))
            }
            when (result) {
                is ReadResult.Value -> return result.value
                is ReadResult.Redirect -> {
                    current = result.url; redirects++
                }
            }
        }
    }

    /** `GET /users/me`, the profile of the token owner. */
    public suspend fun me(): UserDto =
        getJson(AplusRoutes.path(AplusRoutes.User(AplusRoutes.ME)), UserDto.serializer())

    /** `GET /users/{userId}`. Another user's profile answers `404` to a student token. */
    public suspend fun user(userId: Long): UserDto =
        getJson(AplusRoutes.path(AplusRoutes.User(userId.toString())), UserDto.serializer())

    /** `GET /courses/{courseId}`, the course instance metadata. */
    public suspend fun course(courseId: Long): CourseDto =
        getJson(AplusRoutes.path(AplusRoutes.Course(courseId)), CourseDto.serializer())

    /**
     * `GET /users?search=...`, users whose email address is exactly [email].
     *
     * A+ matches the whole address with `search_fields = ['=user__email']`, so a match returns at
     * most one row. Without a query A+ answers an empty page, to prevent directory enumeration.
     *
     * Reading another user's row requires a teacher or admin token. Insufficient permission
     * returns an empty page.
     */
    public suspend fun userSearch(email: String, maxResults: Int = limits.maxResults): List<UserBriefDto> =
        listing(AplusRoutes.path(AplusRoutes.Users()), UserBriefDto.serializer(), maxResults) {
            append("search", email)
        }

    /**
     * `GET /courses/{courseId}/students`, enrolled students with their tags. Needs a staff token.
     *
     * @param field with [values], keeps the students whose [field] equals one of [values]. A+
     *   joins them with commas, so a value must not contain one.
     * @param search matched against first and last names, student ids and email addresses.
     */
    public suspend fun students(
        courseId: Long,
        field: StudentField? = null,
        values: Collection<String> = emptyList(),
        search: String? = null,
        maxResults: Int = limits.maxResults,
    ): List<StudentDto> {
        require((field == null) == values.isEmpty()) { "A student field needs values, and values need a field" }
        require(values.none { ',' in it }) { "Student filter values must not contain commas" }
        val path = AplusRoutes.path(AplusRoutes.Course.Students(AplusRoutes.Course(courseId)))
        return listing(path, StudentDto.serializer(), maxResults) {
            field?.let { append("field", it.parameter) }
            if (values.isNotEmpty()) append("values", values.joinToString(","))
            search?.let { append("search", it) }
        }
    }

    /**
     * `GET /courses/{courseId}/usertags`, the student tags defined on the course. Needs a teacher token.
     *
     * The first two rows are always the tags A+ hardcodes, `user-internal` and `user-external`,
     * which carry no [UserTagDto.id].
     */
    public suspend fun usertags(courseId: Long, maxResults: Int = limits.maxResults): List<UserTagDto> =
        listing(
            AplusRoutes.path(AplusRoutes.Course.UserTags(AplusRoutes.Course(courseId))),
            UserTagDto.serializer(),
            maxResults,
        )

    /** `GET /courses/{courseId}/exercises`, every module of the course with its exercises. A+ sends one page. */
    public suspend fun exerciseModules(courseId: Long): List<ExerciseModuleDto> =
        listing(
            AplusRoutes.path(AplusRoutes.Course.Exercises(AplusRoutes.Course(courseId))),
            ExerciseModuleDto.serializer(),
        )

    /** `GET /exercises/{exerciseId}`, one exercise including its grader `form_spec`. */
    public suspend fun exercise(exerciseId: Long): ExerciseDto =
        getJson(AplusRoutes.path(AplusRoutes.Exercise(exerciseId)), ExerciseDto.serializer())

    /**
     * `GET /courses/{courseId}/points/{userId}`, the points tree.
     *
     * @param userId an A+ user id, or `me` for the token owner. Another student's tree answers
     *   `404` to a student token.
     */
    public suspend fun points(
        courseId: Long,
        userId: String = AplusRoutes.ME,
    ): PointsDto = getJson(
        AplusRoutes.path(AplusRoutes.Course.Points(AplusRoutes.Course(courseId), userId)),
        PointsDto.serializer(),
    )

    /** `GET /courses/{courseId}/news`. */
    public suspend fun news(courseId: Long): List<NewsItemDto> =
        listing(
            AplusRoutes.path(AplusRoutes.Course.News(AplusRoutes.Course(courseId))),
            NewsItemDto.serializer(),
        )

    /** `GET /courses/{courseId}/mygroups`, the submission groups of the token owner. */
    public suspend fun myGroups(courseId: Long): List<GroupDto> =
        listing(
            AplusRoutes.path(AplusRoutes.Course.MyGroups(AplusRoutes.Course(courseId))),
            GroupDto.serializer(),
        )

    /**
     * `GET /exercises/{exerciseId}/submissions[/{userId}]`, newest first.
     *
     * A+ fixes the order to `-id` and the page size to its `PAGE_SIZE` setting.
     *
     * @param userId an A+ user id or `me`, to list only that user's submissions.
     */
    public suspend fun exerciseSubmissions(
        exerciseId: Long,
        userId: String? = null,
        maxResults: Int = limits.maxResults,
    ): List<SubmissionBriefDto> =
        listing(exerciseSubmissionsPath(exerciseId, userId), SubmissionBriefDto.serializer(), maxResults)

    /**
     * The newest submissions from [exerciseSubmissions], up to A+'s `PAGE_SIZE`.
     *
     * Only this page must fit within [maxResults]. The listing may hold more submissions.
     */
    public suspend fun latestExerciseSubmissions(
        exerciseId: Long,
        userId: String? = null,
        maxResults: Int = limits.maxResults,
    ): List<SubmissionBriefDto> =
        listing(
            exerciseSubmissionsPath(exerciseId, userId),
            SubmissionBriefDto.serializer(),
            maxResults,
            firstPageOnly = true,
        )

    /**
     * Creates a graded submission with `POST /exercises/{id}/submissions` and a teacher token.
     * A+ answers `201 Created` with an empty body.
     *
     * This write is not retried by this library. Supplied transports must also disable retries.
     */
    public suspend fun createGradedSubmission(exerciseId: Long, grade: GradeSubmissionRequest) {
        postJson(
            exerciseSubmissionsPath(exerciseId, null),
            AplusJson.default.encodeToJsonElement(GradeSubmissionRequest.serializer(), grade),
        )
    }

    private fun exerciseSubmissionsPath(exerciseId: Long, userId: String?): String {
        val submissions = AplusRoutes.Exercise.Submissions(AplusRoutes.Exercise(exerciseId))
        return if (userId == null) AplusRoutes.path(submissions)
        else AplusRoutes.path(AplusRoutes.Exercise.Submissions.ByUser(submissions, userId))
    }

    /** `GET /submissions/{submissionId}`, one submission with feedback and files. */
    public suspend fun submission(submissionId: Long): SubmissionDto =
        getJson(AplusRoutes.path(AplusRoutes.Submission(submissionId)), SubmissionDto.serializer())

    /**
     * `GET /courses/{courseId}/submissiondata/{userId}`, the legacy tabular export.
     *
     * Uses PascalCase columns decoded by [AplusJson.verbatim]. Per-file columns are unmodeled.
     *
     * [userId] is an A+ user id, or `me` for the token owner.
     * See [exerciseSubmissionData] for a course-wide exercise export.
     *
     * @param best when `false`, lists all submissions. When `true`, lists the best per exercise.
     */
    public suspend fun submissionData(
        courseId: Long,
        userId: String = AplusRoutes.ME,
        best: Boolean = false,
    ): List<SubmissionDataRowDto> {
        val path = AplusRoutes.path(AplusRoutes.Course.SubmissionData(AplusRoutes.Course(courseId), userId))
        return getJson(path, ListSerializer(SubmissionDataRowDto.serializer()), AplusJson.verbatim) {
            parameter("best", if (best) "yes" else "no")
            parameter("format", "json")
        }
    }

    /**
     * `GET /courses/{courseId}/submissiondata?exercise_id={exerciseId}`, the export for
     * one exercise across all students on the course.
     *
     * Both export variants use `submissions_sheet`, which fills `UserID`, `StudentID`, and `Email`
     * per submitter. A group submission therefore produces one [SubmissionDataRowDto] per member.
     * Stock A+ supports this endpoint with a staff token.
     *
     * @param best when `false` every submission is listed. When `true`, only the best submission
     *   per exercise and submitter is listed.
     */
    public suspend fun exerciseSubmissionData(
        courseId: Long,
        exerciseId: Long,
        best: Boolean = false,
    ): List<SubmissionDataRowDto> {
        val path = AplusRoutes.path(AplusRoutes.Course.AllSubmissionData(AplusRoutes.Course(courseId)))
        return getJson(path, ListSerializer(SubmissionDataRowDto.serializer()), AplusJson.verbatim) {
            parameter("exercise_id", exerciseId)
            parameter("best", if (best) "yes" else "no")
            parameter("format", "json")
        }
    }

    /**
     * `POST /exercises/{exerciseId}/submissions/submit`, a multipart submission.
     *
     * The `__aplus__` field carries group and language. Each entry in [files] uses its form_spec
     * key as the part name. A+ answers `201 Created` with the new submission's `Location`, and
     * lists in [SubmitResult.errors] what went wrong while grading it. A refused submission
     * answers `400` with its reasons, raised as [AplusException].
     *
     * [SubmissionFile.fileName] can come from form_i18n. Quoting escapes quotes, backslashes,
     * tabs, CR, and LF in Content-Disposition to preserve the filename parameter.
     *
     * @param groupId the submission group, or [SUBMIT_ALONE_GROUP_ID] to submit alone. A positive
     *   id outside the token owner's groups returns `400` with EXERCISE_ERROR_NO_GROUP_WITH_ID.
     * @throws IllegalArgumentException for filename control characters below space except tab,
     *   CR, and LF. Ktor rejects these header values as caller errors outside [AplusException].
     */
    public suspend fun submit(
        exerciseId: Long,
        files: List<SubmissionFile>,
        groupId: Long = SUBMIT_ALONE_GROUP_ID,
        language: String = LocalizedString.DEFAULT_LANGUAGE,
    ): SubmitResult {
        val path = AplusRoutes.path(AplusRoutes.Exercise.Submit(AplusRoutes.Exercise(exerciseId)))
        val parts = formData {
            append(APLUS_FORM_FIELD, aplusFormField(groupId, language))
            files.forEach { file ->
                append(
                    file.fieldName,
                    file.content,
                    Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=" + file.fileName.quote())
                    },
                )
            }
        }
        return execute(HttpMethod.Post, baseUrl + path, operationToken(), {
            setBody(MultiPartFormDataContent(parts))
        }) { response ->
            val body = ensureSuccess(response)
            val errors = if (body.text.isBlank()) null else decodeBody(response, body.text, SubmitErrors.serializer())
            SubmitResult(response.headers[HttpHeaders.Location], errors?.errors.orEmpty())
        }
    }

    /** Closes the underlying [httpClient], but only when this client created it. */
    override fun close() {
        if (ownsHttpClient) httpClient.close()
    }

    /**
     * [language] comes from course configuration and can contain quotes or backslashes.
     * JSON encoding escapes them before submission.
     */
    private fun aplusFormField(groupId: Long, language: String): String =
        buildJsonObject {
            put("group", groupId)
            put("lang", language)
        }.toString()

    private suspend fun postJson(path: String, body: JsonElement) {
        val bytes = body.toString().encodeToByteArray()
        // Ktor converts channel content to a one-shot OkHttp body, including for 503 follow-ups.
        val content = object : OutgoingContent.ReadChannelContent() {
            private val channel = ByteReadChannel(bytes)
            override val contentType: ContentType = ContentType.Application.Json
            override val contentLength: Long = bytes.size.toLong()
            override fun readFrom(): ByteReadChannel = channel
        }
        execute(HttpMethod.Post, baseUrl + path, operationToken(), { setBody(content) }) { response ->
            ensureSuccess(response)
        }
    }

    private suspend fun <T> getJson(
        path: String,
        deserializer: DeserializationStrategy<T>,
        json: Json = AplusJson.default,
        block: HttpRequestBuilder.() -> Unit = {},
    ): T = execute(HttpMethod.Get, baseUrl + path, operationToken(), block) {
        decodeJson(it, deserializer, json = json)
    }.value

    private suspend fun <T> listing(
        path: String,
        serializer: KSerializer<T>,
        maxResults: Int = limits.maxResults,
        firstPageOnly: Boolean = false,
        query: ParametersBuilder.() -> Unit = {},
    ): List<T> {
        val token = operationToken()
        val pages = Paginator(
            limits,
            maxResults,
            resolve = { link, current -> requireSameOrigin(link, current, base, apiPathOnly = true, linkResolver) },
            fetch = { url, maxBytes ->
                execute(HttpMethod.Get, url, token) { decodeJson(it, Page.serializer(serializer), maxBytes) }
            },
        )
        val url = URLBuilder(baseUrl + path).apply { parameters.query() }.buildString()
        return if (firstPageOnly) pages.firstPage(url) else pages.all(url)
    }

    private suspend fun <T> decodeJson(
        response: HttpResponse,
        deserializer: DeserializationStrategy<T>,
        maxBytes: Int = limits.maxResponseBytes,
        json: Json = AplusJson.default,
    ): AplusResponse<T> {
        val body = ensureSuccess(response, maxBytes)
        val value = decodeBody(response, body.text, deserializer, json)
        return AplusResponse(value, response.call.request.url.toString(), body.bytes)
    }

    private fun <T> decodeBody(
        response: HttpResponse,
        text: String,
        deserializer: DeserializationStrategy<T>,
        json: Json = AplusJson.default,
    ): T = try {
        json.decodeFromString(deserializer, text)
    } catch (malformed: SerializationException) {
        throw AplusException.Malformed(
            response.status,
            response.call.request.url.toString(),
            text.truncateUtf8(limits.maxErrorBytes),
            malformed,
        )
    }

    private fun requireByteLimit(maxBytes: Int) {
        require(maxBytes > 0) { "Response byte limit must be positive, was $maxBytes" }
    }

    private suspend fun operationToken(): String? = tokenProvider()?.takeIf { it.isNotBlank() }

    private suspend fun <T> execute(
        method: HttpMethod,
        url: String,
        token: String?,
        block: HttpRequestBuilder.() -> Unit = {},
        handle: suspend (HttpResponse) -> T,
    ): T = httpClient.prepareRequest(url) {
        this.method = method
        expectSuccess = false
        accept(ContentType.Application.Json)
        if (token != null) header(HttpHeaders.Authorization, "$TOKEN_SCHEME $token")
        block()
    }.execute(handle)

    private suspend fun ensureSuccess(response: HttpResponse, maxBytes: Int = limits.maxResponseBytes): ResponseBody {
        if (!response.status.isSuccess()) throw failure(response)
        return response.readBoundedBody(maxBytes)
    }

    private suspend fun failure(response: HttpResponse): AplusException {
        val body = response.readBoundedBody(limits.maxErrorBytes, truncate = true)
        return AplusException.of(response.status, response.call.request.url.toString(), body.text)
    }

    public companion object {
        internal const val API_PATH: String = "/api/v2"

        /**
         * The [submit] group id for submitting alone.
         *
         * `BaseExercise._check_submission_allowed` in `exercise/exercise_models.py` looks up a group
         * only when `gid > 0`. Zero, negative values, and unparseable strings therefore submit alone.
         * Use `0` to match the submit button in `course/templates/course/_group_select.html` and
         * the solo submission cache in `exercise/cache/points.py`. `GroupSelectForm` in
         * `course/forms.py` rejects negative ids because it tests `gid != 0`.
         *
         * Omitting the field reuses the student's last selected course group. [submit] always sends it.
         */
        public const val SUBMIT_ALONE_GROUP_ID: Long = 0

        /** The `Authorization` scheme A+ expects. */
        public const val TOKEN_SCHEME: String = "Token"

        /** The multipart field A+ reads the submission group and language out of. */
        internal const val APLUS_FORM_FIELD: String = "__aplus__"

        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)

        /**
         * Builds and owns a configured OkHttp client. [close] shuts down its engine.
         *
         * [configure] receives the OkHttp engine configuration.
         *
         * ```kotlin
         * AplusClient.create(baseUrl, tokenProvider) {
         *     engine { config { sslSocketFactory(factory, trustManager) } }
         * }
         * ```
         *
         * For other engines, pass a configured [HttpClient] to the primary constructor.
         *
         * @param baseUrl the A+ root `https://plus.cs.aalto.fi` or API root
         *   `https://plus.cs.aalto.fi/api/v2`.
         * @param tokenProvider called once per operation, including all its pages, for lazy token loading.
         *   Returning `null` sends an unauthenticated request.
         * @param limits response byte, page, and result budgets.
         * @param configure additional configuration applied after the A+ defaults.
         */
        @JvmStatic
        @JvmOverloads
        public fun create(
            baseUrl: String,
            tokenProvider: suspend () -> String?,
            limits: AplusLimits = AplusLimits(),
            configure: HttpClientConfig<OkHttpConfig>.() -> Unit = {},
        ): AplusClient {
            val apiRoot = normalizeBaseUrl(baseUrl)
            return AplusClient(
                httpClient = HttpClient(OkHttp) {
                    aplusDefaults()
                    engine { config { retryOnConnectionFailure(false) } }
                    configure()
                },
                baseUrl = apiRoot,
                tokenProvider = tokenProvider,
                ownsHttpClient = true,
                limits = limits,
            )
        }

        /**
         * Normalizes [baseUrl] to `scheme://host[:port][/prefix]/api/v2`, without a trailing
         * slash.
         *
         * @throws IllegalArgumentException when [baseUrl] is not an HTTP(S) origin with an optional
         *   path prefix, or carries credentials, a query, a fragment, dot segments, or encoded characters.
         */
        public fun normalizeBaseUrl(baseUrl: String): String = normalizeAplusBaseUrl(baseUrl)
    }
}

/**
 * Configures a Ktor client for [AplusClient], which follows redirects itself and only within
 * the configured origin.
 */
public fun HttpClientConfig<*>.aplusDefaults() {
    expectSuccess = false
    followRedirects = false
}
