package fi.aalto.cs.aplus.client

import fi.aalto.cs.aplus.client.dto.UserDto
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class BinaryDownloadTest {
    @Test
    fun `a 304 location never triggers another request`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests) {
            respond("", HttpStatusCode.NotModified, headersOf(HttpHeaders.Location, "/files/final"))
        }
        assertFailsWith<AplusException> { client.readBytes("$BASE_URL/files/start", 32, maxRedirects = 2) }
        assertEquals(1, requests.size)
    }
    @Test
    fun `submission attachments preserve arbitrary binary bytes and use the upstream route`(): Unit = runBlocking {
        val bytes = byteArrayOf(0, -1, -128, 13, 10, 42)
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests) { respond(ByteReadChannel(bytes)) }
        assertContentEquals(bytes, client.submissionFile(42, 7, maxBytes = bytes.size))
        assertEquals("/api/v2/submissions/42/files/7", requests.single().url.encodedPath)
        assertAuthorized(requests.single())
    }

    @Test
    fun `binary cap stops reading before EOF and closes the response`(): Unit = runBlocking {
        val body = ByteChannel(autoFlush = true)
        body.writeFully(ByteArray(64))
        val client = mockClient { respond(body) }
        try {
            withTimeout(2.seconds) {
                assertFailsWith<AplusException.LimitExceeded> { client.readBytes("$BASE_URL/files/7", maxBytes = 16) }
            }
            assertTrue(body.isClosedForRead)
        } finally {
            body.cancel()
        }
    }

    @Test
    fun `binary downloads reject foreign origins before requesting`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests) { respondJson("ignored") }
        for (url in listOf(
            "https://evil.test/files/7",
            "http://user@localhost:8000/files/7",
            "$BASE_URL/files/7#secret"
        )) {
            assertFailsWith<IllegalArgumentException> { client.readBytes(url, 32) }
        }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `same-origin redirects capture one token and reject cross-origin forwarding`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        var tokenReads = 0
        val client = mockClient(requests, tokenProvider = { tokenReads++; TOKEN }) { request ->
            when (request.url.encodedPath) {
                "/files/start" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/files/final"))
                "/files/unsafe" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "https://evil.test/files/7")
                )

                else -> respond("bytes")
            }
        }
        assertEquals("bytes", client.readBytes("$BASE_URL/files/start", 32, maxRedirects = 2).decodeToString())
        assertEquals(1, tokenReads)
        requests.forEach(::assertAuthorized)
        assertFailsWith<IllegalArgumentException> { client.readBytes("$BASE_URL/files/unsafe", 32, maxRedirects = 2) }
        assertEquals(3, requests.size)
    }

    @Test
    fun `redirect aliases are caller-owned and rechecked after mapping`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        fun client(linkResolver: (String) -> String) = mockClient(requests, linkResolver = linkResolver) { request ->
            if (request.url.encodedPath.endsWith("start")) respond(
                "", HttpStatusCode.Found,
                headersOf(HttpHeaders.Location, "https://public.example/files/final")
            )
            else respond("ok")
        }
        val aliased = client { it.replace("https://public.example", BASE_URL) }
        assertEquals("ok", aliased.readBytes("$BASE_URL/files/start", 32, 1).decodeToString())
        val escaping = client { if ("final" in it) "https://evil.test/files/7" else it }
        assertFailsWith<IllegalArgumentException> { escaping.readBytes("$BASE_URL/files/start", 32, 1) }
        assertEquals(3, requests.size)
    }

    @Test
    fun `redirect budget stops loops`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client =
            mockClient(requests) { respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/files/loop")) }
        assertFailsWith<AplusException.Http> { client.readBytes("$BASE_URL/files/loop", 32, maxRedirects = 1) }
        assertEquals(2, requests.size)
    }

    @Test
    fun `cancelled binary reads release their response`(): Unit = runBlocking {
        val body = ByteChannel(autoFlush = true)
        val entered = CompletableDeferred<Unit>()
        val channel = object : ByteReadChannel by body {
            override suspend fun awaitContent(min: Int): Boolean {
                entered.complete(Unit)
                return body.awaitContent(min)
            }
        }
        val client = mockClient { respond(channel) }
        val request = async { client.readBytes("$BASE_URL/files/7", 32) }
        entered.await()
        request.cancel()
        assertFailsWith<CancellationException> { request.await() }
        assertTrue(body.isClosedForRead)
    }

    @Test
    fun `JSON metadata reports the final URL and decoded byte count`(): Unit = runBlocking {
        val text = """{"id":1,"username":"ä","is_external":false}"""
        val client = mockClient { respondJson(text) }
        val response = client.readJson("users/me", UserDto.serializer())
        assertEquals(1L, response.value.id)
        assertEquals("$API_ROOT/users/me", response.requestUrl)
        assertEquals(text.encodeToByteArray().size, response.bodyBytes)
        assertFailsWith<IllegalArgumentException> { client.readJson("$BASE_URL/admin", UserDto.serializer()) }
    }
}
