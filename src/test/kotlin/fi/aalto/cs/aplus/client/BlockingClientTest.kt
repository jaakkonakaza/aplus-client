package fi.aalto.cs.aplus.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlin.test.*

class BlockingClientTest {
    @Test
    fun `blocking listings preserve pagination and per-operation authentication`() {
        val requests = mutableListOf<HttpRequestData>()
        var token = "first"
        val suspendClient = mockClient(requests, tokenProvider = { token }) { request ->
            val page = request.url.parameters["page"]?.toLong() ?: 1L
            respondJson(pageJson(
                if (page == 1L) "$API_ROOT/courses/3/students?page=2" else null,
                listOf("""{"id":$page,"username":"student$page","is_external":false}"""),
                count = 2,
            ))
        }
        suspendClient.httpClient.use {
            AplusBlockingClient(suspendClient).use { client ->
                assertEquals(listOf(1L, 2L), client.students(3).map { it.id })
                token = "second"
                assertEquals(2, client.students(3).size)
            }
        }
        assertEquals(listOf("Token first", "Token first", "Token second", "Token second"),
            requests.map { it.headers["Authorization"] })
    }

    @Test
    fun `API errors propagate without a future exception wrapper`() {
        val suspendClient = mockClient { respondJson("{}", HttpStatusCode.Forbidden) }
        suspendClient.httpClient.use {
            assertFailsWith<AplusException.Forbidden> { AplusBlockingClient(suspendClient).me() }
        }
    }

    @Test
    fun `timeout cancels suspended work`() {
        val cancelled = CountDownLatch(1)
        val suspendClient = mockClient {
            try { awaitCancellation() } finally { cancelled.countDown() }
        }
        suspendClient.httpClient.use {
            assertFailsWith<TimeoutCancellationException> { AplusBlockingClient(suspendClient, 200).me() }
            assertTrue(cancelled.await(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `interrupting a caller cancels its request`() {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val suspendClient = mockClient {
            started.countDown()
            try { awaitCancellation() } finally { cancelled.countDown() }
        }
        suspendClient.httpClient.use {
            val thread = Thread {
                try { AplusBlockingClient(suspendClient).me() } catch (error: Throwable) { failure.set(error) }
            }
            thread.start()
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                thread.interrupt()
                thread.join(5_000)
                assertFalse(thread.isAlive)
                assertIs<InterruptedException>(failure.get())
                assertTrue(cancelled.await(5, TimeUnit.SECONDS))
            } finally {
                thread.interrupt()
                thread.join(5_000)
            }
        }
    }

    @Test
    fun `closing an authentication view preserves its owner and closing the owner releases transport`() {
        val transport = HttpClient(MockEngine { respondFixture("users_me.json") }) { aplusDefaults() }
        val owner = AplusBlockingClient(AplusClient(transport, BASE_URL, { TOKEN }, ownsHttpClient = true))
        owner.use {
            owner.withToken("another").use { assertEquals(517L, it.me().id) }
            assertEquals(517L, owner.me().id)
        }
        assertFails { owner.me() }
    }

    @Test
    fun `factory rejects nonpositive operation timeouts before requesting a token`() {
        for (timeout in listOf(0L, -1L)) {
            assertFailsWith<IllegalArgumentException> {
                AplusBlockingClient.create(BASE_URL, { error("Token must not be requested") }, timeoutMillis = timeout)
            }
        }
    }
}
