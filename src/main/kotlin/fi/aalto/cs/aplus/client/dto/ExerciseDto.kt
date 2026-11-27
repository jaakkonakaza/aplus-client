package fi.aalto.cs.aplus.client.dto

import fi.aalto.cs.aplus.client.TruthyBooleanSerializer
import kotlinx.serialization.Serializable

/**
 * An exercise round from `GET /courses/{id}/exercises`.
 */
@Serializable
public data class ExerciseModuleDto(
    val id: Long,
    val url: String? = null,
    val htmlUrl: String? = null,
    val displayName: String,
    val isOpen: Boolean = false,
    val isHidden: Boolean = false,
    val readingOpeningTime: String? = null,
    val openingTime: String? = null,
    val closingTime: String? = null,
    val exercises: List<ExerciseBriefDto> = emptyList(),
)

/**
 * An exercise listed inside [ExerciseModuleDto].
 *
 * @property maxSubmissions `0` means unlimited in A+'s `exercise/exercise_models.py`.
 *   Use [submissionLimit] for display. The default also covers an omitted field.
 * @property hasSubmittableFiles whether the exercise accepts uploads. A+ sends `false`, `null`,
 *   or `[]` for no files, so the client decodes this as a non-null [Boolean].
 */
@Serializable
public data class ExerciseBriefDto(
    val id: Long,
    val url: String? = null,
    val htmlUrl: String? = null,
    val displayName: String,
    val maxPoints: Int = 0,
    val maxSubmissions: Int = 0,
    val hierarchicalName: String? = null,
    val difficulty: String = "",
    @Serializable(with = TruthyBooleanSerializer::class)
    val hasSubmittableFiles: Boolean = false,
) {
    /**
     * The submission limit, or `null` for unlimited submissions.
     *
     * The listing has no per-student deviation.
     */
    public val submissionLimit: Int? get() = maxSubmissions.takeIf { it > 0 }
}

/**
 * `GET /exercises/{id}`, the full exercise resource.
 *
 * @property maxSubmissions uses the unlimited sentinel `0`, as in [ExerciseBriefDto.maxSubmissions].
 */
@Serializable
public data class ExerciseDto(
    val id: Long,
    val url: String? = null,
    val htmlUrl: String? = null,
    val displayName: String,
    val name: String? = null,
    val maxPoints: Int = 0,
    val maxSubmissions: Int = 0,
    val hierarchicalName: String? = null,
    val difficulty: String = "",
    val course: CourseBriefDto? = null,
    val isSubmittable: Boolean = false,
    val postUrl: String? = null,
    val consecutiveOrder: Int? = null,
    val parentName: String? = null,
    val exerciseInfo: ExerciseInfoDto? = null,
    val templates: String? = null,
    val submissions: String? = null,
    val mySubmissions: String? = null,
    val myStats: String? = null,
    val statistics: String? = null,
    val status: String? = null,
)

/**
 * The grader-supplied submission form description.
 *
 * @property formSpec the fields the grader expects, in order.
 * @property formI18n translations keyed first by [FormSpecFieldDto.title], then language code.
 *   For example, `{"Solution": {"en": "Solution.scala", "fi": "Ratkaisu.scala"}}`.
 *   The outer key is the untranslated title.
 */
@Serializable
public data class ExerciseInfoDto(
    val formSpec: List<FormSpecFieldDto> = emptyList(),
    val formI18n: Map<String, Map<String, String>> = emptyMap(),
) {
    /**
     * The file upload fields required in a submission.
     *
     * A+ compares the type to lowercase `file` in `exercise/cache/basetypes.py` and
     * `exercise/api/csv/submission_sheet.py`. It stores the grader's exercise_info unchanged
     * in `edit_course/operations/configure.py`. Case-insensitive matching would disagree
     * with the server about which exercises accept file submissions.
     */
    public fun fileFields(): List<FormSpecFieldDto> = formSpec.filter { it.type == "file" }

    /**
     * File upload fields grouped by language, using translated filenames.
     *
     * Each language maps to fields in [formSpec] order. [formI18n] can translate one title to
     * `Solution.scala` in English and `Ratkaisu.scala` in Finnish.
     *
     * Only languages present in [formI18n] appear. Fields without translations are omitted,
     * even when required by the exercise. An empty result therefore does not prove the exercise
     * accepts no files. Use [fileFields] to check, and [FormSpecFieldDto.filename] to fall back to a title.
     */
    public fun fileFieldsByLanguage(): Map<String, List<LocalizedFileField>> {
        val byLanguage = LinkedHashMap<String, MutableList<LocalizedFileField>>()
        fileFields().forEach { field ->
            formI18n[field.title].orEmpty().forEach { (language, filename) ->
                byLanguage.getOrPut(language) { mutableListOf() }.add(LocalizedFileField(field.key, filename))
            }
        }
        return byLanguage
    }
}

/**
 * One field of [ExerciseInfoDto.formSpec].
 *
 * @property key the multipart part name for this field.
 * @property type the field type, most often `file`. See [ExerciseInfoDto.fileFields].
 * @property title the label and [ExerciseInfoDto.formI18n] key. For a `file` field it is
 *   the filename and can carry mooc-grader's [I18N_PREFIX]. Read it through [filename].
 */
@Serializable
public data class FormSpecFieldDto(
    val key: String,
    val type: String,
    val title: String = "",
    val required: Boolean = false,
) {
    /**
     * [title] as a filename, with [I18N_PREFIX] removed.
     *
     * Mooc-grader builds file field titles through `i18n_map` in `util/export.py` and gitmanager.
     * A+ stores the resulting title and form_i18n key unchanged. The prefix is added only when
     * two or more course languages share a value. Single-language courses and translated
     * filenames keep the bare title.
     *
     * This is a fallback. On prefixed keys, `i18n_map` collapses whitespace to `_` and adds
     * `_duplicate` for collisions. Those changes cannot be reversed. Prefer the exact filename
     * in [ExerciseInfoDto.formI18n], available through [ExerciseInfoDto.fileFieldsByLanguage].
     */
    public val filename: String get() = title.removePrefix(I18N_PREFIX)

    public companion object {
        /**
         * The translation-key prefix mooc-grader adds to [title].
         *
         * `i18n_map` in mooc-grader's `util/export.py` adds it. A+ passes it through unchanged.
         * [filename] removes it.
         */
        public const val I18N_PREFIX: String = "i18n_"
    }
}

/**
 * One file upload field of [ExerciseInfoDto.fileFieldsByLanguage], resolved for one language.
 *
 * @property key the [FormSpecFieldDto.key] to submit the file under, i.e. the multipart part
 *   name. The same key in every language.
 * @property filename the name the file has in this language, which is the
 *   [ExerciseInfoDto.formI18n] translation of the field's [FormSpecFieldDto.title].
 */
public data class LocalizedFileField(
    val key: String,
    val filename: String,
)
