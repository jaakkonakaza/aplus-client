package fi.aalto.cs.aplus.client

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DRF omits fields built in to_representation. FixtureDecodingTest covers those responses.
 * [OpenApiFieldConformanceTest] checks endpoints with typed schemas.
 */
class OpenApiConformanceTest {

    /** [specPath] includes the API root. [libraryPath] is relative to it. */
    private data class Endpoint(
        val specPath: String,
        val method: String,
        val libraryPath: String,
        val queryParams: Set<String> = emptySet(),
    ) {
        override fun toString(): String = "${method.uppercase()} $specPath"
    }

    private val endpoints: List<Endpoint> = listOf(
        Endpoint(
            "/api/v2/submissions/{submission_id}/files/{submittedfile_id}/", "get",
            AplusRoutes.path(AplusRoutes.Submission.File(AplusRoutes.Submission(42), 7))
        ),
        Endpoint(
            "/api/v2/exercises/{exercise_id}/submissions/", "get",
            AplusRoutes.path(AplusRoutes.Exercise.Submissions(AplusRoutes.Exercise(24)))
        ),
        Endpoint(
            "/api/v2/exercises/{exercise_id}/submissions/", "post",
            AplusRoutes.path(AplusRoutes.Exercise.Submissions(AplusRoutes.Exercise(24)))
        ),
        Endpoint(
            "/api/v2/exercises/{exercise_id}/submissions/{user_id}/", "get",
            AplusRoutes.path(
                AplusRoutes.Exercise.Submissions.ByUser(
                    AplusRoutes.Exercise.Submissions(
                        AplusRoutes.Exercise(
                            24
                        )
                    ), "me"
                )
            ),
            setOf("page")
        ),
        Endpoint(
            specPath = "/api/v2/users/{user_id}/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.User(AplusRoutes.ME)),
        ),
        Endpoint(
            specPath = "/api/v2/users/{user_id}/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.User("517")),
        ),
        Endpoint(
            specPath = "/api/v2/users/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.Users()),
            queryParams = setOf("search"),
        ),
        Endpoint(
            specPath = "/api/v2/courses/{course_id}/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.Course(COURSE_ID)),
        ),
        Endpoint(
            specPath = "/api/v2/courses/{course_id}/students/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.Course.Students(AplusRoutes.Course(COURSE_ID))),
            queryParams = setOf("field", "values", "search"),
        ),
        Endpoint(
            specPath = "/api/v2/courses/{course_id}/usertags/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.Course.UserTags(AplusRoutes.Course(COURSE_ID))),
        ),
        Endpoint(
            specPath = "/api/v2/courses/{course_id}/exercises/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.Course.Exercises(AplusRoutes.Course(COURSE_ID))),
        ),
        Endpoint(
            specPath = "/api/v2/courses/{course_id}/points/{user_id}/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.Course.Points(AplusRoutes.Course(COURSE_ID), AplusRoutes.ME)),
        ),
        Endpoint(
            specPath = "/api/v2/courses/{course_id}/news/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.Course.News(AplusRoutes.Course(COURSE_ID))),
        ),
        Endpoint(
            specPath = "/api/v2/courses/{course_id}/mygroups/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.Course.MyGroups(AplusRoutes.Course(COURSE_ID))),
        ),
        Endpoint(
            specPath = "/api/v2/courses/{course_id}/submissiondata/{user_id}/",
            method = "get",
            libraryPath = AplusRoutes.path(
                AplusRoutes.Course.SubmissionData(AplusRoutes.Course(COURSE_ID), AplusRoutes.ME),
            ),
            queryParams = setOf("best", "format"),
        ),
        Endpoint(
            specPath = "/api/v2/courses/{course_id}/submissiondata/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.Course.AllSubmissionData(AplusRoutes.Course(COURSE_ID))),
            queryParams = setOf("exercise_id", "best", "format"),
        ),
        Endpoint(
            specPath = "/api/v2/exercises/{exercise_id}/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.Exercise(EXERCISE_ID)),
        ),
        Endpoint(
            specPath = "/api/v2/exercises/{exercise_id}/submissions/submit/",
            method = "post",
            libraryPath = AplusRoutes.path(AplusRoutes.Exercise.Submit(AplusRoutes.Exercise(EXERCISE_ID))),
        ),
        Endpoint(
            specPath = "/api/v2/submissions/{submission_id}/",
            method = "get",
            libraryPath = AplusRoutes.path(AplusRoutes.Submission(SUBMISSION_ID)),
        ),
    )

    @Test
    fun `every route the library calls is described by the A+ spec`() {
        endpoints.forEach { endpoint ->
            val pathItem = specPaths[endpoint.specPath]
            assertTrue(pathItem != null, "The spec has no path ${endpoint.specPath}")
            assertTrue(
                endpoint.method in pathItem.keys,
                "${endpoint.specPath} does not accept ${endpoint.method.uppercase()}, only ${pathItem.keys}",
            )
        }
    }

    @Test
    fun `the paths the library renders are the paths the spec describes`() {
        endpoints.forEach { endpoint ->
            assertEquals(
                normalize(endpoint.specPath),
                normalize(AplusClient.API_PATH + endpoint.libraryPath),
                "${endpoint.libraryPath} is not the same route as ${endpoint.specPath}",
            )
        }
    }

    @Test
    fun `route rendering produces the paths the client requests`() {
        assertEquals("/users/me", AplusRoutes.path(AplusRoutes.User(AplusRoutes.ME)))
        assertEquals("/courses/3", AplusRoutes.path(AplusRoutes.Course(COURSE_ID)))
        assertEquals(
            "/courses/3/points/me",
            AplusRoutes.path(AplusRoutes.Course.Points(AplusRoutes.Course(COURSE_ID), AplusRoutes.ME)),
        )
        assertEquals(
            "/exercises/24/submissions/submit",
            AplusRoutes.path(AplusRoutes.Exercise.Submit(AplusRoutes.Exercise(EXERCISE_ID))),
        )
        assertEquals("/users", AplusRoutes.path(AplusRoutes.Users()))
        assertEquals(
            "/courses/3/students",
            AplusRoutes.path(AplusRoutes.Course.Students(AplusRoutes.Course(COURSE_ID)))
        )
        assertEquals(
            "/courses/3/usertags",
            AplusRoutes.path(AplusRoutes.Course.UserTags(AplusRoutes.Course(COURSE_ID)))
        )

        assertEquals(
            "/courses/3/submissiondata",
            AplusRoutes.path(AplusRoutes.Course.AllSubmissionData(AplusRoutes.Course(COURSE_ID))),
        )
    }

    @Test
    fun `every query parameter the library sends is declared by the spec`() {
        endpoints.forEach { endpoint ->
            val declared = queryParameters(endpoint.specPath, endpoint.method).keys
            // DRF's schema generator misses this retrieve action's delegation to list().
            val implicitPage = if (endpoint.method == "get" &&
                endpoint.specPath == "/api/v2/exercises/{exercise_id}/submissions/{user_id}/"
            ) setOf("page") else emptySet()
            val undeclared = endpoint.queryParams.filterTo(mutableSetOf()) {
                it !in declared && it !in UNDECLARED_QUERY_PARAMS && it !in implicitPage
            }
            assertTrue(
                undeclared.isEmpty(),
                "$endpoint accepts $declared, but the library also sends $undeclared",
            )
        }
    }

    @Test
    fun `every query parameter the spec requires is always sent`() {
        endpoints.forEach { endpoint ->
            val required = queryParameters(endpoint.specPath, endpoint.method)
                .filterValues { it["required"]?.jsonPrimitive?.content == "true" }
                .keys
            assertTrue(
                required.all { it in endpoint.queryParams },
                "$endpoint requires $required, but the library only sends ${endpoint.queryParams}",
            )
        }
    }

    @Test
    fun `the endpoints without a typed response schema are still generic`() {
        FIXTURE_LEVEL_ENDPOINTS.forEach { (path, method) ->
            assertFalse(
                isTyped(responseSchema(path, method)),
                "$method $path now has a typed response schema. Promote it from FixtureDecodingTest " +
                        "into OpenApiFieldConformanceTest and drop it from this list.",
            )
        }

        // DRF describes a pagination envelope here, but A+ returns a bare PascalCase row array.
        // Only a typed row schema would let SubmissionDataRowDto move to field comparisons.

        val rows = checkNotNull(responseSchema(COURSE_SUBMISSIONDATA, "get"))
            .getValue("properties").jsonObject
            .getValue("results").jsonObject
            .getValue("items").jsonObject

        assertTrue(
            rows.isEmpty(),
            "$COURSE_SUBMISSIONDATA now types its rows. Promote SubmissionDataRowDto from " +
                    "FixtureDecodingTest into OpenApiFieldConformanceTest and drop this test.",
        )

        // DRF uses a component $ref for typed responses such as /users/{user_id}.
        // The schema check must resolve it to detect the fields.

        val typed = checkNotNull(responseSchema("/api/v2/users/{user_id}/", "get"))

        assertTrue(isTyped(typed), $$"a $ref response was not recognised as typed")
    }

    private fun isTyped(schema: JsonObject?): Boolean =
        schema != null && "properties" in resolveSchemaRef(schema)

    private companion object {
        /**
         * Query parameters omitted from the schema.
         *
         * The submissiondata operation docstrings document best and exercise_id.
         * Format is DRF's global renderer selector, which its schema generator omits. The captured
         * description predates the schema fields of the students' FieldValuesFilter, `field` and `values`.
         */
        val UNDECLARED_QUERY_PARAMS = setOf("best", "exercise_id", "format", "field", "values")

        /**
         * Operations with generic response schemas, covered by FixtureDecodingTest.
         *
         * The course-wide submissiondata export has a pagination envelope with generic row items.
         * The schema-refresh check inspects those items separately.
         */
        val FIXTURE_LEVEL_ENDPOINTS = listOf(
            "/api/v2/courses/{course_id}/exercises/" to "get",
            "/api/v2/courses/{course_id}/points/{user_id}/" to "get",
            "/api/v2/courses/{course_id}/submissiondata/{user_id}/" to "get",
        )

        const val COURSE_SUBMISSIONDATA = "/api/v2/courses/{course_id}/submissiondata/"

        const val EXERCISE_ID = 24L
        const val SUBMISSION_ID = 21126L
    }

    private fun normalize(path: String): String {
        val segments = path.trim('/').split('/').map { segment ->
            when {
                segment.startsWith("{") && segment.endsWith("}") -> "{}"
                segment.toLongOrNull() != null -> "{}"
                segment == AplusRoutes.ME -> "{}"
                else -> segment
            }
        }
        return "/" + segments.joinToString("/") + "/"
    }
}
