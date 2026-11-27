# Releasing

Set `version` in `gradle.properties` and update the dependency examples.
For version `0.0.1`, use tag `v0.0.1`. Course plugin and module archive versions are separate.

All artifacts use group `io.github.jaakkonakaza` and require Java 25:

- `aplus-client`
- `course-config`
- `course-config-scala_3`, for Scala 3.9 or newer

## Check locally

```sh
bash scripts/check-release-version.sh v0.0.1
./gradlew clean build publishToMavenLocal -Dmaven.repo.local="$PWD/build/release-repository" -PdocsRevision=v0.0.1
```

Artifacts are written to `build/release-repository` without uploading them.
Check the JARs, sources, documentation and publication metadata for all three artifacts.
Artifact versions must match `version` in `gradle.properties`. Each JAR must include `META-INF/LICENSE`.
The Scala adapter must depend on the matching course-config release and Scala 3.9.0.
Published dependencies must exclude test libraries and documentation tools.
Run `bash scripts/check-publication.sh` to verify isolated Kotlin and Scala consumers against
the staged artifacts.

## Set up publishing

Verify the Maven Central namespace and publish the public signing key to a supported keyserver.
Add these GitHub repository secrets:

- `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` from a Central Portal user token.
- `SIGNING_KEY`, the ASCII-armored private signing key.
- `SIGNING_PASSWORD` if the key has a passphrase.

## Publish

Commit and push the reviewed sources and matching tag. Publish a GitHub release for that tag.
The Publish workflow checks the version, builds, signs and releases the artifacts to Maven Central.

For a manual retry, supply the existing tag as the workflow's `tag` input.
Check the Central Portal before retrying because release versions cannot be replaced.
After publishing, resolve all three artifacts from a separate consumer build.
