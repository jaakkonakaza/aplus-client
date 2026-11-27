package fi.aalto.cs.aplus.client

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class ClientBoundariesTest {
    @Test
    fun `a body limit aborts an open response without waiting for EOF`(): Unit = runBlocking {
        val body = ByteChannel(autoFlush = true)
        body.writeFully(ByteArray(128) { 'x'.code.toByte() })
        val client = mockClient(limits = AplusLimits(maxResponseBytes = 16)) { respond(body) }
        try {
            withTimeout(2.seconds) {
                assertFailsWith<AplusException.LimitExceeded> { client.me() }
            }
        } finally {
            body.cancel()
        }
    }

    @Test
    fun `a body stream that fails part way is raised rather than returned truncated`(): Unit = runBlocking {
        // When a failure lands between reads, Ktor's read returns with the channel closed.
        suspend fun failedAfterSomeBytes() = ByteChannel(autoFlush = true).apply {
            writeFully("partial".encodeToByteArray())
            close(IOException("connection reset"))
        }

        val failure = assertFailsWith<IOException> { failedAfterSomeBytes().readBufferOrThrow(64L) }
        assertEquals("connection reset", failure.message)
    }

    @Test
    fun `a chunked successful body is bounded by bytes before decoding`(): Unit = runBlocking {
        val body = """{"id":1,"username":"ä","is_external":false}"""
        val bytes = body.encodeToByteArray().size
        val limited = mockClient(limits = AplusLimits(maxResponseBytes = bytes - 1)) { respondJson(body) }
        assertFailsWith<AplusException.LimitExceeded> { limited.me() }
        val exact = mockClient(limits = AplusLimits(maxResponseBytes = bytes)) { respondJson(body) }
        assertEquals("ä", exact.me().username)
    }

    @Test
    fun `a long error body retains its HTTP error and is truncated`(): Unit = runBlocking {
        val client = mockClient(limits = AplusLimits(maxErrorBytes = 8)) {
            respondJson("x".repeat(100), HttpStatusCode.Forbidden)
        }
        assertEquals("x".repeat(8), assertFailsWith<AplusException.Forbidden> { client.me() }.body)
    }
}
