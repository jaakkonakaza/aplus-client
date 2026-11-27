package fi.aalto.cs.aplus.client

import io.ktor.http.*

/**
 * Base class for A+ response and pagination failures.
 *
 * Catch this type to handle HTTP errors, malformed bodies, and invalid pagination together.
 * The following failures are raised separately.
 *
 * - [IllegalArgumentException] for caller errors such as a blank URL or non-positive byte limit.
 * - [AplusUrlException] when a URL to read, post, redirect to, or follow as a `next` link
 *   leaves the configured A+ origin or API root. Keeping this separate prevents a server-error
 *   retry from hiding an attempt to disclose the token.
 * - Engine transport exceptions, typically [java.io.IOException] on OkHttp.
 *
 * @property status the response status. [Pagination] uses [HttpStatusCode.OK] because no response
 *   failed. [Malformed] retains the successful status of the body that failed to decode.
 * @property url the request URL.
 * @property body the response body, truncated to [AplusLimits.maxErrorBytes] for diagnostics.
 *   Empty for [Pagination] and [LimitExceeded].
 */
public sealed class AplusException(
    public val status: HttpStatusCode,
    public val url: String,
    public val body: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /** HTTP 401. The token was missing, expired or rejected. */
    public class Unauthorized(status: HttpStatusCode, url: String, body: String) :
        AplusException(status, url, body, "A+ rejected the API token for $url")

    /** HTTP 403. The token is valid but the user may not access this resource. */
    public class Forbidden(status: HttpStatusCode, url: String, body: String) :
        AplusException(status, url, body, "Access to $url is forbidden")

    /** HTTP 404. The resource does not exist or is hidden from this user. */
    public class NotFound(status: HttpStatusCode, url: String, body: String) :
        AplusException(status, url, body, "$url was not found")

    /** Any other non-2xx response. */
    public class Http(status: HttpStatusCode, url: String, body: String) :
        AplusException(status, url, body, "A+ answered $status for $url")

    /**
     * A successful response whose body could not be decoded.
     *
     * An SSO proxy can return a login page with `200`, and server changes can alter field types.
     * Wrapping the kotlinx SerializationException in [AplusException] lets callers handle these
     * as server failures, with the response status, URL, and body kept for diagnostics.
     * Only response decoding creates this exception.
     *
     * @param cause the `kotlinx.serialization.SerializationException` raised during decoding.
     */
    public class Malformed(status: HttpStatusCode, url: String, body: String, cause: Throwable) :
        AplusException(
            status,
            url,
            body,
            "A+ answered $status for $url with a body that could not be decoded",
            cause,
        )

    /**
     * A listing whose pages do not add up to its `count`, cycle, or exceed the page, result or
     * aggregate byte budget, or a URL that cannot be parsed.
     *
     * All fetched responses succeeded, so [status] is [HttpStatusCode.OK] and [body] is empty.
     * [AplusException.url] is the page where pagination stopped, or the unparseable URL.
     * Malformed URL parser input can come from `next` links, redirects or caller-supplied URLs.
     * Origin and path policy violations raise [AplusUrlException].
     *
     * @param cause the URL parsing exception, or `null` for listing failures.
     */
    public class Pagination(url: String, message: String, cause: Throwable? = null) :
        AplusException(HttpStatusCode.OK, url, "", message, cause)

    /** A response body exceeded the configured byte limit before decoding. */
    public class LimitExceeded(status: HttpStatusCode, url: String, message: String) :
        AplusException(status, url, "", message)

    public companion object {
        /** Builds the exception matching [status]. The caller bounds [body]. */
        public fun of(status: HttpStatusCode, url: String, body: String): AplusException =
            when (status.value) {
                HttpStatusCode.Unauthorized.value -> Unauthorized(status, url, body)
                HttpStatusCode.Forbidden.value -> Forbidden(status, url, body)
                HttpStatusCode.NotFound.value -> NotFound(status, url, body)
                else -> Http(status, url, body)
            }
    }
}
