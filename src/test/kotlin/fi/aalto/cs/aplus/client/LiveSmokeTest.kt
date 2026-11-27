package fi.aalto.cs.aplus.client

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Disabled unless APLUS_URL is set. APLUS_TOKEN and APLUS_COURSE supply authentication
 * and the course to inspect. Missing course data skips the affected checks.
 */
@EnabledIfEnvironmentVariable(named = "APLUS_URL", matches = ".+")
class LiveSmokeTest {
    private val baseUrl = System.getenv("APLUS_URL")
    private val token = System.getenv("APLUS_TOKEN")
    private val courseId = System.getenv("APLUS_COURSE")?.toLongOrNull()

    private fun client() = AplusClient.create(baseUrl = baseUrl, tokenProvider = { token })

    @Test
    fun `the token resolves to a user`(): Unit = runBlocking {
        client().use { aplus ->
            val me = aplus.me()
            assertTrue(me.id > 0)
            assertTrue(me.username.isNotBlank())
        }
    }

    @Test
    fun `the course exposes points, exercises, news and groups`(): Unit = runBlocking {
        assumeTrue(courseId != null, "Set APLUS_COURSE to check the course endpoints")
        val id = courseId!!
        client().use { aplus ->
            assertEquals(id, aplus.course(id).id)
            val modules = aplus.exerciseModules(id)
            assertTrue(modules.isNotEmpty(), "The course has no exercise modules to check")
            val points = aplus.points(id)
            assertTrue(points.maxPoints >= 0)
            aplus.news(id)
            aplus.myGroups(id)

            val exerciseId = modules.flatMap { it.exercises }.firstOrNull()?.id
            if (exerciseId != null) assertEquals(exerciseId, aplus.exercise(exerciseId).id)
        }
    }
}
