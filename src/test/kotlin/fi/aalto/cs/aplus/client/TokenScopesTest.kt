package fi.aalto.cs.aplus.client

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TokenScopesTest {
    @Test
    fun `budget views retain authentication without changing their parent's limits`(): Unit = runBlocking {
        val root = mockClient { request ->
            assertEquals("Token alice", request.headers["Authorization"])
            respondJson("""{"id":1,"username":"alice","is_external":false}""")
        }.withToken("alice")
        val bounded = root.withLimits(root.limits.copy(maxResponseBytes = 8))
        assertSame(root.httpClient, bounded.httpClient)
        assertFailsWith<AplusException.LimitExceeded> { bounded.me() }
        assertEquals("alice", root.me().username)
        bounded.close()
        assertEquals("alice", root.me().username)
    }

    @Test
    fun `concurrent views keep credentials on all their own pages`(): Unit = runBlocking {
        val arrived = List(2) { AtomicInteger() }
        val bothViewsStarted = List(2) { CompletableDeferred<Unit>() }
        val root = mockClient(Collections.synchronizedList(mutableListOf())) { request ->
            val pageIndex = if (request.url.parameters["page"] == null) 0 else 1
            if (arrived[pageIndex].incrementAndGet() == 2) bothViewsStarted[pageIndex].complete(Unit)
            bothViewsStarted[pageIndex].await()
            val token = request.headers["Authorization"]
            val id = when (token) {
                "Token alice" -> 1; "Token bob" -> 2; else -> error("Unexpected authentication")
            }
            val next = "?page=2".takeIf { request.url.parameters["page"] == null }
            respondJson(pageJson(next, listOf("""{"id":$id,"title":"t","body":"b"}"""), 2))
        }
        val results = withTimeout(5.seconds) {
            listOf("alice", "bob").map { token -> async { root.withToken(token).news(COURSE_ID).map { it.id } } }
                .awaitAll()
        }
        assertEquals(listOf(listOf(1L, 1L), listOf(2L, 2L)), results)
    }

    @Test
    fun `provider is captured once for a listing and refreshed on the next operation`(): Unit = runBlocking {
        var calls = 0
        val client = mockClient(tokenProvider = { calls++; if (calls == 1) "first" else "second" }) { request ->
            val token = request.headers["Authorization"]
            val id = if (token == "Token first") 1 else 2
            val next = "?page=2".takeIf { request.url.parameters["page"] == null }
            respondJson(pageJson(next, listOf("""{"id":$id,"title":"t","body":"b"}"""), 2))
        }
        assertEquals(listOf(1L, 1L), client.news(COURSE_ID).map { it.id })
        assertEquals(listOf(2L, 2L), client.news(COURSE_ID).map { it.id })
        assertEquals(2, calls)
    }

    @Test
    fun `closing an anonymous view leaves the owner and other views usable`(): Unit = runBlocking {
        val seen = mutableListOf<String?>()
        val root = mockClient { request ->
            seen += request.headers["Authorization"]
            respondJson("""{"id":1,"username":"test","is_external":false}""")
        }
        val anonymous = root.withToken(null)
        anonymous.me()
        anonymous.close()
        root.me()
        root.withToken(" ").me()
        assertEquals(listOf(null, "Token $TOKEN", null), seen)
    }
}
