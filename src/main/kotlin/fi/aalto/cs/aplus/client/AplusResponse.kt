package fi.aalto.cs.aplus.client

/** A decoded response with its final request URL and decoded body size, for caller-owned pagination policies. */
public data class AplusResponse<T>(
    public val value: T,
    public val requestUrl: String,
    public val bodyBytes: Int,
)
