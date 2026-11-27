package fi.aalto.cs.aplus.client

import com.sun.net.httpserver.HttpServer
import fi.aalto.cs.aplus.client.dto.GradeSubmissionRequest
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class GradeWriteRetryTest {
    @Test
    fun `OkHttp does not replay a grade when a 503 requests an immediate retry`(): Unit = runBlocking {
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.use {
                exchange.requestBody.readBytes()
                requests.incrementAndGet()
                exchange.responseHeaders.add("Retry-After", "0")
                exchange.sendResponseHeaders(503, -1)
            }
        }
        server.start()
        try {
            AplusClient.create("http://127.0.0.1:${server.address.port}", { TOKEN }).use { client ->
                withTimeout(5.seconds) {
                    val failure = assertFailsWith<AplusException.Http> {
                        client.createGradedSubmission(24, GradeSubmissionRequest(listOf(517), 3))
                    }
                    assertEquals(HttpStatusCode.ServiceUnavailable, failure.status)
                }
            }
            assertEquals(1, requests.get(), "A grade creation must never be replayed automatically")
        } finally {
            server.stop(0)
        }
    }
}
