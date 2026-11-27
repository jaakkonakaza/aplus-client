# aplus-client

A Kotlin/JVM client for the [A+ LMS](https://github.com/apluslms/a-plus) REST API.

## Installation

Use Java 25 or newer and add the dependency to your Gradle build:

```kotlin
repositories { mavenCentral() }
dependencies {
    implementation("io.github.jaakkonakaza:aplus-client:0.0.1")
}
```

## Usage

Set `APLUS_TOKEN` to your A+ API token, then run:

```kotlin
import fi.aalto.cs.aplus.client.AplusClient

suspend fun main() {
    val token = requireNotNull(System.getenv("APLUS_TOKEN")) { "Set APLUS_TOKEN" }
    AplusClient.create(
        baseUrl = "https://plus.cs.aalto.fi",
        tokenProvider = { token },
    ).use { aplus ->
        println(aplus.me())
    }
}
```

The client supports courses, exercises, rosters, points, news and submissions.
Some operations require a staff token. See the
[client API](https://jaakkonakaza.github.io/aplus-client/aplus-client/fi.aalto.cs.aplus.client/-aplus-client/index.html)
for permissions and limits. Change `baseUrl` to use another A+ server.

Scala and Java callers can use `AplusBlockingClient` for synchronous requests:

```scala
import fi.aalto.cs.aplus.client.AplusBlockingClient
import scala.util.Using

Using.resource(AplusBlockingClient.create("https://plus.cs.aalto.fi", () => sys.env("APLUS_TOKEN"))) { aplus =>
  println(aplus.course(3L).getName)
}
```

Listings fetch all pages. Use `maxResults` to limit a listing and `AplusLimits` to set download
limits. API and response errors raise `AplusException`. Transport errors and invalid arguments
are raised separately.

For multiple users, use `aplus.withToken(token)`. Keep the owning client open until all views
finish. `withToken(null)` makes an anonymous view. Avoid shared default authorization headers.

## Course configuration

The separate [course-config libraries](course-config/README.md) read and write course manifests
from Kotlin and Scala. JSON examples: [minimal](examples/course-config/minimal.json),
[Scala](examples/course-config/scala.json), [Python](examples/course-config/python.json).

## Development

```sh
./gradlew build
```

The build runs tests and checks API compatibility. After a public API change, run
`./gradlew updateKotlinAbi`. For Scala JVM API changes, review and run
`./gradlew :course-config-scala:updateScalaApi`. See the [fixture notes](src/test/resources/fixtures/README.md)
to refresh test data. Live tests use `APLUS_URL`, `APLUS_TOKEN` and `APLUS_COURSE`.

See [release instructions](docs/RELEASING.md).
The [published API reference](https://jaakkonakaza.github.io/aplus-client/) includes usage samples.
