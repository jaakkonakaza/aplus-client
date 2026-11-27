package fi.aalto.cs.aplus.client

import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlin.test.*

class DecodeTest {

    /** An SSO proxy can return a login page when it requires a browser session. */
    private val loginPage = "<html><body>login</body></html>"

    @Test
    fun `a 2xx body that does not decode is an AplusException`(): Unit = runBlocking {
        val client = mockClient { respondJson(loginPage) }

        val failure = assertFailsWith<AplusException.Malformed> { client.course(COURSE_ID) }

        assertEquals(HttpStatusCode.OK, failure.status)
        assertTrue(failure.url.endsWith("/courses/$COURSE_ID"), failure.url)
        assertTrue(failure.body.contains("<html>"), failure.body)
        assertIs<SerializationException>(failure.cause)
    }

    @Test
    fun `a body too long to carry whole is truncated the way a failing status truncates it`(): Unit = runBlocking {
        // An HTML error page can run to megabytes, and the exception is only a diagnostic.
        val long = "<html>" + "x".repeat(4_000)
        val client = mockClient(limits = AplusLimits(maxErrorBytes = 100)) { respondJson(long) }

        val failure = assertFailsWith<AplusException.Malformed> { client.course(COURSE_ID) }

        assertEquals(long.take(100), failure.body)
    }

    @Test
    fun `a later page of a listing that does not decode is a Malformed too`(): Unit = runBlocking {
        val client = mockClient { request ->
            when (request.url.parameters["page"]) {
                "2" -> respondJson(loginPage)
                else -> respondJson(
                    """{"count":2,"next":"$API_ROOT/courses/$COURSE_ID/news?page=2","previous":null,
                       "results":[{"id":1,"title":"Item 1","body":"<p>1</p>"}]}"""
                )
            }
        }

        val failure = assertFailsWith<AplusException.Malformed> { client.news(COURSE_ID) }

        assertEquals(HttpStatusCode.OK, failure.status)
        assertTrue(failure.url.endsWith("/courses/$COURSE_ID/news?page=2"), failure.url)
        assertIs<SerializationException>(failure.cause)
    }

    @Test
    fun `a response without the identity fields A+ always sends is malformed`(): Unit = runBlocking {
        for (body in listOf("""{"points":12,"username":"u","is_external":false}""", """{"id":1,"username":"u"}""")) {
            assertFailsWith<AplusException.Malformed>(body) { mockClient { respondJson(body) }.points(COURSE_ID) }
        }
        assertFailsWith<AplusException.Malformed> {
            mockClient { respondJson("""{"display_name":"1.1 Sequence laboratory"}""") }.exercise(24)
        }
    }
}
