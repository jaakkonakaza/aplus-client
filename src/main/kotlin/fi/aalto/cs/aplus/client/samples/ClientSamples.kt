@file:Suppress("unused") // Referenced by KDoc @sample.

package fi.aalto.cs.aplus.client.samples

import fi.aalto.cs.aplus.client.AplusClient

private suspend fun readCourse() {
    val baseUrl = requireNotNull(System.getenv("APLUS_URL"))
    AplusClient.create(baseUrl, tokenProvider = { System.getenv("APLUS_TOKEN") }).use { client ->
        val me = client.me()
        val points = client.points(courseId = 3)
        println(me)
        println(points)
    }
}

private suspend fun readRoster() {
    val baseUrl = requireNotNull(System.getenv("APLUS_URL"))
    AplusClient.create(baseUrl, tokenProvider = { System.getenv("APLUS_TOKEN") }).use { client ->
        // A staff token is required. The result includes every page within the configured limits.
        val students = client.students(courseId = 3, maxResults = 1_000)
        println(students)
    }
}

private suspend fun authenticationViews() {
    val baseUrl = requireNotNull(System.getenv("APLUS_URL"))
    AplusClient.create(baseUrl, tokenProvider = { null }).use { owner ->
        val alice = owner.withToken(System.getenv("ALICE_TOKEN"))
        val bob = owner.withToken(System.getenv("BOB_TOKEN"))
        println(alice.me())
        println(bob.me())
    }
}
