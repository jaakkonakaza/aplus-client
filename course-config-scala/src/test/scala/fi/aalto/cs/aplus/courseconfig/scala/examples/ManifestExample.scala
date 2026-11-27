package fi.aalto.cs.aplus.courseconfig.scala.examples

import fi.aalto.cs.aplus.courseconfig.scala.*

object ManifestExample:
  def document(): Document =
    Document(
      id = "524",
      name = "Example course",
      requiredPluginVersion = "4.4",
      aPlusUrl = "https://plus.cs.aalto.fi/",
      languages = List("en"),
      modules = List(Module(name = "Hello", url = "https://example.org/Hello.zip")),
      exerciseModules = Map(42L -> Map("en" -> "Hello")),
      grading = Grading.Total(thresholds = List(
        Grading.Total.Threshold(grade = "pass", points = 50),
        Grading.Total.Threshold(grade = "excellent", points = 90)
      ))
    )
