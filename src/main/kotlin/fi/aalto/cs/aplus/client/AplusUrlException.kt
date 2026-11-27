package fi.aalto.cs.aplus.client

/**
 * A requested URL violates the configured origin or API path restrictions.
 * Also raised for embedded credentials, fragments and unsafe path segments.
 * URL rejection stays separate from [AplusException] to prevent automatic server-error retries.
 * @property url the rejected URL before origin alias rewriting.
 */
public class AplusUrlException internal constructor(public val url: String, message: String) :
    IllegalArgumentException(message)
