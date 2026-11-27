package fi.aalto.cs.aplus.client

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * The IntelliJ plugin shares HttpClient with other features, which must survive client closure.
 * MockEngine exercises the factory's ownership rule without opening a network connection.
 */
class ClientSetupTest {

    private fun engine() = MockEngine { respondFixture("users_me.json") }

    @Test
    fun `a client that owns its HttpClient closes it`(): Unit = runBlocking {
        val client = AplusClient(
            httpClient = HttpClient(engine()) { aplusDefaults() },
            baseUrl = BASE_URL,
            tokenProvider = { TOKEN },
            ownsHttpClient = true,
        )

        assertEquals("http://localhost:8000/api/v2", client.baseUrl)
        assertEquals(517L, client.me().id)

        client.close()
        assertFails("an owned HttpClient must be shut down by close()") { client.me() }
    }

    @Test
    fun `a client given an HttpClient leaves it open`(): Unit = runBlocking {
        val engine = engine()
        val httpClient = HttpClient(engine) { aplusDefaults() }
        val client = AplusClient(
            httpClient = httpClient,
            baseUrl = BASE_URL,
            tokenProvider = { TOKEN },
        )

        assertEquals(517L, client.me().id)

        client.close()

        assertEquals(517L, client.me().id)
        httpClient.close()
    }

    @Test
    fun `create builds on the bundled OkHttp engine and applies the configure block`() {
        var configured = false
        val client = AplusClient.create(
            baseUrl = "https://plus.cs.aalto.fi/",
            tokenProvider = { TOKEN },
            configure = {
                configured = true

                engine { config { retryOnConnectionFailure(true) } }
            },
        )

        client.use {
            assertEquals("https://plus.cs.aalto.fi/api/v2", it.baseUrl)
            assertIs<OkHttpEngine>(it.httpClient.engine)
            assertTrue(configured, "the configure block must run")
        }
    }

    @Test
    fun `create rejects invalid base URLs before allocating its transport`() {
        var configured = false
        for (url in listOf("", "file:///tmp/course", "https://user:password@aplus.test", "https://aplus.test?query=1")) {
            assertFailsWith<IllegalArgumentException>(url) {
                AplusClient.create(url, { TOKEN }) { configured = true }
            }
        }
        assertFalse(configured)
    }

    @Test
    fun `the token provider is consulted per request`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        var token = "first"
        val client = mockClient(requests, tokenProvider = { token }) { respondFixture("users_me.json") }

        client.me()
        token = "second"
        client.me()

        assertEquals(listOf("Token first", "Token second"), requests.map { it.headers["Authorization"] })
    }

    @Test
    fun `a null token sends no Authorization header`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        mockClient(requests, token = null) { respondFixture("users_me.json") }.me()

        assertNull(requests.single().headers["Authorization"])
    }

    @Test
    fun `base urls are normalized to the api root`() {
        assertEquals("http://localhost:8000/api/v2", AplusClient.normalizeBaseUrl("http://localhost:8000"))
        assertEquals("http://localhost:8000/api/v2", AplusClient.normalizeBaseUrl("http://localhost:8000/"))
        assertEquals("http://localhost:8000/api/v2", AplusClient.normalizeBaseUrl("http://localhost:8000/api/v2/"))
        assertEquals("https://plus.cs.aalto.fi/api/v2", AplusClient.normalizeBaseUrl("https://plus.cs.aalto.fi"))
        assertEquals("https://example.test/api/v2", AplusClient.normalizeBaseUrl("https://EXAMPLE.TEST"))
        assertEquals("https://localhost/prefix/api/v2", AplusClient.normalizeBaseUrl("https://localhost/prefix/"))
    }

    @Test
    fun `base urls with credentials, other schemes, ambiguous suffixes or no host are rejected`() {
        for (base in listOf(
            "", "   ",
            "ftp://localhost",
            "https://user@localhost",
            "https://localhost?x=1",
            "https://localhost/#x",
            "https://localhost/../prefix",
        )) {
            assertFailsWith<IllegalArgumentException>("\"$base\" was accepted as a base URL") {
                AplusClient.normalizeBaseUrl(base)
            }
        }
    }
}
