package fi.aalto.cs.aplus.client

import io.ktor.resources.*
import io.ktor.resources.serialization.*

/**
 * A+ `api/v2` routes as Ktor [Resource] types.
 *
 * Paths are relative to the API root `https://host/api/v2`. [AplusClient] prepends the base
 * URL. Optional query parameters are added per request so absent values stay out of the URL.
 */
internal object AplusRoutes {

    /** The user id A+ resolves to the token owner. */
    const val ME: String = "me"

    /** `/users/{userId}`, where [ME] is a valid id for the authenticated user. */
    @Resource("/users/{userId}")
    class User(val userId: String)

    /**
     * `/users`, the user directory.
     *
     * A+ only lists users that match a `search` query, and answers an empty page without one,
     * so this route is only useful with the parameter [AplusClient.userSearch] sends.
     */
    @Resource("/users")
    class Users

    @Resource("/courses/{courseId}")
    class Course(val courseId: Long) {

        @Resource("exercises")
        class Exercises(val parent: Course)

        @Resource("points/{userId}")
        class Points(val parent: Course, val userId: String)

        @Resource("news")
        class News(val parent: Course)

        @Resource("mygroups")
        class MyGroups(val parent: Course)

        @Resource("students")
        class Students(val parent: Course)

        @Resource("usertags")
        class UserTags(val parent: Course)

        @Resource("submissiondata/{userId}")
        class SubmissionData(val parent: Course, val userId: String)

        /**
         * `/courses/{courseId}/submissiondata`, the export across all students.
         *
         * Use the `exercise_id` query parameter to limit the export to one exercise.
         */
        @Resource("submissiondata")
        class AllSubmissionData(val parent: Course)
    }

    @Resource("/exercises/{exerciseId}")
    class Exercise(val exerciseId: Long) {

        @Resource("submissions")
        class Submissions(val parent: Exercise) {
            /** [ME] names the token owner. */
            @Resource("{userId}")
            class ByUser(val parent: Submissions, val userId: String)
        }

        @Resource("submissions/submit")
        class Submit(val parent: Exercise)
    }

    @Resource("/submissions/{submissionId}")
    class Submission(val submissionId: Long) {
        @Resource("files/{fileId}")
        class File(val parent: Submission, val fileId: Long)
    }

    /** The [ResourcesFormat] used to render routes. It has no custom serializers. */
    val format: ResourcesFormat = ResourcesFormat()

    /**
     * Renders [resource] to a path relative to the API root, e.g. `/courses/3/points/me`.
     */
    inline fun <reified T : Any> path(resource: T): String = href(format, resource)

    /**
     * Extracts the trailing numeric id from an A+ resource URL.
     *
     * Used by [fi.aalto.cs.aplus.client.dto.PointsExerciseDto.resolvedBestSubmissionId] and
     * [fi.aalto.cs.aplus.client.dto.GroupMemberDto.userIdOrNull] to resolve hyperlinks without
     * fetching the resource.
     *
     * Returns `null` for a `null` URL or a nonnumeric final segment, such as
     * `.../exercises/5/submissions`. Query strings and fragments are removed before parsing,
     * so `.../submissions/42/?next=/api/v2/submissions/99` resolves to `42`.
     * Trailing slashes are accepted because A+ routes use `trailing_slash = '/?'` in `api/urls_v2.py`.
     *
     * The URL is otherwise unvalidated. Callers needing an id from a particular server must
     * compare the URL with their base URL first.
     */
    fun idFromApiUrl(url: String?): Long? = url
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?.trimEnd('/')
        ?.substringAfterLast('/')
        ?.toLongOrNull()
}
