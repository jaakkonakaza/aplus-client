package fi.aalto.cs.aplus.client

import fi.aalto.cs.aplus.client.dto.GradeSubmissionRequest
import fi.aalto.cs.aplus.client.dto.SubmissionStatusDto
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class SubmissionOperationsTest {
    @Test
    fun `submission listings send no ordering or page size, which A+ ignores`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests) { respondJson(pageJson(null, listOf("""{"id":12,"grade":3}"""))) }
        assertEquals(12L, client.exerciseSubmissions(24).single().id)
        assertEquals(3, client.latestExerciseSubmissions(24, "me").single().grade)
        assertEquals("/api/v2/exercises/24/submissions", requests[0].url.encodedPath)
        assertEquals("/api/v2/exercises/24/submissions/me", requests[1].url.encodedPath)
        assertTrue(requests.all { it.url.parameters.isEmpty() })
    }

    @Test
    fun `teacher write sends upstream form fields without grading_data and never retries a rejection`(): Unit =
        runBlocking {
            val requests = mutableListOf<HttpRequestData>()
            val client = mockClient(requests) { respondJson("", HttpStatusCode.Created) }
            client.createGradedSubmission(24, GradeSubmissionRequest(listOf(517), 3, feedback = "Good"))
            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v2/exercises/24/submissions", request.url.encodedPath)
            val body = Json.parseToJsonElement(
                (request.body as OutgoingContent.ReadChannelContent).readFrom().toByteArray().decodeToString()
            ).jsonObject
            assertEquals(setOf("students_by_user_id", "points", "feedback"), body.keys)
            assertEquals(listOf(517L), body.getValue("students_by_user_id").jsonArray.map { it.jsonPrimitive.long })
            assertEquals(3, body.getValue("points").jsonPrimitive.int)
            assertEquals("Good", body.getValue("feedback").jsonPrimitive.content)
            val rejected = mockClient(requests) { respondJson("denied", HttpStatusCode.Forbidden) }
            assertFailsWith<AplusException.Forbidden> {
                rejected.createGradedSubmission(24, GradeSubmissionRequest(listOf(517), 3))
            }
            assertEquals(2, requests.size)
        }

    @Test
    fun `status vocabulary matches upstream including terminal failures`() {
        val upstream = componentSchemaOf("Submission").getValue("properties").jsonObject
            .getValue("status").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(upstream, SubmissionStatusDto.entries.map { it.value }.toSet())
        assertNull(SubmissionStatusDto.parse("future-status"))
        assertTrue(SubmissionStatusDto.isKnown("invalidated"))
    }
}
