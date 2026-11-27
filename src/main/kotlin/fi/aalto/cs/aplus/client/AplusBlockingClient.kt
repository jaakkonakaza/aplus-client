package fi.aalto.cs.aplus.client

import fi.aalto.cs.aplus.client.dto.*
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.DeserializationStrategy
import kotlin.time.Duration.Companion.milliseconds

/**
 * Synchronous access to [AplusClient] for Scala and Java callers.
 *
 * Each operation blocks its calling thread and applies [timeoutMillis] to the entire operation,
 * including pagination. An interrupted thread cancels the operation and receives [InterruptedException].
 * API failures propagate as [AplusException]. Coroutine callers should use [AplusClient].
 *
 * Closing this adapter calls [AplusClient.close] on the wrapped client and follows its ownership rules.
 * Concurrent calls use separate coroutine scopes and share the wrapped client's transport.
 *
 * @param client the client whose authentication, transport and limits each operation uses.
 * @param timeoutMillis positive operation timeout in milliseconds. Expiry raises
 *   [kotlinx.coroutines.TimeoutCancellationException].
 */
public class AplusBlockingClient @JvmOverloads constructor(
    private val client: AplusClient,
    public val timeoutMillis: Long = 120_000,
) : AutoCloseable {
    init {
        require(timeoutMillis > 0) { "Operation timeout must be positive" }
    }

    public val baseUrl: String get() = client.baseUrl
    public val limits: AplusLimits get() = client.limits

    /** Authentication views share the transport. Closing a view leaves the owning client open. */
    public fun withToken(token: String?): AplusBlockingClient =
        AplusBlockingClient(client.withToken(token), timeoutMillis)

    /** Budget views share the transport and authentication provider. */
    public fun withLimits(limits: AplusLimits): AplusBlockingClient =
        AplusBlockingClient(client.withLimits(limits), timeoutMillis)

    private fun <T> call(operation: suspend () -> T): T =
        runBlocking { withTimeout(timeoutMillis.milliseconds) { operation() } }

    /** Blocking variant of [AplusClient.readJson]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun <T> readJson(
        url: String,
        deserializer: DeserializationStrategy<T>,
        maxBytes: Int = limits.maxResponseBytes,
    ): AplusResponse<T> =
        call { client.readJson(url, deserializer, maxBytes) }

    /** Blocking variant of [AplusClient.readBytes]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun readBytes(
        url: String,
        maxBytes: Int = limits.maxResponseBytes,
        maxRedirects: Int = 0,
    ): ByteArray =
        call { client.readBytes(url, maxBytes, maxRedirects) }

    /** Blocking variant of [AplusClient.readText]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun readText(
        url: String,
        maxBytes: Int = limits.maxResponseBytes,
        maxRedirects: Int = 0,
    ): AplusResponse<String> =
        call { client.readText(url, maxBytes, maxRedirects) }

    /** Blocking variant of [AplusClient.submissionFile]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun submissionFile(
        submissionId: Long,
        fileId: Long,
        maxBytes: Int = limits.maxResponseBytes,
    ): ByteArray =
        call { client.submissionFile(submissionId, fileId, maxBytes) }

    /** Blocking variant of [AplusClient.me]. */
    @Throws(InterruptedException::class)
    public fun me(): UserDto =
        call { client.me() }

    /** Blocking variant of [AplusClient.user]. */
    @Throws(InterruptedException::class)
    public fun user(userId: Long): UserDto =
        call { client.user(userId) }

    /** Blocking variant of [AplusClient.course]. */
    @Throws(InterruptedException::class)
    public fun course(courseId: Long): CourseDto =
        call { client.course(courseId) }

    /** Blocking variant of [AplusClient.userSearch]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun userSearch(email: String, maxResults: Int = limits.maxResults): List<UserBriefDto> =
        call { client.userSearch(email, maxResults) }

    /** Blocking variant of [AplusClient.students]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun students(
        courseId: Long,
        field: StudentField? = null,
        values: Collection<String> = emptyList(),
        search: String? = null,
        maxResults: Int = limits.maxResults,
    ): List<StudentDto> =
        call { client.students(courseId, field, values, search, maxResults) }

    /** Blocking variant of [AplusClient.usertags]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun usertags(courseId: Long, maxResults: Int = limits.maxResults): List<UserTagDto> =
        call { client.usertags(courseId, maxResults) }

    /** Blocking variant of [AplusClient.exerciseModules]. */
    @Throws(InterruptedException::class)
    public fun exerciseModules(courseId: Long): List<ExerciseModuleDto> =
        call { client.exerciseModules(courseId) }

    /** Blocking variant of [AplusClient.exercise]. */
    @Throws(InterruptedException::class)
    public fun exercise(exerciseId: Long): ExerciseDto =
        call { client.exercise(exerciseId) }

    /** Blocking variant of [AplusClient.points]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun points(
        courseId: Long,
        userId: String = AplusRoutes.ME,
    ): PointsDto =
        call { client.points(courseId, userId) }

    /** Blocking variant of [AplusClient.news]. */
    @Throws(InterruptedException::class)
    public fun news(courseId: Long): List<NewsItemDto> =
        call { client.news(courseId) }

    /** Blocking variant of [AplusClient.myGroups]. */
    @Throws(InterruptedException::class)
    public fun myGroups(courseId: Long): List<GroupDto> =
        call { client.myGroups(courseId) }

    /** Blocking variant of [AplusClient.exerciseSubmissions]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun exerciseSubmissions(
        exerciseId: Long,
        userId: String? = null,
        maxResults: Int = limits.maxResults,
    ): List<SubmissionBriefDto> =
        call { client.exerciseSubmissions(exerciseId, userId, maxResults) }

    /** Blocking variant of [AplusClient.latestExerciseSubmissions]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun latestExerciseSubmissions(
        exerciseId: Long,
        userId: String? = null,
        maxResults: Int = limits.maxResults,
    ): List<SubmissionBriefDto> =
        call { client.latestExerciseSubmissions(exerciseId, userId, maxResults) }

    /** Blocking variant of [AplusClient.createGradedSubmission]. */
    @Throws(InterruptedException::class)
    public fun createGradedSubmission(exerciseId: Long, grade: GradeSubmissionRequest): Unit =
        call { client.createGradedSubmission(exerciseId, grade) }

    /** Blocking variant of [AplusClient.submission]. */
    @Throws(InterruptedException::class)
    public fun submission(submissionId: Long): SubmissionDto =
        call { client.submission(submissionId) }

    /** Blocking variant of [AplusClient.submissionData]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun submissionData(
        courseId: Long,
        userId: String = AplusRoutes.ME,
        best: Boolean = false,
    ): List<SubmissionDataRowDto> =
        call { client.submissionData(courseId, userId, best) }

    /** Blocking variant of [AplusClient.exerciseSubmissionData]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun exerciseSubmissionData(
        courseId: Long,
        exerciseId: Long,
        best: Boolean = false,
    ): List<SubmissionDataRowDto> =
        call { client.exerciseSubmissionData(courseId, exerciseId, best) }

    /** Blocking variant of [AplusClient.submit]. */
    @JvmOverloads
    @Throws(InterruptedException::class)
    public fun submit(
        exerciseId: Long,
        files: List<SubmissionFile>,
        groupId: Long = AplusClient.SUBMIT_ALONE_GROUP_ID,
        language: String = LocalizedString.DEFAULT_LANGUAGE,
    ): SubmitResult =
        call { client.submit(exerciseId, files, groupId, language) }

    override fun close() {
        client.close()
    }

    public companion object {
        /**
         * Creates an owning client with the bundled OkHttp transport.
         *
         * [tokenProvider] is consulted once per operation. Null or blank tokens send anonymous requests.
         * [timeoutMillis] also sets the transport's socket read timeout.
         * For a custom transport, wrap an [AplusClient] with the constructor.
         */
        @JvmStatic
        @JvmOverloads
        public fun create(
            baseUrl: String,
            tokenProvider: Supplier<String?>,
            limits: AplusLimits = AplusLimits(),
            timeoutMillis: Long = 120_000,
        ): AplusBlockingClient {
            require(timeoutMillis > 0) { "Operation timeout must be positive" }
            return AplusBlockingClient(
                AplusClient.create(baseUrl, { tokenProvider.get() }, limits) {
                    engine { config { readTimeout(timeoutMillis, TimeUnit.MILLISECONDS) } }
                },
                timeoutMillis,
            )
        }
    }
}
