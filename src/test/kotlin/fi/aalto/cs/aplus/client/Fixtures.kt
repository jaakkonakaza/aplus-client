package fi.aalto.cs.aplus.client

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlin.test.assertEquals

internal const val BASE_URL: String = "http://localhost:8000"

internal const val API_ROOT: String = BASE_URL + AplusClient.API_PATH

internal const val TOKEN: String = "test-token"
internal const val COURSE_ID: Long = 3L

internal val jsonHeaders: Headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

internal fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.getResourceAsStream("/fixtures/$name")) {
        "Missing fixture /fixtures/$name"
    }.use { it.readBytes().decodeToString() }

internal fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(ByteReadChannel(body), status, jsonHeaders)

/** A DRF page of the JSON objects [results], out of [count] across all pages. */
internal fun pageJson(next: String?, results: List<String>, count: Int = results.size): String =
    """{"count":$count,"next":${next?.let { "\"$it\"" } ?: "null"},"previous":null,"results":[${results.joinToString(",")}]}"""

internal fun MockRequestHandleScope.respondFixture(
    name: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respondJson(fixture(name), status)

internal fun mockClient(
    requests: MutableList<HttpRequestData> = mutableListOf(),
    baseUrl: String = BASE_URL,
    token: String? = TOKEN,
    tokenProvider: suspend () -> String? = { token },
    limits: AplusLimits = AplusLimits(),
    linkResolver: (String) -> String = { it },
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): AplusClient {
    val engine = MockEngine { request ->
        requests += request
        handler(request)
    }
    return AplusClient(
        httpClient = HttpClient(engine) { aplusDefaults() },
        baseUrl = baseUrl,
        tokenProvider = tokenProvider,
        limits = limits,
        linkResolver = linkResolver,
    )
}

internal fun fixtureClient(
    fixtureName: String,
    requests: MutableList<HttpRequestData> = mutableListOf(),
): AplusClient = mockClient(requests) { respondFixture(fixtureName) }

internal fun assertAuthorized(request: HttpRequestData) {
    assertEquals("Token $TOKEN", request.headers["Authorization"])
}
