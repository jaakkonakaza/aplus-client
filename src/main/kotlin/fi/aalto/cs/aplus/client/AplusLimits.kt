package fi.aalto.cs.aplus.client

/**
 * Budgets for one [AplusClient]. Byte limits apply to decoded response bodies, including chunked responses.
 *
 * Listings raise [AplusException.Pagination] when they exceed [maxPaginationBytes], [maxResults]
 * or [maxPages]. A `count` above [maxResults] fails before a second page is fetched.
 *
 * @property maxConcurrentPages Maximum simultaneous numbered-page requests per listing.
 * @property maxPaginationBytes total response-body byte limit for one listing. Concurrent page
 *   reads can exceed this total before rejection. Each response retains its own byte limit.
 * @property maxResults the result limit for one listing. Listing methods take a lower per-call cap.
 * @property maxPages the page limit for listings, including the first page. A+ serves
 *   `REST_FRAMEWORK['PAGE_SIZE']` rows per page. Its default of 100 allows 10,000 rows.
 */
public data class AplusLimits(
    public val maxResponseBytes: Int = 25 * 1024 * 1024,
    public val maxErrorBytes: Int = 4_096,
    public val maxPaginationBytes: Long = 100L * 1024 * 1024,
    public val maxResults: Int = 100_000,
    public val maxPages: Int = 100,
    public val maxConcurrentPages: Int = 4,
) {
    init {
        require(maxResponseBytes > 0) { "maxResponseBytes must be positive" }
        require(maxErrorBytes > 0) { "maxErrorBytes must be positive" }
        require(maxPaginationBytes > 0) { "maxPaginationBytes must be positive" }
        require(maxResults > 0) { "maxResults must be positive" }
        require(maxPages > 0) { "maxPages must be positive, was $maxPages" }
        require(maxConcurrentPages > 0) { "maxConcurrentPages must be positive" }
    }

    /** A copy with a different [maxResponseBytes]. The `with` methods serve callers without named arguments. */
    public fun withMaxResponseBytes(value: Int): AplusLimits = copy(maxResponseBytes = value)

    /** A copy with a different [maxErrorBytes]. */
    public fun withMaxErrorBytes(value: Int): AplusLimits = copy(maxErrorBytes = value)

    /** A copy with a different [maxPaginationBytes]. */
    public fun withMaxPaginationBytes(value: Long): AplusLimits = copy(maxPaginationBytes = value)

    /** A copy with a different [maxResults]. */
    public fun withMaxResults(value: Int): AplusLimits = copy(maxResults = value)

    /** A copy with a different [maxPages]. */
    public fun withMaxPages(value: Int): AplusLimits = copy(maxPages = value)

    /** A copy with a different limit for simultaneous numbered-page requests. */
    public fun withMaxConcurrentPages(value: Int): AplusLimits = copy(maxConcurrentPages = value)
}
