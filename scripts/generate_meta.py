#!/usr/bin/env python3
"""generate_meta.py — OpenAPI → PageMeta DRAFT JSON / INSERT SQL

OpenAPI JSON 과 DTO 이름을 입력받아 PageMeta JSON 골격을 생성한다.
- stdout: PageMeta JSON (pretty)
- --output 지정 시: page_meta INSERT SQL (ON CONFLICT DO NOTHING)

설계 규칙 (ADR-005·ADR-006, ARCHITECTURE §5):
- 신규 생성 메타의 metaStatus 는 항상 DRAFT (CLI 옵션으로도 변경 불가).
- systemType·packageType 는 Enum 값만 허용.
- id 패턴은 {groupId}-v{major}-{minor}.
- 필드 타입 매핑은 ARCHITECTURE §5 매핑표 + 이름 휴리스틱을 따른다.
"""

import argparse
import json
import sys
from pathlib import Path

SYSTEM_TYPES = {"ITSM", "ITAM", "PMS", "COMMON", "SYSTEM"}
PACKAGE_TYPES = {"PACKAGE", "CUSTOM"}
FIELD_TYPE_BY_OPENAPI = {
    ("string", None): "text",
    ("string", "date"): "date",
    ("string", "date-time"): "date",
    ("integer", None): "number",
    ("number", None): "number",
    ("boolean", None): "checkbox",
}
LONG_TEXT_THRESHOLD = 500


def detect_field_type(name: str, schema: dict) -> str:
    """OpenAPI property 의 이름·type·format 으로 FieldType 를 결정한다."""
    if name in {"priority"}:
        return "priority"
    if name in {"status"}:
        return "status"
    if "enum" in schema:
        return "select"
    t = schema.get("type", "string")
    fmt = schema.get("format")
    if name.endswith("Id") and name not in {"id"}:
        return "user-picker"
    if t == "string":
        if schema.get("maxLength", 0) > LONG_TEXT_THRESHOLD:
            return "textarea"
        if "Description" in name or "Content" in name or "Memo" in name:
            return "textarea"
    return FIELD_TYPE_BY_OPENAPI.get((t, fmt), "text")


def build_form_fields(request_schema: dict) -> list:
    """request DTO 의 property 에서 form.fields 를 추출한다."""
    fields = []
    required = set(request_schema.get("required", []))
    for name, sub in request_schema.get("properties", {}).items():
        field = {
            "name": name,
            "label": sub.get("description") or name,
            "type": detect_field_type(name, sub),
        }
        if name in required:
            field["required"] = True
        if "enum" in sub:
            field["options"] = [{"value": v, "label": v} for v in sub["enum"]]
        ml = sub.get("maxLength")
        if isinstance(ml, int):
            field["maxLength"] = ml
        fields.append(field)
    return fields


def build_grid_columns(response_schema: dict) -> list:
    """response DTO 의 property 에서 grid.columns 를 추출한다.

    휴리스틱: 큰 본문(content string)은 목록에서 제외한다.
    """
    cols = []
    for name, sub in response_schema.get("properties", {}).items():
        if name == "content" and sub.get("type") == "string":
            continue
        col = {
            "field": name,
            "label": sub.get("description") or name,
            "type": detect_field_type(name, sub),
        }
        cols.append(col)
    return cols


def build_meta(args, req_schema: dict, res_schema: dict) -> dict:
    """필수 3축을 포함한 PageMeta DRAFT 골격을 만든다."""
    return {
        "id": f"{args.group_id}-v{args.major}-{args.minor}",
        "title": args.title,
        "systemType": args.system_type,
        "packageType": args.package_type,
        "groupId": args.group_id,
        "majorVersion": args.major,
        "minorVersion": args.minor,
        "metaStatus": "DRAFT",
        "api": args.api,
        "grid": {"columns": build_grid_columns(res_schema)},
        "form": {"layout": "two-column", "fields": build_form_fields(req_schema)},
        "actions": [{"id": "create", "label": "등록", "type": "dialog-form"}],
    }


def build_insert_sql(meta: dict, args) -> str:
    """page_meta INSERT SQL (멱등 ON CONFLICT DO NOTHING)."""
    meta_inner = {
        k: v for k, v in meta.items()
        if k in {"api", "grid", "form", "detail", "actions"}
    }
    body_json = json.dumps(meta_inner, ensure_ascii=False)
    body_json_escaped = body_json.replace("'", "''")
    title_escaped = args.title.replace("'", "''")
    return (
        "INSERT INTO page_meta (id, title, system_type, package_type, group_id,\n"
        "                       major_version, minor_version, meta_status, meta_json)\n"
        f"VALUES ('{meta['id']}', '{title_escaped}', '{args.system_type}', '{args.package_type}',\n"
        f"        '{args.group_id}', {args.major}, {args.minor}, 'DRAFT',\n"
        f"        '{body_json_escaped}'::jsonb)\n"
        "ON CONFLICT (id) DO NOTHING;\n"
    )


def main():
    p = argparse.ArgumentParser(description="OpenAPI → PageMeta DRAFT JSON / INSERT SQL")
    p.add_argument("--openapi", required=True)
    p.add_argument("--request-dto", required=True)
    p.add_argument("--response-dto", required=True)
    p.add_argument("--group-id", required=True)
    p.add_argument("--title", required=True)
    p.add_argument("--system-type", required=True, choices=sorted(SYSTEM_TYPES))
    p.add_argument("--package-type", required=True, choices=sorted(PACKAGE_TYPES))
    p.add_argument("--major", type=int, required=True)
    p.add_argument("--minor", type=int, required=True)
    p.add_argument("--api", required=True)
    p.add_argument("--output")
    args = p.parse_args()

    if not args.group_id:
        sys.exit("group-id must be non-empty")
    if not args.api:
        sys.exit("api must be non-empty")
    if args.major < 1 or args.minor < 1:
        sys.exit("major/minor must be >= 1")

    spec = json.loads(Path(args.openapi).read_text("utf-8"))
    schemas = spec.get("components", {}).get("schemas", {})
    req = schemas.get(args.request_dto)
    res = schemas.get(args.response_dto)
    if not req:
        sys.exit(f"request DTO 없음: {args.request_dto}")
    if not res:
        sys.exit(f"response DTO 없음: {args.response_dto}")

    meta = build_meta(args, req, res)

    print(json.dumps(meta, ensure_ascii=False, indent=2))

    if args.output:
        out = Path(args.output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(build_insert_sql(meta, args), "utf-8")


if __name__ == "__main__":
    main()
