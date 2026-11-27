package fi.aalto.cs.aplus.client

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A+ derives has_submittable_files from Python and expressions in
 * `exercise/cache/basetypes.py`. The final operand can be a boolean, null, or an array.
 *
 * JSON null and a missing field both decode as false.
 */
class TruthyBooleanTest {

    @Serializable
    private data class Holder(
        @Serializable(with = TruthyBooleanSerializer::class)
        val value: Boolean = false,
    )

    private fun decode(json: String): Boolean = AplusJson.default.decodeFromString(Holder.serializer(), json).value

    @Test
    fun `a real boolean is used as-is`() {
        assertTrue(decode("""{"value": true}"""))
        assertFalse(decode("""{"value": false}"""))
    }

    @Test
    fun `null reads as false`() {
        assertFalse(decode("""{"value": null}"""))
    }

    @Test
    fun `an empty array reads as false and a non-empty one as true`() {
        assertFalse(decode("""{"value": []}"""))
        assertTrue(decode("""{"value": ["file1"]}"""))
    }

    @Test
    fun `a missing field falls back to the default`() {
        assertFalse(decode("{}"))
    }

    @Test
    fun `it round-trips back to a plain boolean`() {
        assertEquals("""{"value":true}""", AplusJson.default.encodeToString(Holder.serializer(), Holder(true)))
    }
}
