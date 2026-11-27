package fi.aalto.cs.aplus.client

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Reads fields that A+ serializes using Python truthiness.
 *
 * `has_submittable_files` comes from a Python `and` chain over `exercise_info` in
 * `exercise/cache/basetypes.py`. Python returns the last operand, so A+ can send `false`,
 * `null` for a missing `form_spec`, `[]` for an empty one, or `true`.
 * Nonempty arrays decode as `true`. Other non-boolean values decode as `false`.
 */
internal object TruthyBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("fi.aalto.cs.aplus.client.TruthyBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> element.booleanOrNull ?: false
            is JsonArray -> element.isNotEmpty()
            else -> false
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean): Unit = encoder.encodeBoolean(value)
}
