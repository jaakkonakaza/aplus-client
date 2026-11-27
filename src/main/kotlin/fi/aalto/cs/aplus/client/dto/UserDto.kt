package fi.aalto.cs.aplus.client.dto

import kotlinx.serialization.Serializable

/**
 * `GET /users/me` and `GET /users/{id}`.
 *
 * @property enrolledCourses courses the user is a student on.
 * @property staffCourses courses the user has any staff role on.
 */
@Serializable
public data class UserDto(
    val id: Long,
    val url: String? = null,
    val username: String,
    val studentId: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val isExternal: Boolean,
    val enrolledCourses: List<CourseBriefDto> = emptyList(),
    val staffCourses: List<CourseBriefDto> = emptyList(),
    val teacherCourses: List<CourseBriefDto> = emptyList(),
    val assistantCourses: List<CourseBriefDto> = emptyList(),
) {
    /** [fullName] when A+ knows one, otherwise the [username]. */
    public val displayName: String get() = fullName?.takeIf { it.isNotBlank() } ?: username
}

/**
 * One enrolled student from `GET /courses/{id}/students`.
 *
 * The endpoint is available on stock A+ and requires a staff token. Students can read
 * their own row through `/students/me`. Other students' rows answer `404`.
 *
 * @property id the A+ user id from UserProfile.user_id. Required for identifying rows.
 * @property email can be empty for configured or cloned accounts. Shibboleth can synthesize
 *   `{id}@localhost`, which cannot receive mail. Use [id] to identify students.
 *   See A+'s `course/api/full_serializers.py` and `shibboleth_login/auth_backend.py`.
 * @property tagSlugs tag slugs for this course, including user-internal and user-external.
 *   StudentBriefSerializer.get_tag_slugs reads CachedStudent, which defaults to `[]` in
 *   `course/cache/students.py`. UserBriefDto omits tags. PointsDto.tags carries full tag objects.
 * @property summaryHtml the absolute URL of this student's staff results page.
 * @property points the URL fetched by [fi.aalto.cs.aplus.client.AplusClient.points] for [id].
 * @property data the URL of the student's submission export.
 */
@Serializable
public data class StudentDto(
    val id: Long,
    val url: String? = null,
    val username: String,
    val studentId: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val isExternal: Boolean,
    val tagSlugs: List<String> = emptyList(),
    val summaryHtml: String? = null,
    val points: String? = null,
    val data: String? = null,
)

/** A [fi.aalto.cs.aplus.client.AplusClient.students] filter field. Unknown fields answer `404`. */
public enum class StudentField(internal val parameter: String) {
    ID("id"),
    STUDENT_ID("student_id"),
    EMAIL("email"),
}
