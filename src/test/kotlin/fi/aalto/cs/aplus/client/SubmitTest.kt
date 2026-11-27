package fi.aalto.cs.aplus.client

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class SubmitTest {

    private val files = listOf(
        SubmissionFile("file1", "main.py", "print('hi')".encodeToByteArray()),
        SubmissionFile("file2", "models.py", "X = 1".encodeToByteArray()),
    )

    @Test
    fun `a created response reports the new submission`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val location = "$API_ROOT/submissions/21126/"
        val client = mockClient(requests) {
            respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.Created, headersOf(HttpHeaders.Location, location))
        }

        val result = client.submit(exerciseId = 24, files = files, groupId = 12, language = "fi")

        assertEquals(SubmitResult(location, emptyList()), result)

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v2/exercises/24/submissions/submit", request.url.encodedPath)
        assertAuthorized(request)
        assertIs<MultiPartFormDataContent>(request.body)
    }

    @Test
    fun `a submission created with grading errors carries them`(): Unit = runBlocking {
        val client = mockClient { respondJson("""{"errors":["The grader is unavailable"]}""", HttpStatusCode.Created) }

        assertEquals(listOf("The grader is unavailable"), client.submit(exerciseId = 24, files = files).errors)
        val garbled = mockClient { respondJson("<html>", HttpStatusCode.Created) }
        assertFailsWith<AplusException.Malformed> { garbled.submit(exerciseId = 24, files = files) }
    }

    @Test
    fun `a redirect is not an accepted submission`(): Unit = runBlocking {
        val client = mockClient {
            respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/elsewhere"))
        }

        assertFailsWith<AplusException.Http> { client.submit(exerciseId = 24, files = files) }
    }

    @Test
    fun `the magic aplus field carries the group and language`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests) { respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.Created) }

        client.submit(exerciseId = 24, files = files, groupId = 12, language = "fi")

        val body = encodedBody(requests.single())
        assertTrue(body.contains("name=\"${AplusClient.APLUS_FORM_FIELD}\""), body)
        assertTrue(body.contains("""{"group":12,"lang":"fi"}"""), body)
        assertTrue(body.contains("""filename="main.py""""), body)
        assertTrue(body.contains("""filename="models.py""""), body)
        assertTrue(body.contains("print('hi')"), body)
        assertTrue(body.contains("X = 1"), body)
    }

    @Test
    fun `submitting alone is the default, and it is the value the rest of A plus agrees on`(): Unit = runBlocking {
        // BaseExercise._check_submission_allowed accepts non-positive ids for solo submissions.
        // The submit button uses zero, and GroupSelectForm rejects negative ids.

        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests) { respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.Created) }

        client.submit(exerciseId = 24, files = files)

        assertEquals(0L, AplusClient.SUBMIT_ALONE_GROUP_ID)
        val body = encodedBody(requests.single())
        assertTrue(body.contains("""{"group":0,"lang":"en"}"""), body)
    }

    @Test
    fun `a quote in the language does not break the magic field`(): Unit = runBlocking {
        // Course configuration supplies the language. An unescaped quote could break the JSON
        // and cause a 400 response.

        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests) { respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.Created) }

        val language = "fi\"\\"

        client.submit(exerciseId = 24, files = files, groupId = 7, language = language)

        val body = encodedBody(requests.single())
        // Submission metadata is flat JSON.
        val magic = MAGIC_PREFIX + body.substringAfter(MAGIC_PREFIX).substringBefore('}') + "}"
        val decoded = Json.parseToJsonElement(magic).jsonObject

        assertEquals(7, decoded.getValue("group").jsonPrimitive.int)
        assertEquals(language, decoded.getValue("lang").jsonPrimitive.content, magic)
    }

    @Test
    fun `a quote in the file name is escaped in the content disposition`(): Unit = runBlocking {
        // form_i18n supplies filenames from server data. Unescaped quotes could truncate
        // the filename parameter.

        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests) { respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.Created) }

        val quoted = listOf(SubmissionFile("file1", """my"file.py""", "print('hi')".encodeToByteArray()))

        client.submit(exerciseId = 24, files = quoted)

        val body = encodedBody(requests.single())
        assertTrue(body.contains("""filename="my\"file.py""""), body)
    }

    @Test
    fun `a newline in the file name cannot break the header`(): Unit = runBlocking {
        // Quoting escapes CR and LF as well, so neither reaches the header as a control
        // character and neither ends the part's headers early.
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests) { respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.Created) }

        val broken = listOf(SubmissionFile("file1", "a\r\nb.py", "x".encodeToByteArray()))

        client.submit(exerciseId = 24, files = broken)

        val body = encodedBody(requests.single())
        assertTrue(body.contains("""filename="a\r\nb.py""""), body)
    }

    @Test
    fun `any other control character in the file name is refused`(): Unit = runBlocking {
        // Quoting escapes tab, CR, and LF. Ktor rejects other C0 characters in header values.

        val client = mockClient { respond(ByteReadChannel(ByteArray(0)), HttpStatusCode.Created) }

        val bell = listOf(SubmissionFile("file1", "a\u0007b.py", "x".encodeToByteArray()))

        assertFailsWith<IllegalArgumentException> { client.submit(exerciseId = 24, files = bell) }
    }

    @Test
    fun `a rejected submission raises`(): Unit = runBlocking {
        val client = mockClient { respondError(HttpStatusCode.Forbidden, "closed") }
        assertFailsWith<AplusException.Forbidden> { client.submit(exerciseId = 24, files = files) }
    }

    private companion object {
        const val MAGIC_PREFIX = """{"group":"""
    }

    private suspend fun encodedBody(request: HttpRequestData): String = coroutineScope {
        val content = assertIs<MultiPartFormDataContent>(request.body)
        val channel = ByteChannel(autoFlush = true)
        launch {
            content.writeTo(channel)
            channel.flushAndClose()
        }
        channel.toByteArray().decodeToString()
    }
}
