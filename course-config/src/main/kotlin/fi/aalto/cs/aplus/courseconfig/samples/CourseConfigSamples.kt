@file:Suppress("unused") // Referenced by KDoc @sample.

package fi.aalto.cs.aplus.courseconfig.samples

import fi.aalto.cs.aplus.courseconfig.CourseConfig

private fun minimalManifest(): CourseConfig.Document {
    val document = CourseConfig.Document(id = "524", name = "Example course")
    val json = document.toJson()
    return CourseConfig.parse(json)
}

private fun pluginManifest(): CourseConfig.Document {
    val document = CourseConfig.Document(
        id = "524",
        name = "Example course",
        aPlusUrl = "https://plus.cs.aalto.fi/",
        languages = listOf("en"),
        version = "4.4",
        modules = listOf(CourseConfig.Module("Hello", "https://example.org/Hello.zip", version = "1.2")),
        exerciseModules = mapOf(42L to mapOf("en" to "Hello"), 43L to mapOf("en" to "StudentProject")),
        autoInstall = listOf("Hello"),
    )
    CourseConfig.validate(document)
    return document
}

private fun gradeProgression(): CourseConfig.GradeProgress? {
    val grading = CourseConfig.Grading(
        style = CourseConfig.Grading.TOTAL,
        points = linkedMapOf(
            "pass" to mapOf("total" to 50),
            "excellent" to mapOf("total" to 90),
        ),
    )
    grading.validate()
    return grading.progress(mapOf("exercises" to 40, "project" to 20))
}
