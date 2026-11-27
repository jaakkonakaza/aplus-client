package fi.aalto.cs.aplus.courseconfig

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigVersionTest {
    @Test
    fun `parses and normalises two integer components`() {
        assertEquals(ConfigVersion(4, 12), ConfigVersion.parse("4.12"))
        assertEquals("1.2", ConfigVersion.parse("01.002").toString())
        assertEquals("2147483647.2147483647", ConfigVersion.parse("2147483647.2147483647").toString())
    }

    @Test
    fun `rejects other forms`() {
        for (value in listOf("", "1", "1.", ".1", "1.2.3", "-1.0", "1.-1", "+1.0", " 1.0", "1.0 ", "1,0", "2147483648.0")) {
            assertNull(ConfigVersion.parseOrNull(value), value)
            assertFailsWith<IllegalArgumentException>(value) { ConfigVersion.parse(value) }
        }
        assertFailsWith<IllegalArgumentException> { ConfigVersion(-1, 0) }
    }

    @Test
    fun `orders by major then minor`() {
        assertTrue(ConfigVersion(4, 10) > ConfigVersion(4, 9))
        assertTrue(ConfigVersion(5, 0) > ConfigVersion(4, 99))
        assertEquals(0, ConfigVersion.parse("4.01").compareTo(ConfigVersion(4, 1)))
    }
}
