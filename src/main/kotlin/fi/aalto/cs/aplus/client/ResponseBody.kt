package fi.aalto.cs.aplus.client

import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

internal data class ResponseBody(val text: String, val bytes: Int)

internal suspend fun HttpResponse.readBoundedBody(limit: Int, truncate: Boolean = false): ResponseBody {
    val bytes = readBoundedBytes(limit, truncate)
    return ResponseBody(bytes.decodeToString(), bytes.size)
}

/**
 * Reads at most [limit] bytes, or raises [AplusException.LimitExceeded] for a longer body unless
 * [truncate] is set.
 */
internal suspend fun HttpResponse.readBoundedBytes(limit: Int, truncate: Boolean = false): ByteArray {
    require(limit > 0) { "Response byte limit must be positive" }
    val channel = bodyAsChannel()
    try {
        // One byte beyond the limit tells an exact fit from an overflow without reading to EOF.
        val buffer = channel.readBufferOrThrow(limit + 1L)
        if (buffer.size > limit && !truncate) {
            throw AplusException.LimitExceeded(status, call.request.url.toString(), "A+ response exceeds $limit bytes")
        }
        return buffer.readByteArray(minOf(buffer.size, limit.toLong()).toInt())
    } finally {
        channel.cancel()
    }
}

/**
 * [readBuffer] of at most [max] bytes that rethrows the failure a channel was closed with.
 * [readBuffer] stops at such a failure and returns the bytes read so far as if the body had ended.
 */
internal suspend fun ByteReadChannel.readBufferOrThrow(max: Long): Buffer =
    readBuffer(max).also { closedCause?.let { throw it } }

/** The first [maxBytes] UTF-8 bytes of this string, the way a truncated body read decodes them. */
internal fun String.truncateUtf8(maxBytes: Int): String {
    val bytes = encodeToByteArray()
    return if (bytes.size <= maxBytes) this else bytes.decodeToString(0, maxBytes)
}
