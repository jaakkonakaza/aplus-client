package fi.aalto.cs.aplus.client

import io.ktor.http.*
import java.net.URI
import java.net.URISyntaxException

/** Normalizes [baseUrl] to `scheme://host[:port][/prefix]/api/v2`, without a trailing slash. */
internal fun normalizeAplusBaseUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    val uri = try {
        URI(trimmed)
    } catch (malformed: URISyntaxException) {
        throw IllegalArgumentException("Invalid A+ base URL", malformed)
    }
    require(uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) { "A+ base URL must use HTTP(S)" }
    require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "A+ base URL must be absolute, without credentials, query or fragment"
    }
    require(uri.port == -1 || uri.port in 1..65535) { "Invalid A+ port" }
    require(uri.path.split('/').none { it == "." || it == ".." } && '%' !in uri.rawPath && '\\' !in uri.rawPath) {
        "A+ base URL path must not contain dot segments or encoded characters"
    }
    val withApi = if (trimmed.endsWith(AplusClient.API_PATH)) trimmed else trimmed + AplusClient.API_PATH
    return URLBuilder(withApi).apply { host = host.lowercase() }.build().toString().trimEnd('/')
}

/**
 * Every authenticated URL must stay on [base]'s origin after [rewrite].
 * [apiPathOnly] also restricts it to the API path.
 *
 * Ktor throws [URLParserException] for malformed authorities and [URLDecodeException] for
 * broken percent escapes. Both become [AplusException.Pagination]. Ktor accepts some invalid
 * hosts with spaces and scheme-only URLs, which the origin check rejects.
 *
 * @throws AplusUrlException when the URL leaves [base] or contains credentials, a fragment or an unsafe path.
 */
internal fun requireSameOrigin(
    candidate: String,
    currentUrl: String,
    base: Url,
    apiPathOnly: Boolean,
    rewrite: (String) -> String = { it },
): String {
    // Ktor preserves parser and decode failures for malformed absolute URLs.
    try {
        if (candidate.contains("://")) Url(candidate)
        val current = URI(currentUrl)
        val resolved = if (candidate.startsWith('?')) {
            URI("${current.scheme}://${current.rawAuthority}${current.rawPath}$candidate")
        } else current.resolve(candidate)
        val normalized = URI(rewrite(resolved.toString())).normalize()
        val url = Url(normalized.toASCIIString())
        val decodedPath = normalized.path.orEmpty()

        val noCredentialsOrFragment = normalized.userInfo == null && normalized.fragment == null
        // Normalizing the decoded path again catches dot segments that were percent-encoded.
        val canonicalPath = !decodedPath.contains('\\') && URI(null, null, decodedPath, null).normalize().path == decodedPath
        val sameOrigin = url.protocol == base.protocol && url.host.equals(base.host, ignoreCase = true) && url.port == base.port
        val withinApiPath = url.encodedPath == base.encodedPath || url.encodedPath.startsWith(base.encodedPath + "/")
        if (!(noCredentialsOrFragment && canonicalPath && sameOrigin && (!apiPathOnly || withinApiPath))) {
            throw AplusUrlException(
                candidate,
                "Refusing URL $candidate, which is outside ${if (apiPathOnly) "$base" else "the origin of $base"}",
            )
        }
        return url.toString()
    } catch (malformed: URLParserException) {
        throw malformedUrl(candidate, malformed)
    } catch (malformed: URLDecodeException) {
        throw malformedUrl(candidate, malformed)
    } catch (malformed: URISyntaxException) {
        throw malformedUrl(candidate, malformed)
    }
}

private fun malformedUrl(candidate: String, cause: Throwable): AplusException.Pagination =
    AplusException.Pagination(candidate, "Refusing $candidate, which is not a URL", cause)
