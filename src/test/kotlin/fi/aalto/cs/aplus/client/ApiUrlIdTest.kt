package fi.aalto.cs.aplus.client

import fi.aalto.cs.aplus.client.dto.GroupMemberDto
import fi.aalto.cs.aplus.client.dto.PointsExerciseDto
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A+ embeds resource URLs where a nested object would otherwise appear.
 * Parsing the final segment supplies ids without another request.
 */
class ApiUrlIdTest {

    @Test
    fun `the trailing id is read with or without a trailing slash`() {
        // A+ accepts trailing slashes through trailing_slash = '/?' in api/urls_v2.py.
        // Its generated hyperlinks omit the slash.

        assertEquals(42L, AplusRoutes.idFromApiUrl("https://plus.cs.aalto.fi/api/v2/submissions/42"))
        assertEquals(42L, AplusRoutes.idFromApiUrl("https://plus.cs.aalto.fi/api/v2/submissions/42/"))
        assertEquals(517L, AplusRoutes.idFromApiUrl("http://localhost:8000/api/v2/users/517"))
    }

    @Test
    fun `a query string and a fragment are cut off rather than parsed`() {
        assertEquals(42L, AplusRoutes.idFromApiUrl("$URL_42?format=json"))
        assertEquals(42L, AplusRoutes.idFromApiUrl("$URL_42/?format=json"))
        assertEquals(42L, AplusRoutes.idFromApiUrl("$URL_42#files"))
        assertEquals(42L, AplusRoutes.idFromApiUrl("$URL_42?format=json#files"))

        assertEquals(42L, AplusRoutes.idFromApiUrl("$URL_42/?next=/api/v2/submissions/99"))
    }

    @Test
    fun `a paginated next link is not an id`() {
        // DRF pagination links address listings and carry page numbers in the query.

        assertNull(AplusRoutes.idFromApiUrl("http://localhost:8000/api/v2/courses/3/students?page=2"))
    }

    @Test
    fun `a url with no id at the end parses to nothing`() {
        assertNull(AplusRoutes.idFromApiUrl(null))
        assertNull(AplusRoutes.idFromApiUrl(""))
        assertNull(AplusRoutes.idFromApiUrl("https://plus.cs.aalto.fi/api/v2/exercises/5/submissions"))
        assertNull(AplusRoutes.idFromApiUrl("https://plus.cs.aalto.fi/api/v2/courses/3/points/me"))
        assertNull(AplusRoutes.idFromApiUrl("not a url"))
    }

    @Test
    fun `the best submission id is parsed from the url`() {
        val stock = PointsExerciseDto(id = 1, name = "e", bestSubmission = URL_42)
        val nothingSubmitted = PointsExerciseDto(id = 1, name = "e")

        assertEquals(42L, stock.resolvedBestSubmissionId)
        assertNull(nothingSubmitted.resolvedBestSubmissionId)
    }

    @Test
    fun `a group member's user id comes from the url, not from the member id`() {
        val member = GroupMemberDto(id = 3, url = "http://localhost:8000/api/v2/users/517/")

        assertEquals(517L, member.userIdOrNull)
    }

    @Test
    fun `an unreadable member url gives no user id rather than the member id`() {
        // Member ids use a separate sequence, so they cannot stand in for a user id.

        assertNull(GroupMemberDto(id = 3, url = null).userIdOrNull)
        assertNull(GroupMemberDto(id = 3, url = "http://localhost:8000/api/v2/users/me").userIdOrNull)
    }

    @Test
    fun `every hyperlink in the captures parses, and none of them carries a trailing slash`(): Unit =
        runBlocking {
            // Stock points expose best submission ids only through these captured hyperlinks.

            val plain = fixtureClient("points_plain.json").points(COURSE_ID)
            val links = plain.allExercises().mapNotNull { it.bestSubmission }

            assertEquals(CAPTURED_EXERCISES, links.size, "every exercise in the capture has a best submission")
            assertTrue(links.none { it.endsWith("/") }, "reverse() mints them slash-less")
            assertTrue(links.all { AplusRoutes.idFromApiUrl(it) != null }, "every one parses to an id")
        }

    private companion object {
        const val URL_42 = "https://plus.cs.aalto.fi/api/v2/submissions/42"
        const val CAPTURED_EXERCISES = 12
    }
}
