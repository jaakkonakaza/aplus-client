package fi.aalto.cs.aplus.courseconfig

import org.junit.jupiter.api.Test
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ExamplesTest {
    @Test
    fun `published JSON examples validate and survive serialization`() {
        for (name in listOf("minimal", "scala", "python")) {
            val resource = assertNotNull(javaClass.getResource("/$name.json"))
            val document = CourseConfig.parse(resource.readText())
            CourseConfig.validate(document)
            assertEquals(document, CourseConfig.parse(document.toJson()), name)
        }
    }

    @Test
    fun `complete manifests demonstrate every supported field`() {
        val documents = listOf("scala", "python").map { name ->
            Json.parseToJsonElement(assertNotNull(javaClass.getResource("/$name.json")).readText()).jsonObject
        }
        fun covers(serializer: KSerializer<*>, objects: List<JsonObject>) {
            val descriptor = serializer.descriptor
            val expected = (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet()
            assertEquals(expected, objects.flatMap { it.keys }.toSet(), descriptor.serialName)
        }
        covers(CourseConfig.Document.serializer(), documents)
        covers(CourseConfig.Module.serializer(), documents.flatMap { it.getValue("modules").jsonArray.map { item -> item.jsonObject } })
        covers(CourseConfig.RequiredPlugin.serializer(), documents.flatMap { it.getValue("requiredPlugins").jsonArray.map { item -> item.jsonObject } })
        covers(CourseConfig.Resources.serializer(), documents.map { it.getValue("resources").jsonObject })
        covers(CourseConfig.Callbacks.serializer(), documents.mapNotNull { it["callbacks"]?.jsonObject })
        covers(CourseConfig.ScalaRepl.serializer(), documents.mapNotNull { it["scalaRepl"]?.jsonObject })
        covers(CourseConfig.Grading.serializer(), documents.map { it.getValue("grading").jsonObject })
    }
}
