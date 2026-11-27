package fi.aalto.cs.aplus.courseconfig.scala

import fi.aalto.cs.aplus.courseconfig.CourseConfig as Wire
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import _root_.scala.jdk.CollectionConverters.*

class CourseConfigTest:
  @Test def optionalArgumentsAcceptPlainValuesAndOptions(): Unit =
    val settings = Resources(
      ideSettings = "ide.zip", ideSettingsMac = "mac.zip", ideSettingsLinux = "linux.zip",
      ideSettingsWindows = "windows.zip", projectSettings = "project.zip",
      customProperties = "course.properties", feedbackCss = "feedback.css"
    )
    assertEquals(Resources(
      ideSettings = Some("ide.zip"), ideSettingsMac = Some("mac.zip"), ideSettingsLinux = Some("linux.zip"),
      ideSettingsWindows = Some("windows.zip"), projectSettings = Some("project.zip"),
      customProperties = Some("course.properties"), feedbackCss = Some("feedback.css")
    ), settings)
    val module = Module("Library", "Library.zip", language = "fi", changelog = "")
    assertEquals(Module("Library", "Library.zip", language = Some("fi"), changelog = Some("")), module)
    val repl = ScalaRepl(arguments = "-deprecation")
    assertEquals(ScalaRepl(arguments = Some("-deprecation")), repl)
    val callbacks = Callbacks(Seq("example"))
    val grading = Grading.Total(Vector(Grading.Total.Threshold("pass", 10)))
    val plain = Document("524", "O1", modules = Vector(module), languages = Seq("fi"),
      aPlusUrl = "https://plus.cs.aalto.fi/", resources = settings, scalaRepl = repl,
      callbacks = callbacks, grading = grading)
    val wrapped = Document("524", "O1", modules = List(module), languages = List("fi"),
      aPlusUrl = Some("https://plus.cs.aalto.fi/"), resources = Some(settings), scalaRepl = Some(repl),
      callbacks = Some(callbacks), grading = Some(grading))
    assertEquals(wrapped, plain)
    assertEquals(plain, Wire.parse(plain.toJson))

  @Test def absentArgumentsAndEmptyStringsRemainDistinct(): Unit =
    val missing: Option[String] = None
    assertEquals(Resources(), Resources(projectSettings = missing, ideSettings = None))
    assertEquals(Document("524", "O1"), Document("524", "O1", aPlusUrl = missing,
      resources = None, scalaRepl = None, callbacks = None, grading = None))
    assertNull(Module("Library", "Library.zip").getChangelog)
    val module = Module("Library", "Library.zip", changelog = "")
    assertEquals("", module.getChangelog)
    assertEquals("", Resources(projectSettings = "").getProjectSettings)
    assertEquals("", ScalaRepl(arguments = "").getArguments)
    val document = Document("524", "O1", modules = Seq(module))
    assertEquals("", Wire.parse(document.toJson).getModules.get(0).getChangelog)

  @Test def documentedExampleRoundTrips(): Unit =
    val document = examples.ManifestExample.document()
    assertEquals(document, Wire.parse(document.toJson))
    assertEquals("4.4", document.getVersion)

  @Test def namedFactoriesPreserveAllArguments(): Unit =
    val library: Module = Module(name = "Library", url = "https://example.test/Library.zip", version = "1.2",
      language = Some("fi"), changelog = Some("Added exercises"), sbt = true)
    val document: Document = Document(
      id = "524",
      name = "O1",
      modules = List(library),
      exerciseModules = Map(42L -> Map("fi" -> "Ave", "en" -> "Ave")),
      aPlusUrl = Some("https://plus.cs.aalto.fi/"),
      languages = List("fi", "en"),
      requiredPluginVersion = "4.4",
      resources = Some(Resources(
        ideSettings = Some("https://example.test/ide.zip"),
        ideSettingsMac = Some("https://example.test/mac.zip"),
        ideSettingsLinux = Some("https://example.test/linux.zip"),
        ideSettingsWindows = Some("https://example.test/windows.zip"),
        projectSettings = Some("https://example.test/settings.zip"),
        customProperties = Some("https://example.test/course.properties"),
        feedbackCss = Some("https://example.test/feedback.css")
      )),
      requiredPlugins = List(RequiredPlugin(name = "Scala", id = "org.intellij.scala")),
      vmOptions = Map("file.encoding" -> "UTF-8"),
      optionalCategories = List("training"),
      autoInstall = List(library.name),
      scalaRepl = Some(ScalaRepl(initialCommands = Map("Ave" -> List("import o1.*")), arguments = Some("-deprecation"))),
      callbacks = Some(Callbacks(postDownloadModule = List("example"))),
      hiddenElements = List(123L),
      grading = Some(Grading.Total(List(Grading.Total.Threshold("pass", 50)))),
      alwaysShowGroups = true
    )
    val expected = Wire.parse("""{
      "id":"524", "name":"O1", "version":"4.4",
      "modules":[{"name":"Library", "url":"https://example.test/Library.zip", "version":"1.2",
        "language":"fi", "changelog":"Added exercises", "sbt":true}],
      "exerciseModules":{"42":{"fi":"Ave", "en":"Ave"}},
      "aPlusUrl":"https://plus.cs.aalto.fi/", "languages":["fi","en"],
      "resources":{
        "ideSettings":"https://example.test/ide.zip", "ideSettingsMac":"https://example.test/mac.zip",
        "ideSettingsLinux":"https://example.test/linux.zip", "ideSettingsWindows":"https://example.test/windows.zip",
        "projectSettings":"https://example.test/settings.zip",
        "customProperties":"https://example.test/course.properties", "feedbackCss":"https://example.test/feedback.css"
      },
      "requiredPlugins":[{"name":"Scala", "id":"org.intellij.scala"}],
      "vmOptions":{"file.encoding":"UTF-8"}, "optionalCategories":["training"], "autoInstall":["Library"],
      "scalaRepl":{"initialCommands":{"Ave":["import o1.*"]}, "arguments":"-deprecation"},
      "callbacks":{"postDownloadModule":["example"]}, "hiddenElements":[123],
      "grading":{"style":"total", "points":{"pass":{"total":50}}}, "alwaysShowGroups":true
    }""")
    assertEquals(expected, document)

  @Test def gradingPreservesFiveGradeProgression(): Unit =
    val grading = Grading.O1(thresholds = List(
      Grading.O1.Threshold(grade = "1", a = 2000, b = 0, c = 0),
      Grading.O1.Threshold(grade = "2", a = 2100, b = 450, c = 0),
      Grading.O1.Threshold(grade = "3", a = 2100, b = 800, c = 0),
      Grading.O1.Threshold(grade = "4", a = 2100, b = 800, c = 450),
      Grading.O1.Threshold(grade = "5", a = 2100, b = 800, c = 625)
    ))
    val document = Document(id = "524", name = "O1", grading = Some(grading))
    val decoded = Wire.parse(document.toJson).getGrading
    assertEquals(List("1", "2", "3", "4", "5"), decoded.getPoints.keySet.asScala.toList)
    assertEquals(625, decoded.getPoints.get("5").get("C").intValue)
    assertEquals(Some("4"), grading.progress(Map("A" -> 2000, "B" -> 900, "C" -> 450)).map(_.getGrade))
    assertEquals(None, grading.progress(Map("A" -> 2000)))
    val future = new Wire.Grading("future", java.util.Collections.emptyMap())
    assertEquals(None, future.progress(Map("A" -> 2000)))

  @Test def totalGradingAndCollections(): Unit =
    val grading: Grading = Grading.Total(thresholds = List(
      Grading.Total.Threshold(grade = "pass", points = 10), Grading.Total.Threshold(grade = "excellent", points = 20)
    ))
    val document = Document(id = "524", name = "O1", grading = Some(grading))
    assertEquals(20, document.getGrading.getPoints.get("excellent").get("total").intValue)

  @Test def rejectsInvalidGradeProgressions(): Unit =
    def rejected(thresholds: List[Grading.O1.Threshold]): Unit =
      assertThrows(classOf[IllegalArgumentException], () => {
        Grading.O1(thresholds)
        ()
      })

    rejected(Nil)
    rejected(List(Grading.O1.Threshold("1", 1, 0, 0), Grading.O1.Threshold("1", 2, 0, 0)))

  @Test def factoryValidatesAuthoredDocuments(): Unit =
    assertThrows(classOf[IllegalArgumentException], () => {
      Document(id = "524", name = "O1", autoInstall = List("Missing"))
      ()
    })
