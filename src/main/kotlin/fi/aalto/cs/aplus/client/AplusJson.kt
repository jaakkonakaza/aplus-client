package fi.aalto.cs.aplus.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * JSON configurations for decoding A+ responses.
 *
 * Most endpoints use `snake_case`. The legacy `/courses/{id}/submissiondata` export uses
 * `PascalCase` and needs a separate configuration.
 */
public object AplusJson {
    /**
     * Decoder for the regular `snake_case` A+ endpoints.
     *
     * Unknown keys are ignored for compatibility with newer A+ releases.
     * [JsonNamingStrategy.SnakeCase] maps Kotlin property names to server field names.
     */
    public val default: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    /**
     * Decoder that uses serial names verbatim, without any naming strategy.
     *
     * `/courses/{id}/submissiondata` uses explicit `@SerialName` annotations for PascalCase columns.
     * A naming strategy would alter those annotated names.
     */
    public val verbatim: Json = Json(default) { namingStrategy = null }
}
