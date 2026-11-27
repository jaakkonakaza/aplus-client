# Test fixtures

`openapi.json` describes A+ API v2 at upstream commit
[`98301bae9fb7fc010259840b9d248af96c3351d9`](https://github.com/apluslms/a-plus/tree/98301bae9fb7fc010259840b9d248af96c3351d9)
from 2026-11-17. It covers 58 paths.

Response fixtures were prepared from development-server responses and serializer definitions.
They were checked against that revision but have not been recaptured from a running server.

## Refresh the schema

Use a clean A+ checkout and run from this repository:

```sh
uv venv --python 3.12 /tmp/aplus-schema-venv
uv pip install --python /tmp/aplus-schema-venv/bin/python \
  -r ../a-plus/requirements.txt
uv pip install --python /tmp/aplus-schema-venv/bin/python \
  -r scripts/requirements-schema.txt
/tmp/aplus-schema-venv/bin/python scripts/generate-openapi.py \
  ../a-plus src/test/resources/fixtures/openapi.json
./gradlew test
```

Update the recorded revision after refreshing. Schema generation leaves the server checkout unchanged.
Points, exercise listings and submission exports need response fixtures because the generated
schema does not describe their full response shapes. Exports return a bare array despite the
schema describing pagination.

## Refresh responses

Compare fixtures with the matching server serializers and response builders.
Keep separate examples for empty lists, group submissions and unreleased feedback.
Remove real credentials and personal data before saving responses.

Hardcoded tags have no database ID. Group exports contain one row per submitter, so a submission
ID can occur more than once. Unreleased submissions can expose penalties while hiding grades
and feedback.
