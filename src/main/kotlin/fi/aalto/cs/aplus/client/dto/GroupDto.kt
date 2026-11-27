package fi.aalto.cs.aplus.client.dto

import fi.aalto.cs.aplus.client.AplusRoutes
import kotlinx.serialization.Serializable

/**
 * One item of `GET /courses/{id}/mygroups`, a submission group the user belongs to.
 */
@Serializable
public data class GroupDto(
    val id: Long,
    val url: String? = null,
    val members: List<GroupMemberDto> = emptyList(),
    val timestamp: String? = null,
)

/**
 * A member of a [GroupDto].
 *
 * A+ uses UserMinimalSerializer to expose only data students may see about each other.
 * Username, student number, and email are omitted.
 *
 * Group member ids use a separate sequence from user ids. [userIdOrNull] parses the user id from [url].
 */
@Serializable
public data class GroupMemberDto(
    val id: Long,
    val url: String? = null,
    val fullName: String? = null,
) {
    /**
     * The A+ user id, the final segment of [url]. Returns `null` for absent URLs and
     * nonnumeric final segments.
     *
     * [id] identifies the group member and is never a user id.
     */
    public val userIdOrNull: Long?
        get() = AplusRoutes.idFromApiUrl(url)
}
