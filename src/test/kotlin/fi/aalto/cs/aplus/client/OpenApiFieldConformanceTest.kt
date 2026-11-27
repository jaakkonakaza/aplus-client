package fi.aalto.cs.aplus.client

import fi.aalto.cs.aplus.client.dto.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Kotlin fields may be nullable when the schema is stricter. DRF excludes read-only fields
 * from required even when A+ always sends them.
 *
 * FixtureDecodingTest covers endpoints with generic schemas. See [OpenApiConformanceTest]
 * for how endpoints are assigned to these checks.
 */
class OpenApiFieldConformanceTest {

    /** [label] must match the prefixes in [DRF_METHOD_FIELDS]. */
    private class Case(val label: String, val serializer: KSerializer<*>, schema: () -> JsonObject) {
        val schema: JsonObject by lazy(schema)

        override fun toString(): String = "$label as ${serializer.descriptor.serialName}"
    }

    private val cases: List<Case> = listOf(
        Case("User", UserDto.serializer()) { componentSchema("User") },
        Case("Course", CourseDto.serializer()) { componentSchema("Course") },
        Case("Exercise.course", CourseBriefDto.serializer()) { componentSchema("Exercise").property("course") },
        Case("UserBrief", UserBriefDto.serializer()) { usersResponse.property("results").items() },
        Case("StudentBrief", StudentDto.serializer()) { studentsResponse.property("results").items() },
        Case("CourseUsertag", UserTagDto.serializer()) { usertagsResponse.property("results").items() },
        Case("PaginatedCourseNews", Page.serializer(NewsItemDto.serializer())) { newsResponse },
        Case("CourseNews", NewsItemDto.serializer()) { newsResponse.property("results").items() },
        Case("CourseStudentGroupBrief", GroupDto.serializer()) { mygroupsResponse.property("results").items() },
        Case("CourseStudentGroupBrief.members", GroupMemberDto.serializer()) {
            mygroupsResponse.property("results").items().property("members").items()
        },
        Case("Exercise", ExerciseDto.serializer()) { componentSchema("Exercise") },
        Case("Submission", SubmissionDto.serializer()) { componentSchema("Submission") },
        Case("Submission.exercise", SubmissionExerciseDto.serializer()) {
            componentSchema("Submission").property("exercise")
        },
        Case("Submission.submitters", UserBriefDto.serializer()) {
            componentSchema("Submission").property("submitters").items()
        },
        Case("Submission.grader", UserBriefDto.serializer()) { componentSchema("Submission").property("grader") },
        Case("Submission.files", SubmittedFileDto.serializer()) {
            componentSchema("Submission").property("files").items()
        },
        Case("SubmissionBrief", SubmissionBriefDto.serializer()) { componentSchema("SubmissionBrief") },
    )

    @TestFactory
    fun `every DTO matches the schema it decodes`(): List<DynamicTest> =
        cases.map { case -> dynamicTest(case.toString()) { assertConforms(case.label, case.schema, case.serializer) } }

    @Test
    fun `every DRF method field exemption is still needed`() {
        val byLabel = cases.associateBy { it.label }
        val stillNeeded = DRF_METHOD_FIELDS.filter { entry ->
            val case = byLabel.getValue(entry.substringBeforeLast('.'))
            val name = entry.substringAfterLast('.')
            val type = case.schema.property(name)["type"]?.jsonPrimitive?.content
            type != null && !fieldsOf(case.serializer).getValue(name).kind.matches(type)
        }

        assertEquals(
            DRF_METHOD_FIELDS,
            stillNeeded.toSet(),
            "the A+ spec and the DTO now agree on these, so they no longer belong in DRF_METHOD_FIELDS",
        )
    }

    @Test
    fun `the news language sentinel is the only one the spec has`() {
        // Language codes depend on deployment configuration. Only the all-languages sentinel
        // is a library constant.

        val values = newsResponse.property("results").items().property("language").enumValues()

        assertTrue(
            NewsItemDto.LANGUAGE_ALL in values,
            "language no longer accepts \"${NewsItemDto.LANGUAGE_ALL}\": $values",
        )
        assertEquals(
            emptyList(),
            (values - NewsItemDto.LANGUAGE_ALL).filterNot { it.matches(LANGUAGE_CODE) },
            "language gained a value that is not a language code. If it is a new sentinel, " +
                    "NewsItemDto needs a constant for it and NewsItemDto.appliesTo needs to honour it",
        )
    }

    private fun assertConforms(label: String, schema: JsonObject, serializer: KSerializer<*>) {
        val properties = schema["properties"]?.jsonObject?.mapValues { (_, value) -> resolve(value.jsonObject) }
            ?: fail("$label has no properties in the spec, so it cannot be checked field by field")
        val descriptor = serializer.descriptor
        val fields = fieldsOf(serializer)

        assertEquals(
            emptySet(),
            fields.keys - properties.keys,
            "$label: ${descriptor.serialName} declares fields the A+ spec does not describe",
        )
        assertEquals(
            emptySet(),
            properties.keys - fields.keys,
            "$label: the A+ spec describes properties ${descriptor.serialName} does not model",
        )

        properties.forEach { (name, property) ->
            val field = fields.getValue(name)

            if (property["nullable"]?.jsonPrimitive?.content == "true") {
                assertTrue(field.isNullable, "$label.$name is nullable in the spec but not in ${descriptor.serialName}")
            }

            val type = property["type"]?.jsonPrimitive?.content
            if (type != null && "$label.$name" !in DRF_METHOD_FIELDS) {
                assertTrue(
                    field.kind.matches(type),
                    "$label.$name is $type in the spec but ${field.kind} in ${descriptor.serialName}",
                )
            }
        }
    }

    /** Field names must use the same naming strategy as [AplusJson.default]. */
    private fun fieldsOf(serializer: KSerializer<*>): Map<String, SerialDescriptor> {
        val descriptor = serializer.descriptor
        return (0 until descriptor.elementsCount).associate { index ->
            val name = JsonNamingStrategy.SnakeCase
                .serialNameForJson(descriptor, index, descriptor.getElementName(index))
            name to descriptor.getElementDescriptor(index)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun SerialKind.matches(type: String): Boolean = when (type) {
        "integer" -> this == PrimitiveKind.INT || this == PrimitiveKind.LONG
        "number" -> this == PrimitiveKind.DOUBLE || this == PrimitiveKind.FLOAT
        "string" -> this == PrimitiveKind.STRING
        "boolean" -> this == PrimitiveKind.BOOLEAN
        "array" -> this == StructureKind.LIST

        "object" -> this == StructureKind.CLASS || this == StructureKind.MAP || this is PolymorphicKind
        else -> false
    }

    private val newsResponse: JsonObject by lazy {
        checkNotNull(responseSchema("/api/v2/courses/{course_id}/news/", "get"))
    }

    private val mygroupsResponse: JsonObject by lazy {
        checkNotNull(responseSchema("/api/v2/courses/{course_id}/mygroups/", "get"))
    }

    private val usersResponse: JsonObject by lazy {
        checkNotNull(responseSchema("/api/v2/users/", "get"))
    }

    private val studentsResponse: JsonObject by lazy {
        checkNotNull(responseSchema("/api/v2/courses/{course_id}/students/", "get"))
    }

    private val usertagsResponse: JsonObject by lazy {
        checkNotNull(responseSchema("/api/v2/courses/{course_id}/usertags/", "get"))
    }

    private fun componentSchema(name: String): JsonObject = componentSchemaOf(name)

    private fun JsonObject.property(name: String): JsonObject =
        resolve(getValue("properties").jsonObject.getValue(name).jsonObject)

    private fun JsonObject.items(): JsonObject = resolve(getValue("items").jsonObject)

    private fun JsonObject.enumValues(): List<String> =
        getValue("enum").jsonArray.map { it.jsonPrimitive.content }

    private fun resolve(schema: JsonObject): JsonObject = resolveSchemaRef(schema)

    private companion object {

        val LANGUAGE_CODE = Regex("[a-z]{2,3}(-[A-Za-z0-9]+)*")

        /**
         * Properties whose schema types come from DRF fallback behavior.
         *
         * DRF cannot infer SerializerMethodField or custom to_representation results. It marks
         * these as read-only strings, though captured responses contain booleans, integers,
         * arrays, or grader JSON.
         *
         * Names and nullability remain checked. Only type comparisons are exempted.
         */
        val DRF_METHOD_FIELDS = setOf(

            "User.is_external",
            "UserBrief.is_external",
            "StudentBrief.is_external",
            "Submission.submitters.is_external",
            "Submission.grader.is_external",
            "Exercise.is_submittable",
            "CourseUsertag.font_white",

            "Exercise.consecutive_order",
            "Submission.grade",
            "SubmissionBrief.grade",

            "User.enrolled_courses",
            "User.staff_courses",
            "User.teacher_courses",
            "User.assistant_courses",
            "StudentBrief.tag_slugs",
            "Submission.grading_data",
        )
    }
}
