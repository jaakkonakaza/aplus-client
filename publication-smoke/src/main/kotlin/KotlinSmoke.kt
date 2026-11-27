import fi.aalto.cs.aplus.client.AplusClient
import fi.aalto.cs.aplus.courseconfig.CourseConfig

fun main() {
    AplusClient.create("https://example.org", tokenProvider = { null }).use {
        check(it.baseUrl == "https://example.org/api/v2")
    }
    val document = CourseConfig.Document("1", "Course")
    check(CourseConfig.parse(document.toJson()) == document)
}
