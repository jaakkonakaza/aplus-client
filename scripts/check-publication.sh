#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
publication_repository="$PWD/build/publication-smoke-repository"
release_version="$(awk -F= '$1 == "version" { print $2 }' gradle.properties)"
./gradlew publishToMavenLocal -Dmaven.repo.local="$publication_repository"
./gradlew -p publication-smoke check -PpublicationRepository="$publication_repository" -PreleaseVersion="$release_version"
