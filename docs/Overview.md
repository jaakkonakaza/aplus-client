# Module A+ JVM libraries

Use **aplus-client** to call the A+ REST API from Kotlin or Java.
Use **course-config** to read and author course manifests without an HTTP client or IDE dependency.
The **course-config-scala** adapter provides named Scala factories for the shared manifest types.

[Scala API reference](https://jaakkonakaza.github.io/aplus-client/scala/index.html)

## JSON examples

The [A+ Courses configuration registry](https://version.aalto.fi/gitlab/aplus-courses/course-config-urls)
lists published course manifests in `courses.yaml`.

Download the examples below and replace their placeholder IDs and URLs before use.

The minimal template catalog omits IDE settings. The Scala course maps exercise 1012 to
StudentProject, which students create themselves. It needs no entry in `modules` or `autoInstall`.

The document's `version` controls plugin compatibility. A+ Courses blocks setup for an older
major version. Other mismatches trigger notifications. Modules have separate archive versions.
Grading thresholds appear from lowest to highest. The total style sums points across categories.

The Scala example includes sbt, language-specific downloads, student-created modules, REPL
commands and O1 grading. The Python example includes bilingual modules, a download callback
and total-points grading. Together they cover every supported field.
JVM properties in `vmOptions` omit `-D`. Callback identifiers must be supported by the plugin.
Prepare the settings archives, properties file and feedback CSS at their resource URLs.
Scala archives must contain the packages imported by the REPL commands.
The Python example's `AddModuleWatermark` callback adds student and course details to `.py` files.
