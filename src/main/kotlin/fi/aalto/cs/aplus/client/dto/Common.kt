package fi.aalto.cs.aplus.client.dto

import kotlinx.serialization.Serializable

/**
 * The short course representation A+ embeds inside other resources.
 */
@Serializable
public data class CourseBriefDto(
    val id: Long,
    val url: String? = null,
    val htmlUrl: String? = null,
    val code: String? = null,
    val name: String? = null,
    val instanceName: String? = null,
)

/**
 * The short user representation A+ embeds in submissions and groups, and the row type of
 * `GET /users?search=...`.
 */
@Serializable
public data class UserBriefDto(
    val id: Long,
    val url: String? = null,
    val username: String,
    val studentId: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val isExternal: Boolean,
)

/**
 * A course user tag, also embedded in the points tree.
 *
 * CourseUsertagSerializer sends all properties below. The points tree embeds only [id],
 * [url], [slug], and [name] through UserToTagSerializer in `exercise/api/custom_serializers.py`.
 * Defaults allow both stock A+ representations to decode.
 *
 * @property id `null` for unsaved HardcodedUserTag instances `user-internal` and `user-external`.
 *   Teacher-created tags have an id. [slug] identifies both kinds.
 * @property description an empty string when unset. The server column is `blank=True, null=False`.
 * @property visibleToStudents whether A+ shows the tag to the tagged student. Defaults to `false`
 *   for points-tree tags because that representation omits the field. A `null` [color]
 *   distinguishes these tags from the full listing.
 * @property color the background color as `#rrggbb`. Absent only in the points-tree representation.
 * @property fontColor the readable foreground for [color], `#FFF` or `#000`.
 * @property fontWhite whether [fontColor] is white. A+ computes it from [color].
 * @property taggings the taggings URL filtered to this tag. Absent for hardcoded tags with no id.
 */
@Serializable
public data class UserTagDto(
    val id: Long? = null,
    val url: String? = null,
    val slug: String,
    val name: String,
    val description: String = "",
    val visibleToStudents: Boolean = false,
    val color: String? = null,
    val fontColor: String? = null,
    val fontWhite: Boolean = false,
    val taggings: String? = null,
)
