package fi.aalto.cs.aplus.client.dto

import kotlinx.serialization.Serializable

/**
 * One item of `GET /courses/{id}/news`.
 *
 * @property audience who the item targets, a CourseInstance.ENROLLMENT_AUDIENCE value. `1` targets
 *   students with an internal account, `2` for external ones, and `3`, the default, for everyone.
 * @property language the target language, or [LANGUAGE_ALL] for every language.
 * @property pin whether the item is pinned to the course front page.
 */
@Serializable
public data class NewsItemDto(
    val id: Long,
    val url: String? = null,
    val title: String,
    val body: String,
    val publish: String? = null,
    val audience: Int = 3,
    val language: String = LANGUAGE_ALL,
    val pin: Boolean = false,
) {
    /** Whether this item should be shown to a reader using [courseLanguage]. */
    public fun appliesTo(courseLanguage: String): Boolean =
        language == LANGUAGE_ALL || language == courseLanguage

    public companion object {
        /** [language] value meaning "every language". */
        public const val LANGUAGE_ALL: String = "-"
    }
}
