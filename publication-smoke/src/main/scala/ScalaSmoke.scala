import fi.aalto.cs.aplus.courseconfig.scala.*
import fi.aalto.cs.aplus.courseconfig.CourseConfig
import fi.aalto.cs.aplus.client.{AplusBlockingClient, AplusLimits}
import scala.util.Using

@main def scalaSmoke(): Unit =
  val document = Document(
    id = "1", name = "Course", languages = List("en"),
    resources = Resources(ideSettingsLinux = "https://example.org/linux.zip"),
    grading = Grading.Total(List(Grading.Total.Threshold("pass", 10)))
  )
  require(CourseConfig.parse(document.toJson()) == document)
  require(document.getGrading.progress(Map("exercises" -> 10)).exists(_.getGrade == "pass"))
  Using.resource(AplusBlockingClient.create("https://example.org", () => "token", new AplusLimits(), 5000L)) { client =>
    require(client.getBaseUrl == "https://example.org/api/v2")
    Using.resource(client.withToken("another")) { view =>
      require(view.getTimeoutMillis == 5000L)
    }
  }
