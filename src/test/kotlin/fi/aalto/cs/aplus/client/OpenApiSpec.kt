package fi.aalto.cs.aplus.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Fixture provenance and refresh instructions are in `src/test/resources/fixtures/README.md`. */
internal val aplusSpec: JsonObject by lazy {
    Json.parseToJsonElement(fixture("openapi.json")).jsonObject
}

internal val specPaths: Map<String, Map<String, JsonObject>> by lazy {
    aplusSpec.getValue("paths").jsonObject.mapValues { (_, pathItem) ->
        pathItem.jsonObject.mapValues { (_, operation) -> operation.jsonObject }
    }
}

internal fun queryParameters(path: String, method: String): Map<String, JsonObject> =
    specPaths.getValue(path).getValue(method)["parameters"]?.jsonArray.orEmpty()
        .map { it.jsonObject }
        .filter { it["in"]?.jsonPrimitive?.content == "query" }
        .associateBy { it.getValue("name").jsonPrimitive.content }

internal fun componentSchemaOf(name: String): JsonObject =
    aplusSpec.getValue("components").jsonObject.getValue("schemas").jsonObject.getValue(name).jsonObject

/**
 * DRF uses $ref for typed responses. Resolve these before inspecting schema fields.
 */
internal fun resolveSchemaRef(schema: JsonObject): JsonObject {
    val ref = schema[$$"$ref"]?.jsonPrimitive?.content ?: return schema
    return resolveSchemaRef(componentSchemaOf(ref.substringAfterLast('/')))
}

/** Returns unresolved `$ref` values. Pass them through [resolveSchemaRef] before inspecting fields. */
internal fun responseSchema(path: String, method: String): JsonObject? {
    val responses = specPaths.getValue(path).getValue(method).getValue("responses").jsonObject
    val success = responses.keys.first { it.startsWith("2") }
    val content = responses.getValue(success).jsonObject.getValue("content").jsonObject
    val body = content["application/vnd.aplus+json"] ?: content.values.first()
    return body.jsonObject.getValue("schema").jsonObject.takeIf { it.isNotEmpty() }
}
