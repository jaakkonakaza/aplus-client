package fi.aalto.cs.aplus.client

import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicLong

/**
 * The DRF `PageNumberPagination` envelope. [count] and [results] have no defaults, so a body
 * missing either field fails to decode.
 */
@Serializable
internal class Page<T>(
    val count: Int,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T>,
)

/**
 * When the first `next` link asks for `page=2`, the remaining numbered pages are derived from
 * `count` and the first page's size and fetched concurrently. [AplusLimits.maxConcurrentPages] bounds
 * simultaneous page requests. Any other `next` link is followed one page at a time.
 *
 * @param maxResults the per-call result cap, itself capped by [AplusLimits.maxResults].
 * @param resolve resolves a `next` link against the URL of the page that carried it and refuses
 *   one that leaves the API root.
 */
internal class Paginator<T>(
    private val limits: AplusLimits,
    maxResults: Int,
    private val resolve: (link: String, currentUrl: String) -> String,
    private val fetch: suspend (url: String, maxBytes: Int) -> AplusResponse<Page<T>>,
) {
    private val resultLimit: Int

    init {
        require(maxResults > 0) { "maxResults must be positive, was $maxResults" }
        resultLimit = minOf(maxResults, limits.maxResults)
    }

    /** A `count` above the result cap fails before a second page is fetched. */
    suspend fun all(url: String): List<T> {
        val first = fetchWithin(url, 0)
        validateFirst(first, allowLargerCount = false)
        val next = resolve(first.value.next ?: return first.value.results, first.requestUrl)
        return if (pageNumber(next) == 2) numbered(first, next) else sequential(first, next)
    }

    /** The first page's `count` may exceed the cap. Its results must fit within it. */
    suspend fun firstPage(url: String): List<T> {
        val first = fetchWithin(url, 0)
        validateFirst(first, allowLargerCount = true)
        first.value.next?.let { resolve(it, first.requestUrl) }
        return first.value.results
    }

    private suspend fun sequential(first: AplusResponse<Page<T>>, second: String): List<T> {
        val count = first.value.count
        val results = ArrayList<T>(count).apply { addAll(first.value.results) }
        val seen = hashSetOf(first.requestUrl)
        var bytes = first.bodyBytes.toLong()
        var url = second
        var pages = 1
        while (true) {
            if (!seen.add(url)) fail(url, "A+ pagination returned a cycle")
            if (pages >= limits.maxPages) fail(url, TOO_MANY_PAGES)
            val page = fetchWithin(url, bytes)
            bytes += page.bodyBytes
            pages++
            validateContinuation(page, count)
            if (page.value.results.size > resultLimit - results.size) fail(url, TOO_MANY_RESULTS)
            results.addAll(page.value.results)
            if (results.size > count || (results.size < count) != (page.value.next != null)) {
                fail(url, "A+ pagination returned an incomplete result set")
            }
            url = resolve(page.value.next ?: return results, page.requestUrl)
        }
    }

    private suspend fun numbered(first: AplusResponse<Page<T>>, second: String): List<T> = coroutineScope {
        val count = first.value.count
        val size = first.value.results.size
        val total = ((count.toLong() + size - 1) / size).toInt()
        if (total > limits.maxPages) fail(second, TOO_MANY_PAGES)
        val bytes = AtomicLong(first.bodyBytes.toLong())
        val permits = Semaphore(limits.maxConcurrentPages)
        val rest = (2..total).map { number ->
            async {
                permits.withPermit {
                    val url = URLBuilder(second).apply { parameters["page"] = number.toString() }.buildString()
                    val page = fetchWithin(url, bytes.get())
                    if (bytes.addAndGet(page.bodyBytes.toLong()) > limits.maxPaginationBytes) fail(url, TOO_LARGE)
                    validateContinuation(page, count)
                    val expectedSize = if (number < total) size else count - size * (total - 1)
                    val expectedNext = if (number < total) number + 1 else null
                    val next = page.value.next?.let { pageNumber(resolve(it, page.requestUrl)) ?: -1 }
                    if (page.value.results.size != expectedSize || next != expectedNext) {
                        fail(url, "A+ pagination returned inconsistent numbered page metadata")
                    }
                    page.value.results
                }
            }
        }
        first.value.results + rest.awaitAll().flatten()
    }

    private suspend fun fetchWithin(url: String, consumed: Long): AplusResponse<Page<T>> {
        val remaining = limits.maxPaginationBytes - consumed
        if (remaining <= 0) fail(url, TOO_LARGE)
        val maxBytes = minOf(limits.maxResponseBytes.toLong(), remaining).toInt()
        return try {
            fetch(url, maxBytes)
        } catch (exceeded: AplusException.LimitExceeded) {
            if (maxBytes < limits.maxResponseBytes) fail(url, TOO_LARGE) else throw exceeded
        }
    }

    private fun validateFirst(first: AplusResponse<Page<T>>, allowLargerCount: Boolean) {
        val page = first.value
        val size = page.results.size
        if (page.count < 0 || size > page.count || page.previous != null ||
            (size < page.count) != (page.next != null) || (size == 0 && page.count > 0)
        ) fail(first.requestUrl, INCONSISTENT)
        if (size > resultLimit || (!allowLargerCount && page.count > resultLimit)) {
            fail(first.requestUrl, TOO_MANY_RESULTS)
        }
    }

    private fun validateContinuation(page: AplusResponse<Page<T>>, count: Int) {
        if (page.value.count != count || page.value.results.isEmpty() || page.value.results.size > count) {
            fail(page.requestUrl, INCONSISTENT)
        }
    }

    private fun pageNumber(url: String): Int? = Url(url).parameters["page"]?.toIntOrNull()

    private fun fail(url: String, message: String): Nothing = throw AplusException.Pagination(url, message)

    private companion object {
        const val INCONSISTENT = "A+ pagination returned inconsistent page metadata"
        const val TOO_MANY_RESULTS = "A+ pagination returned too many results"
        const val TOO_MANY_PAGES = "A+ pagination returned too many pages"
        const val TOO_LARGE = "A+ pagination response is too large"
    }
}
