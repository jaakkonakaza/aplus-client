# Course configuration

`course-config` reads and writes course manifests in Kotlin.
`course-config-scala_3` adds Scala factories. Both require Java 25 or newer.
The Scala adapter requires Scala 3.9 or newer.

For Kotlin with Gradle, add Maven Central and:

```kotlin
implementation("io.github.jaakkonakaza:course-config:0.0.1")
```

For Scala CLI:

```scala
//> using dep "io.github.jaakkonakaza::course-config-scala:0.0.1"
import fi.aalto.cs.aplus.courseconfig.scala.*

val document = Document(
  id = "524",
  name = "O1",
  languages = Seq("fi", "en"),
  resources = Resources(projectSettings = "https://example.test/settings.zip")
)
val json = document.toJson()
```

Kotlin callers use `CourseConfig.Document` and `CourseConfig.parse(json)`.
Parsing ignores unknown fields. Call `CourseConfig.validate(document)` to check a parsed
manifest. Writing with `toJson()` validates it first.

`id` is the course's A+ ID. JSON requires `id`, `name`, `modules` and `exerciseModules`.

See the [minimal](../examples/course-config/minimal.json),
[Scala](../examples/course-config/scala.json) and [Python](../examples/course-config/python.json)
examples. Replace their placeholder IDs and URLs before use.
The [course registry](https://version.aalto.fi/gitlab/aplus-courses/course-config-urls)
lists published course manifests in `courses.yaml`.

The [Document API](https://jaakkonakaza.github.io/aplus-client/course-config/fi.aalto.cs.aplus.courseconfig/-course-config/-document/index.html)
and [Scala API](https://jaakkonakaza.github.io/aplus-client/scala/index.html) cover fields, grading and
versions.
