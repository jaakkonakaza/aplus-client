#!/usr/bin/env python3
"""Generate the API v2 schema from an A+ source checkout without changing it."""

import argparse
import json
import os
import re
from pathlib import Path
import sys
from types import ModuleType, SimpleNamespace


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkout", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    sys.path.insert(0, str(args.checkout.resolve()))
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "aplus.settings")
    os.environ.setdefault("APLUS_BASE_URL", "http://localhost")
    os.environ.setdefault("APLUS_SECRET_KEY", "schema-generation-only")
    os.environ.setdefault("APLUS_LOCAL_SETTINGS", "aplus/local_settings.test.py")

    import django

    django.setup()
    from django.urls import include, path
    from rest_framework import serializers
    from rest_framework.schemas.openapi import AutoSchema, SchemaGenerator

    class UpstreamAutoSchema(AutoSchema):
        def get_operation_id(self, path, method):
            return method.lower() + "_" + re.sub(r"[^a-zA-Z0-9]+", "_", path).strip("_")

        def get_operation(self, path, method):
            operation = super().get_operation(path, method)
            # Points builds a tree in to_representation that DRF cannot infer from User fields.
            if type(self.view).__name__ == "CoursePointsViewSet" and method == "GET":
                for response in operation.get("responses", {}).values():
                    for media in response.get("content", {}).values():
                        schema = media.get("schema", {})
                        results = schema.get("properties", {}).get("results")
                        if results is not None:
                            results["items"] = {"type": "object"}
                        else:
                            media["schema"] = {"type": "object"}
            return operation

        def map_field(self, field):
            # A+ uses ListSerializer with primitive children for several custom fields.
            if isinstance(field, serializers.ListSerializer) and not isinstance(field.child, serializers.Serializer):
                return {"type": "array", "items": super().map_field(field.child)}
            schema = super().map_field(field)
            if isinstance(field, serializers.URLField):
                schema.pop("pattern", None)
            return schema

        def map_field_validators(self, field, schema):
            super().map_field_validators(field, schema)
            # Django's URL regex uses Python-only escapes. The uri format covers these fields.
            if isinstance(field, serializers.URLField):
                schema.pop("pattern", None)

        def get_filter_parameters(self, path, method):
            # A+ permission filters predate DRF's schema hook and declare no parameters.
            if not self.allows_filters(path, method):
                return []
            parameters = []
            for backend_class in self.view.filter_backends:
                hook = getattr(backend_class(), "get_schema_operation_parameters", None)
                if hook is not None:
                    parameters.extend(hook(self.view))
            return parameters

        def get_serializer(self, path, method):
            # Schema generation has no request or course context for serializer selection.
            if not hasattr(self.view, "get_serializer_class"):
                return None
            request = self.view.request
            if request is None:
                self.view.request = SimpleNamespace(method=method)
            try:
                try:
                    serializer_class = self.view.get_serializer_class()
                except AssertionError:
                    if getattr(self.view, "serializer_class", None) is None:
                        return None
                    raise
                return serializer_class()
            finally:
                self.view.request = request

    from rest_framework.settings import api_settings

    api_settings.DEFAULT_SCHEMA_CLASS = UpstreamAutoSchema
    urls = ModuleType("aplus_client_schema_urls")
    urls.urlpatterns = [path("api/v2/", include("api.urls_v2"))]
    sys.modules[urls.__name__] = urls
    schema = SchemaGenerator(title="A+ API", version="2.0", urlconf=urls.__name__).get_schema(
        request=None, public=True
    )
    args.output.write_text(json.dumps(schema, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
