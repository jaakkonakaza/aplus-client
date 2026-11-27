package fi.aalto.cs.aplus.client

import fi.aalto.cs.aplus.client.dto.StudentField
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

class PaginationTest {
    @Test
    fun `numbered page downloads obey the configured concurrency limit`(): Unit = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val client = paged(
            requests = Collections.synchronizedList(mutableListOf()),
            limits = AplusLimits(maxConcurrentPages = 2),
        ) { page, _ ->
            if (page > 1) {
                val count = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, count) }
                try {
                    delay(10.milliseconds)
                } finally {
                    active.decrementAndGet()
                }
            }
            respondJson(pageJson(if (page < 9) "$students?page=${page + 1}" else null, students(page.toLong()), 9))
        }
        assertEquals((1L..9L).toList(), withTimeout(5.seconds) { client.ids() })
        assertEquals(2, peak.get())
        assertFailsWith<IllegalArgumentException> { AplusLimits(maxConcurrentPages = 0) }
    }

    private val students = "$API_ROOT/courses/$COURSE_ID/students"

    private fun student(id: Long) = """{"id":$id,"username":"student$id","is_external":false}"""

    private fun students(vararg ids: Long) = ids.map(::student)

    private fun paged(
        requests: MutableList<HttpRequestData> = mutableListOf(),
        limits: AplusLimits = AplusLimits(),
        handler: suspend MockRequestHandleScope.(page: Int, request: HttpRequestData) -> HttpResponseData,
    ) = mockClient(requests, limits = limits) { request ->
        handler(request.url.parameters["page"]?.toIntOrNull() ?: 1, request)
    }

    private suspend fun AplusClient.ids() = students(COURSE_ID).map { it.id }

    private suspend fun assertPaginationFailure(message: String, block: suspend () -> Unit) {
        assertEquals(message, assertFailsWith<AplusException.Pagination> { block() }.message)
    }

    @Test
    fun `numbered pages after the first are derived from the count and fetched together`(): Unit = runBlocking {
        val requests = Collections.synchronizedList(mutableListOf<HttpRequestData>())
        val arrived = AtomicInteger()
        val allPagesStarted = CompletableDeferred<Unit>()
        val client = paged(requests) { page, _ ->
            if (page > 1) {
                if (arrived.incrementAndGet() == 4) allPagesStarted.complete(Unit)
                allPagesStarted.await()
            }
            respondJson(pageJson(if (page < 5) "$students?page=${page + 1}" else null, students(page.toLong()), 5))
        }

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), withTimeout(5.seconds) { client.ids() })
        assertEquals(setOf(null, "2", "3", "4", "5"), requests.map { it.url.parameters["page"] }.toSet())
        requests.forEach(::assertAuthorized)
    }

    @Test
    fun `query parameters ride the first request and every numbered page`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = paged(requests) { page, _ ->
            val next = "$students?field=id&page=${page + 1}&values=1%2C2%2C3"
            respondJson(pageJson(next.takeIf { page < 3 }, students(page.toLong()), 3))
        }

        assertEquals(3, client.students(COURSE_ID, StudentField.ID, listOf("1", "2", "3")).size)
        requests.forEach {
            assertEquals("id", it.url.parameters["field"])
            assertEquals("1,2,3", it.url.parameters["values"])
        }
    }

    @Test
    fun `a next link that is not page 2 is followed one page at a time`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests) { request ->
            when (request.url.parameters["cursor"]) {
                null -> respondJson(pageJson("$students?cursor=second&ordering=-id", students(1), 3))
                "second" -> respondJson(pageJson("?cursor=third", students(2), 3))
                else -> respondJson(pageJson(null, students(3), 3))
            }
        }

        assertEquals(listOf(1L, 2L, 3L), client.ids())
        assertEquals("-id", requests[1].url.parameters["ordering"])
        assertEquals("third", requests[2].url.parameters["cursor"])
    }

    @Test
    fun `a numbered next link other than page 2 stays sequential without inventing pages`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = paged(requests) { page, _ ->
            respondJson(pageJson(if (page < 4) "?page=${if (page == 1) 3 else 4}" else null, students(page.toLong()), 3))
        }

        assertEquals(listOf(1L, 3L, 4L), client.ids())
        assertEquals(listOf(null, "3", "4"), requests.map { it.url.parameters["page"] })
    }

    @Test
    fun `a body that is not a page is malformed rather than an empty listing`(): Unit = runBlocking {
        for (body in listOf("{", "{}", """{"results":[]}""", """{"count":0}""")) {
            assertFailsWith<AplusException.Malformed>(body) { mockClient { respondJson(body) }.ids() }
        }
        assertEquals(emptyList(), mockClient { respondJson(pageJson(null, emptyList())) }.ids())
    }

    @Test
    fun `a first page that misstates its count or navigation is refused`(): Unit = runBlocking {
        for (body in listOf(
            pageJson(null, students(1), count = 2),
            pageJson("?page=2", students(1, 2), count = 2),
            pageJson("?page=2", emptyList(), count = 2),
            pageJson(null, students(1, 2), count = 1),
            pageJson(null, emptyList(), count = -1),
            """{"count":1,"next":null,"previous":"$students","results":[${student(1)}]}""",
        )) {
            val requests = mutableListOf<HttpRequestData>()
            assertPaginationFailure("A+ pagination returned inconsistent page metadata") {
                mockClient(requests) { respondJson(body) }.ids()
            }
            assertEquals(1, requests.size, body)
        }
    }

    @Test
    fun `a continuation page with another count or no results is refused`(): Unit = runBlocking {
        for (second in listOf(pageJson(null, students(2), count = 3), pageJson(null, emptyList(), count = 2))) {
            val client = mockClient { request ->
                respondJson(if (request.url.parameters["cursor"] == null) pageJson("?cursor=2", students(1), 2) else second)
            }
            assertPaginationFailure("A+ pagination returned inconsistent page metadata") { client.ids() }
        }
    }

    @Test
    fun `a sequential listing that ends early or runs past its count is incomplete`(): Unit = runBlocking {
        for ((count, second) in listOf(3 to students(2), 2 to students(2, 3))) {
            val requests = mutableListOf<HttpRequestData>()
            val client = mockClient(requests) { request ->
                val first = request.url.parameters["cursor"] == null
                respondJson(if (first) pageJson("?cursor=second", students(1), count) else pageJson(null, second, count))
            }
            assertPaginationFailure("A+ pagination returned an incomplete result set") { client.ids() }
            assertEquals(2, requests.size)
        }
    }

    @Test
    fun `a numbered page of the wrong size or with the wrong next link is refused`(): Unit = runBlocking {
        val wrong = listOf<(Int) -> String>(
            { page -> pageJson(if (page < 3) "?page=${page + 1}" else null, if (page == 2) students(3) else students(4, 5), 5) },
            { page -> pageJson(if (page == 2) "?page=4" else null, students(3, 4), 5) },
            { page -> pageJson("?page=${page + 1}", students(5), 5) },
        )
        for (answer in wrong) {
            val client = paged { page, _ ->
                respondJson(if (page == 1) pageJson("?page=2", students(1, 2), 5) else answer(page))
            }
            assertPaginationFailure("A+ pagination returned inconsistent numbered page metadata") { client.ids() }
        }
    }

    @Test
    fun `a numbered page that disappeared fails the listing with its status`(): Unit = runBlocking {
        for (missing in listOf(3, 5)) {
            val client = paged { page, _ ->
                if (page == missing) respondJson("not found", HttpStatusCode.NotFound)
                else respondJson(pageJson(if (page < 5) "?page=${page + 1}" else null, students(page.toLong()), 5))
            }
            assertFailsWith<AplusException.NotFound> { client.ids() }
        }
    }

    @Test
    fun `a count above the result cap fails before a second page is fetched`(): Unit = runBlocking {
        for (next in listOf("?page=2", "?cursor=next")) {
            val requests = mutableListOf<HttpRequestData>()
            val client = mockClient(requests, limits = AplusLimits(maxResults = 3)) {
                respondJson(pageJson(next, students(1), 5))
            }
            assertPaginationFailure("A+ pagination returned too many results") { client.ids() }
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun `the per-call result cap applies below the client's`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val tag = """{"slug":"tag","name":"Tag"}"""
        val client = mockClient(requests) { respondJson(pageJson("?page=2", listOf(tag), 257)) }

        assertPaginationFailure("A+ pagination returned too many results") { client.usertags(COURSE_ID, maxResults = 256) }
        assertEquals(1, requests.size)
        assertFailsWith<IllegalArgumentException> { client.usertags(COURSE_ID, maxResults = 0) }
    }

    @Test
    fun `the page limit is checked against the count before numbered pages are fetched`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests, limits = AplusLimits(maxPages = 3)) {
            respondJson(pageJson("$students?page=2", students(1), 500))
        }

        assertPaginationFailure("A+ pagination returned too many pages") { client.ids() }
        assertEquals(1, requests.size)
    }

    @Test
    fun `the page limit stops a sequential listing including its first page`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests, limits = AplusLimits(maxPages = 2)) { request ->
            val cursor = request.url.parameters["cursor"]?.toLong() ?: 0
            respondJson(pageJson("$students?cursor=${cursor + 1}", students(cursor), 1_000))
        }

        assertPaginationFailure("A+ pagination returned too many pages") { client.ids() }
        assertEquals(2, requests.size)
        assertEquals(listOf(1L, 2L), paged(limits = AplusLimits(maxPages = 2)) { page, _ ->
            respondJson(pageJson("?page=2".takeIf { page == 1 }, students(page.toLong()), 2))
        }.ids())
    }

    @Test
    fun `a next link already followed is a cycle, including one back to the first page`(): Unit = runBlocking {
        for ((next, fetched) in listOf("$students?cursor=repeat" to 2, students to 1)) {
            val requests = mutableListOf<HttpRequestData>()
            val client = mockClient(requests) { respondJson(pageJson(next, students(1), 1_000)) }
            assertPaginationFailure("A+ pagination returned a cycle") { client.ids() }
            assertEquals(fetched, requests.size)
        }
    }

    @Test
    fun `the aggregate byte budget includes the first page`(): Unit = runBlocking {
        val first = pageJson("?page=2", students(1), 2)
        val second = pageJson(null, students(2), 2)
        for (budget in listOf(first.length.toLong(), first.length + second.length - 1L)) {
            val requests = mutableListOf<HttpRequestData>()
            val limits = AplusLimits(maxResponseBytes = maxOf(first.length, second.length), maxPaginationBytes = budget)
            val client = paged(requests, limits) { page, _ -> respondJson(if (page == 1) first else second) }
            assertPaginationFailure("A+ pagination response is too large") { client.ids() }
            assertEquals(if (budget == first.length.toLong()) 1 else 2, requests.size)
        }
    }

    @Test
    fun `a page over the response limit is a limit failure, not a pagination one`(): Unit = runBlocking {
        val client = mockClient(limits = AplusLimits(maxResponseBytes = 16)) { respondJson(pageJson(null, students(1))) }
        assertFailsWith<AplusException.LimitExceeded> { client.ids() }
    }

    @Test
    fun `the first page alone may report more results than the cap but must hold no more`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests) { respondJson(pageJson("?page=2", listOf("""{"id":9}""", """{"id":8}"""), 500)) }

        assertEquals(listOf(9L, 8L), client.latestExerciseSubmissions(24, "517", maxResults = 2).map { it.id })
        assertEquals("/api/v2/exercises/24/submissions/517", requests.single().url.encodedPath)
        assertPaginationFailure("A+ pagination returned too many results") {
            client.latestExerciseSubmissions(24, "517", maxResults = 1)
        }
        val offOrigin = mockClient { respondJson(pageJson("https://evil.test/api/v2/x", listOf("""{"id":9}"""), 2)) }
        assertFailsWith<IllegalArgumentException> { offOrigin.latestExerciseSubmissions(24) }
    }

    @Test
    fun `the link resolver maps an alias origin before the same-origin check`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = mockClient(requests, linkResolver = { it.replace("https://public.test", BASE_URL) }) { request ->
            val first = request.url.parameters["page"] == null
            respondJson(pageJson("https://public.test/api/v2/courses/3/students?page=2".takeIf { first }, students(1), 2))
        }

        assertEquals(listOf(1L, 1L), client.ids())
        assertEquals(listOf("localhost", "localhost"), requests.map { it.url.host })
        assertFailsWith<IllegalArgumentException> { client.withToken(TOKEN).readText("https://elsewhere.test/x") }
    }

    @Test
    fun `next links resolve against the page that carried them`(): Unit = runBlocking {
        for (next in listOf("?cursor=2", "/api/v2/courses/3/students?cursor=2", "students?cursor=2", "$API_ROOT/courses/3/students/?cursor=2")) {
            val requests = mutableListOf<HttpRequestData>()
            val client = mockClient(requests) { request ->
                val first = request.url.parameters["cursor"] == null
                respondJson(pageJson(next.takeIf { first }, students(if (first) 1 else 2), 2))
            }
            assertEquals(listOf(1L, 2L), client.ids(), next)
            assertTrue(requests.last().url.encodedPath.startsWith("/api/v2/courses/3/students"), next)
        }
    }

    @Test
    fun `a next link at the api root or with an explicit default port is allowed`(): Unit = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = paged(requests) { page, _ -> respondJson(pageJson("$API_ROOT?page=2".takeIf { page == 1 }, students(page.toLong()), 2)) }
        assertEquals(listOf(1L, 2L), client.ids())
        assertEquals("/api/v2", requests.last().url.encodedPath)

        val https = AplusClient(HttpClient(MockEngine { request ->
            val page = request.url.parameters["page"]?.toIntOrNull() ?: 1
            respondJson(pageJson("https://aplus.test:443/api/v2/courses/3/students?page=2".takeIf { page == 1 }, students(page.toLong()), 2))
        }) { aplusDefaults() }, "https://aplus.test", { TOKEN })
        assertEquals(listOf(1L, 2L), https.ids())
    }

    @Test
    fun `next links leaving the api root are refused before any token is sent there`(): Unit = runBlocking {
        for (next in listOf(
            "https://evil.test/api/v2/courses/3/students?page=2", "//evil.test/api/v2/news",
            "$BASE_URL/admin/news", "$BASE_URL/api/v20/courses/3/news", "$BASE_URL/api/v2-other/news",
            "http://user@localhost:8000/api/v2/news", "$API_ROOT/news#fragment",
            "$API_ROOT/%2e%2e/admin", "$API_ROOT/../admin",
        )) {
            val requests = mutableListOf<HttpRequestData>()
            val client = mockClient(requests) { respondJson(pageJson(next, students(1), 2)) }
            val error = assertFailsWith<AplusUrlException>(next) { client.ids() }
            assertEquals(next, error.url)
            assertEquals(1, requests.size, next)
        }
    }

    @Test
    fun `a next link that is not a url is a pagination failure carrying the parser error`(): Unit = runBlocking {
        // Ktor's URLParserException extends IllegalStateException and URLDecodeException extends
        // Exception, so both would bypass catches for AplusException without wrapping.
        for ((next, cause) in listOf(
            "http://localhost:port/api/v2/courses/3/news" to URLParserException::class,
            "$API_ROOT/courses/3/news?page=%%" to URLDecodeException::class,
        )) {
            val failure = assertFailsWith<AplusException.Pagination> {
                mockClient { respondJson(pageJson(next, students(1), 2)) }.ids()
            }
            assertTrue(failure.message.orEmpty().contains("not a URL"), failure.message.orEmpty())
            assertEquals(next, failure.url)
            assertEquals(HttpStatusCode.OK, failure.status)
            assertEquals("", failure.body)
            assertTrue(cause.isInstance(failure.cause), "$next: ${failure.cause}")
        }
    }

    @Test
    fun `limits must be positive`() {
        assertFailsWith<IllegalArgumentException> { AplusLimits(maxPages = 0) }
        assertFailsWith<IllegalArgumentException> { AplusLimits(maxResults = 0) }
        assertFailsWith<IllegalArgumentException> { AplusLimits(maxPaginationBytes = 0) }
    }
}
