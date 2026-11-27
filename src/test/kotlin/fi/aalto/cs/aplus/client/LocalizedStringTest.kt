package fi.aalto.cs.aplus.client

import kotlin.test.Test
import kotlin.test.assertEquals

/** Ports the cases from the A+ server's `lib/localization_syntax.py`. */
class LocalizedStringTest {

    @Test
    fun `picks the requested language and re-attaches the prefix`() {
        val string = LocalizedString("1. |en:Intro|fi:Johdanto|")
        assertEquals("1. Intro", string.pick("en"))
        assertEquals("1. Johdanto", string.pick("fi"))
    }

    @Test
    fun `re-attaches a suffix as well`() {
        val string = LocalizedString("|en:Week|fi:Viikko| 1")
        assertEquals("Week 1", string.pick("en"))
        assertEquals("Viikko 1", string.pick("fi"))
    }

    @Test
    fun `falls back to english when the language is missing`() {
        assertEquals("Intro", LocalizedString("|en:Intro|fi:Johdanto|").pick("sv"))
    }

    @Test
    fun `falls back to the first variant when english is missing too`() {
        assertEquals("Johdanto", LocalizedString("|fi:Johdanto|sv:Inledning|").pick("de"))
    }

    @Test
    fun `passes an unlocalized string through unchanged`() {
        assertEquals("Plain title", LocalizedString("Plain title").pick("fi"))
    }

    @Test
    fun `a single pipe is not a localization`() {
        assertEquals("a|b", LocalizedString("a|b").pick("en"))
    }

    @Test
    fun `a region tag is still a language`() {
        assertEquals("Text", LocalizedString("|en-GB:Text|").pick("en-GB"))
    }
}
