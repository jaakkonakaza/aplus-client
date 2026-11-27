package fi.aalto.cs.aplus.courseconfig

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CourseConfigTest {
    @Test
    fun `authoring requires a positive course id but decoding stays permissive`() {
        for (id in listOf("oops", "0", "-1", "9223372036854775808")) {
            val document = CourseConfig.parse("""{"id":"$id","name":"Course","modules":[],"exerciseModules":{}}""")
            assertEquals(id, document.id)
            assertFailsWith<IllegalArgumentException> { document.toJson() }
        }
        CourseConfig.validate(CourseConfig.Document(Long.MAX_VALUE.toString(), "Course"))
    }
    @Test
    fun `full plugin document preserves every populated field`() {
        val input = checkNotNull(javaClass.getResource("/course-config-2026.json")).readText()
        val document = CourseConfig.parse(input)
        assertEquals("524", document.id)
        assertEquals("4.4", document.version)
        assertEquals(42, document.modules.size)
        assertEquals(3, document.requiredPlugins.size)
        assertEquals("https://plus.cs.aalto.fi/", document.aPlusUrl)
        assertEquals(625, document.grading?.points?.get("5")?.get("C"))
        val output = document.toJson()
        assertEquals(document, CourseConfig.parse(output))
        val expected = Json.parseToJsonElement(input).jsonObject
        // Omitted default versions and explicit "1.0" have the same meaning to both consumers.
        val modules = expected.getValue("modules").jsonArray.map { entry ->
            val module = entry.jsonObject
            if (module["version"]?.jsonPrimitive?.content == "1.0") JsonObject(module - "version") else module
        }
        assertEquals(JsonObject(expected + ("modules" to JsonArray(modules))), Json.parseToJsonElement(output))
    }

    @Test
    fun `Hanami subset remains supported and unknown fields are ignored`() {
        val document =
            CourseConfig.parse("""{"id":"524","name":"O1","modules":[{"name":"Hello","url":"https://example.test/Hello.zip"}],"exerciseModules":{"42":{"en":"Hello"}},"future":true}""")
        assertNull(document.aPlusUrl)
        assertEquals(emptyList(), document.languages)
        assertEquals("1.0", document.modules.single().version)
        assertEquals(document, CourseConfig.parse(document.toJson()))
    }

    @Test
    fun `empty catalog fields remain present for existing consumers`() {
        val document = CourseConfig.Document(id = "524", name = "O1")
        val output = Json.parseToJsonElement(document.toJson()).jsonObject
        assertEquals(JsonArray(emptyList()), output["modules"])
        assertEquals(JsonObject(emptyMap()), output["exerciseModules"])
        assertEquals(document, CourseConfig.parse(document.toJson()))
    }

    @Test
    fun `JSON requires identity and catalog fields even with constructor defaults`() {
        val input = Json.parseToJsonElement(CourseConfig.Document("524", "O1").toJson()).jsonObject
        for (field in listOf("id", "name", "modules", "exerciseModules")) {
            assertFailsWith<SerializationException>(field) {
                CourseConfig.parse(JsonObject(input - field).toString())
            }
        }
    }

    @Test
    fun `reading ignores authoring errors in fields a consumer may not use`() {
        val document =
            CourseConfig.parse("""{"id":"524","name":"O1","modules":[],"exerciseModules":{},"autoInstall":["Missing"],"grading":{"style":"o1","points":{"pass":{"A":-1}}}}""")
        assertEquals("O1", document.name)
        assertEquals(listOf("Missing"), document.autoInstall)
        val errors = CourseConfig.validationErrors(document)
        kotlin.test.assertTrue(errors.any { it.startsWith("autoInstall[0]:") })
        kotlin.test.assertTrue(errors.any { it.startsWith("grading.points[pass]") })
        assertFailsWith<IllegalArgumentException> { CourseConfig.validate(document) }
        assertFailsWith<IllegalArgumentException> { document.toJson() }
    }

    @Test
    fun `rejects malformed versions duplicate modules and invalid mapping ids`() {
        val base = CourseConfig.Document(id = "524", name = "O1")
        for (version in listOf("1", "-1.0", "1.2.3", "2147483648.0", "1.+2")) {
            assertFailsWith<IllegalArgumentException> { base.copy(version = version).toJson() }
        }
        val module = CourseConfig.Module("Hello", "https://example.test/Hello.zip")
        assertFailsWith<IllegalArgumentException> { CourseConfig.validate(base.copy(modules = listOf(module, module))) }
        assertFailsWith<IllegalArgumentException> { CourseConfig.validate(base.copy(exerciseModules = mapOf(0L to emptyMap()))) }
    }

    @Test
    fun `student-created modules need no download and future grading styles remain readable`() {
        val document = CourseConfig.Document(
            id = "524",
            name = "O1",
            exerciseModules = mapOf(42L to mapOf("fi" to "Ave")),
            scalaRepl = CourseConfig.ScalaRepl(mapOf("Ave" to listOf("import o1.*"))),
            grading = CourseConfig.Grading("future", mapOf("pass" to mapOf("new-category" to 1))),
        )
        assertEquals(document, CourseConfig.parse(document.toJson()))
    }

    @Test
    fun `reports multiple invalid fields with paths`() {
        val document = CourseConfig.Document("", "", emptyList(), emptyMap(), autoInstall = listOf("Missing"))
        val errors = CourseConfig.validationErrors(document)
        assertEquals(3, errors.size)
        kotlin.test.assertTrue(errors.any { it.startsWith("autoInstall[0]:") })
        val exception = assertFailsWith<IllegalArgumentException> { document.toJson() }
        kotlin.test.assertTrue(exception.message.orEmpty().contains("id:"))
        kotlin.test.assertTrue(exception.message.orEmpty().contains("name:"))
    }

    @Test
    fun `invalid module mapping and grading fields report their own paths`() {
        val base = CourseConfig.Document("524", "O1")
        val module = CourseConfig.Module("Hello", "https://example.test/Hello.zip")
        val cases = listOf(
            base.copy(modules = listOf(module.copy(name = ""))) to "modules[0].name:",
            base.copy(modules = listOf(module.copy(url = ""))) to "modules[0].url:",
            base.copy(modules = listOf(module.copy(version = "bad"))) to "modules[0].version:",
            base.copy(exerciseModules = mapOf(42L to mapOf("" to "Hello"))) to "exerciseModules[42]:",
            base.copy(exerciseModules = mapOf(42L to mapOf("en" to ""))) to "exerciseModules[42][en]:",
            base.copy(grading = CourseConfig.Grading("o1", mapOf("pass" to mapOf("A" to 1)))) to "grading.points[pass]:",
            base.copy(grading = CourseConfig.Grading("total", mapOf("pass" to mapOf("other" to 1)))) to "grading.points[pass]:",
        )
        for ((document, path) in cases) {
            val errors = CourseConfig.validationErrors(document)
            kotlin.test.assertTrue(errors.any { it.startsWith(path) }, "$path missing from $errors")
        }
    }
}
