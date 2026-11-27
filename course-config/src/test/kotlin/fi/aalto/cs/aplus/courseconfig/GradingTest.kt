package fi.aalto.cs.aplus.courseconfig

import fi.aalto.cs.aplus.courseconfig.CourseConfig.Grading
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GradingTest {
    @Test
    fun `invalid known grading rules never award a grade`() {
        val rules = listOf(
            Grading("total", mapOf("pass" to mapOf("totla" to 100))),
            Grading("o1", mapOf("pass" to mapOf("A" to 100))),
            Grading("total", linkedMapOf("1" to mapOf("total" to 100), "2" to mapOf("total" to 50))),
        )
        for (grading in rules) assertNull(grading.progress(mapOf("A" to 0, "B" to 0, "C" to 0)))
    }
    private val o1 = Grading(
        style = Grading.O1,
        points = linkedMapOf(
            "1" to mapOf("A" to 100, "B" to 0, "C" to 0),
            "2" to mapOf("A" to 200, "B" to 100, "C" to 0),
            "3" to mapOf("A" to 300, "B" to 200, "C" to 100),
        ),
    )

    private fun o1Points(a: Int, b: Int, c: Int) = mapOf("A" to a, "B" to b, "C" to c)

    @Test
    fun `an unknown style has no progress`() {
        assertNull(Grading("custom", mapOf("1" to mapOf("total" to 10))).progress(mapOf("total" to 5)))
    }

    @Test
    fun `the grade is the highest one whose requirements are all met`() {
        assertEquals("3", o1.progress(o1Points(a = 300, b = 200, c = 100))?.grade)
    }

    @Test
    fun `surplus points in higher categories count towards A`() {
        assertEquals("1", o1.progress(o1Points(a = 40, b = 40, c = 40))?.grade)
    }

    @Test
    fun `points carried into A are no longer available for B`() {
        val progress = o1.progress(o1Points(a = 100, b = 100, c = 0))
        assertEquals("1", progress?.grade)
        assertEquals(0, progress?.pointsUntilNext?.get("A"))
        assertEquals(100, progress?.pointsUntilNext?.get("B"))
    }

    @Test
    fun `points until the next grade are reported per category`() {
        val progress = o1.progress(o1Points(a = 100, b = 0, c = 0))
        assertEquals("1", progress?.grade)
        assertEquals(o1Points(a = 100, b = 100, c = 0), progress?.pointsUntilNext)
        assertEquals(o1Points(a = 200, b = 100, c = 0), progress?.maxOfNext)
    }

    @Test
    fun `the highest grade reports zero points remaining`() {
        val progress = o1.progress(o1Points(a = 500, b = 300, c = 200))
        assertEquals("3", progress?.grade)
        assertEquals(o1Points(a = 0, b = 0, c = 0), progress?.pointsUntilNext)
    }

    @Test
    fun `a missing o1 category means no grade can be calculated`() {
        assertNull(o1.progress(mapOf("A" to 100, "B" to 100)))
    }

    @Test
    fun `the total style sums every category`() {
        val total = Grading(Grading.TOTAL, linkedMapOf("1" to mapOf("total" to 100), "2" to mapOf("total" to 200)))
        val progress = total.progress(mapOf("A" to 60, "B" to 60))
        assertEquals("1", progress?.grade)
        assertEquals(mapOf("total" to 80), progress?.pointsUntilNext)
        assertEquals(mapOf("total" to 200), progress?.maxOfNext)
    }

    @Test
    fun `total points above Int MAX still reach the highest grade`() {
        val grading = Grading(Grading.TOTAL, mapOf("pass" to mapOf("total" to Int.MAX_VALUE)))
        grading.validate()
        val progress = grading.progress(mapOf("A" to Int.MAX_VALUE, "B" to 1))
        assertEquals("pass", progress?.grade)
        assertEquals(mapOf("total" to 0), progress?.pointsUntilNext)
    }

    @Test
    fun `total deficits beyond Int MAX are capped`() {
        val grading = Grading(Grading.TOTAL, mapOf("pass" to mapOf("total" to Int.MAX_VALUE)))
        val progress = grading.progress(mapOf("A" to Int.MIN_VALUE))
        assertNull(progress?.grade)
        assertEquals(mapOf("total" to Int.MAX_VALUE), progress?.pointsUntilNext)
    }

}
