#!/usr/bin/env python3
"""Check produced JSON output against schema.json.

schema.json is hand-maintained and is what the reference docs and every consumer
are written against, so a field the serializers emit but the schema never
declares is a silent contract break: `alt_source` and `pdfua_tag` shipped in
every output for releases without appearing in the schema at all, and `font`
was declared a string while the serializer writes null for any node whose font
has no name.

Two checks run over each file:

  * JSON Schema validation, which catches a declared field whose value no longer
    matches its declared type.
  * An undeclared-field sweep, which catches the opposite drift. Plain
    validation cannot: the schema sets no `additionalProperties`, by design, so
    an unknown field passes silently.

Usage: validate-json-schema.py FILE_OR_DIR [FILE_OR_DIR ...]
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCHEMA_PATH = ROOT / "schema.json"

# Fields the serializers attach to any element, declared once rather than on
# every definition.
UNIVERSAL_FIELDS = {"type", "id", "page number", "bounding box"}

# The document object carries no "type" of its own, so the sweep below cannot
# find its definition the way it finds an element's. It is checked separately
# against the schema's top-level properties, under this name.
ROOT_LABEL = "document"


def resolve(node, defs, depth=0):
    """Every property name a schema node allows, following $ref and allOf."""
    if depth > 20 or not isinstance(node, dict):
        return {}
    if "$ref" in node:
        name = node["$ref"].split("/")[-1]
        return resolve(defs.get(name, {}), defs, depth + 1)
    props = {}
    for combinator in ("allOf", "anyOf", "oneOf"):
        for sub in node.get(combinator, []):
            props.update(resolve(sub, defs, depth + 1))
    props.update(node.get("properties", {}))
    return props


def build_type_map(schema):
    """Map each element's "type" value to the property names its definition allows."""
    defs = schema.get("$defs", {})
    by_type = {}
    for name, definition in defs.items():
        props = resolve(definition, defs)
        const = props.get("type", {}).get("const")
        if const is None:
            continue
        for value in const if isinstance(const, list) else [const]:
            by_type[value] = set(props) | UNIVERSAL_FIELDS
    return by_type


def check_root(document, schema, undeclared):
    """Sweep the document object itself, which has no "type" to look up."""
    if not isinstance(document, dict):
        return
    allowed = set(resolve(schema, schema.get("$defs", {})))
    for field in sorted(set(document) - allowed):
        undeclared.setdefault((ROOT_LABEL, field), "$")


def walk(node, by_type, path, undeclared, unknown_types):
    if isinstance(node, list):
        for index, item in enumerate(node):
            walk(item, by_type, f"{path}[{index}]", undeclared, unknown_types)
        return
    if not isinstance(node, dict):
        return

    element_type = node.get("type")
    if isinstance(element_type, str):
        allowed = by_type.get(element_type)
        if allowed is None:
            unknown_types.add(element_type)
        else:
            for field in sorted(set(node) - allowed):
                undeclared.setdefault((element_type, field), path)

    for key, value in node.items():
        walk(value, by_type, f"{path}.{key}", undeclared, unknown_types)


def validate_against_schema(document, schema, name):
    try:
        import jsonschema
    except ImportError:
        print("  ! jsonschema not installed - skipping type validation", file=sys.stderr)
        return []
    validator = jsonschema.Draft7Validator(schema)
    return [
        "  {}: {} (at {})".format(
            name, error.message, "/".join(str(part) for part in error.absolute_path) or "<root>"
        )
        for error in sorted(validator.iter_errors(document), key=lambda e: list(e.absolute_path))
    ]


def collect_files(arguments):
    files = []
    for argument in arguments:
        path = Path(argument)
        if path.is_dir():
            files.extend(sorted(path.rglob("*.json")))
        elif path.is_file():
            files.append(path)
        else:
            print(f"warning: {path} does not exist, skipping", file=sys.stderr)
    return files


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2

    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    by_type = build_type_map(schema)

    files = collect_files(argv[1:])
    if not files:
        print("error: no JSON files to check", file=sys.stderr)
        return 2

    failures = []
    for path in files:
        try:
            document = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            failures.append(f"{path}: not valid JSON: {error}")
            continue

        print(f"checking {path}")
        undeclared = {}
        unknown_types = set()
        check_root(document, schema, undeclared)
        walk(document, by_type, "$", undeclared, unknown_types)

        for (element_type, field), where in sorted(undeclared.items()):
            failures.append(
                f'{path}: "{element_type}" carries undeclared field "{field}" '
                f"(first seen at {where}); add it to schema.json"
            )
        for element_type in sorted(unknown_types):
            failures.append(
                f'{path}: element type "{element_type}" has no definition in schema.json'
            )
        failures.extend(
            f"{path}:{message}" for message in validate_against_schema(document, schema, "schema")
        )

    if failures:
        print(f"\n{len(failures)} schema problem(s):\n", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        print(
            "\nschema.json is the contract the reference docs and every consumer read.\n"
            "Update it in the same change as the serializer, then run `npm run sync-schema`.",
            file=sys.stderr,
        )
        return 1

    print(f"\nOK: {len(files)} file(s) match schema.json")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
