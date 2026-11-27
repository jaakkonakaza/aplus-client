#!/usr/bin/env bash
set -euo pipefail

release_tag="${1:?Pass the release tag, such as v0.0.1}"
release_version="$(sed -n 's/^version=//p' gradle.properties)"

if [[ ! "$release_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  printf 'Expected a numeric release version in gradle.properties\n' >&2
  exit 1
fi
if [[ "$release_tag" != "v$release_version" ]]; then
  printf 'Release tag %s does not match version %s\n' "$release_tag" "$release_version" >&2
  exit 1
fi
