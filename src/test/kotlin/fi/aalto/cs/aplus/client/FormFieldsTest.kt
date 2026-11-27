package fi.aalto.cs.aplus.client

import fi.aalto.cs.aplus.client.dto.ExerciseInfoDto
import fi.aalto.cs.aplus.client.dto.FormSpecFieldDto
import fi.aalto.cs.aplus.client.dto.LocalizedFileField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A+ stores mooc-grader's exercise_info unchanged in `edit_course/operations/configure.py`.
 * Field type casing and i18n_ prefixes must therefore match the grader's output.
 */
class FormFieldsTest {

    @Test
    fun `only lowercase file fields are file fields, the way A plus compares them`() {
        // A+ compares type to lowercase file in exercise/cache/basetypes.py and
        // exercise/api/csv/submission_sheet.py. Case-insensitive matching would accept fields
        // the server excludes.

        val info = ExerciseInfoDto(
            formSpec = listOf(
                field("file1", "Solution", type = "file"),
                field("file2", "Other", type = "File"),
                field("answer", "Answer", type = "text"),
                field("note", "Note", type = "static"),
            ),
        )

        assertEquals(listOf("file1"), info.fileFields().map { it.key })
    }

    @Test
    fun `a file field is offered in every language the grader translated it into`() {
        val info = ExerciseInfoDto(
            formSpec = listOf(field("file1", "Solution")),
            formI18n = mapOf("Solution" to mapOf("en" to "Solution.scala", "fi" to "Ratkaisu.scala")),
        )

        assertEquals(
            mapOf(
                "en" to listOf(LocalizedFileField("file1", "Solution.scala")),
                "fi" to listOf(LocalizedFileField("file1", "Ratkaisu.scala")),
            ),
            info.fileFieldsByLanguage(),
        )
    }

    @Test
    fun `the fields of one language stay in form spec order`() {
        val info = ExerciseInfoDto(
            formSpec = listOf(field("file1", "First"), field("file2", "Second")),
            formI18n = mapOf("First" to mapOf("en" to "A.scala"), "Second" to mapOf("en" to "B.scala")),
        )

        assertEquals(
            listOf(LocalizedFileField("file1", "A.scala"), LocalizedFileField("file2", "B.scala")),
            info.fileFieldsByLanguage().getValue("en"),
        )
    }

    @Test
    fun `fields that are not file uploads are left out`() {
        val info = ExerciseInfoDto(
            formSpec = listOf(field("answer", "Answer", type = "text")),
            formI18n = mapOf("Answer" to mapOf("en" to "answer.txt")),
        )

        assertTrue(info.fileFieldsByLanguage().isEmpty())
    }

    @Test
    fun `an untranslated field disappears rather than falling back to its title`() {

        val info = ExerciseInfoDto(formSpec = listOf(field("file1", "Solution")), formI18n = emptyMap())

        assertTrue(info.fileFieldsByLanguage().isEmpty())
        assertEquals(1, info.fileFields().size)
    }

    @Test
    fun `only the languages form_i18n mentions appear, as the union across fields`() {
        val info = ExerciseInfoDto(
            formSpec = listOf(field("file1", "First"), field("file2", "Second")),
            formI18n = mapOf(
                "First" to mapOf("en" to "A.scala", "fi" to "A.scala"),
                "Second" to mapOf("en" to "B.scala"),
            ),
        )

        val byLanguage = info.fileFieldsByLanguage()

        assertEquals(setOf("en", "fi"), byLanguage.keys)
        assertEquals(2, byLanguage.getValue("en").size)
        assertEquals(
            listOf(LocalizedFileField("file1", "A.scala")),
            byLanguage.getValue("fi"),
            "a language only some fields are translated into carries only those fields",
        )
    }

    @Test
    fun `the grader's i18n key prefix is stripped off a title used as a filename`() {
        // mooc-grader's i18n_map in util/export.py prefixes values shared by two or more languages.

        assertEquals("Solution.scala", field("file1", "i18n_Solution.scala").filename)
        assertEquals("Solution.scala", field("file1", "Solution.scala").filename)
        assertEquals("", field("file1", "").filename)
    }

    @Test
    fun `only a leading prefix is stripped, so a filename may contain the marker`() {

        assertEquals("src/i18n_strings.scala", field("file1", "src/i18n_strings.scala").filename)
        assertEquals("i18n_strings.scala", field("file1", "i18n_i18n_strings.scala").filename)
    }

    @Test
    fun `the translation is the exact filename, and it is never prefixed`() {
        // i18n_map stores exact per-language filenames under the prefixed key.

        val info = ExerciseInfoDto(
            formSpec = listOf(field("file1", "i18n_Solution.scala")),
            formI18n = mapOf("i18n_Solution.scala" to mapOf("en" to "Solution.scala", "fi" to "Solution.scala")),
        )

        assertEquals(
            listOf(LocalizedFileField("file1", "Solution.scala")),
            info.fileFieldsByLanguage().getValue("fi"),
        )
    }

    private fun field(key: String, title: String, type: String = "file") =
        FormSpecFieldDto(key = key, type = type, title = title)

}
