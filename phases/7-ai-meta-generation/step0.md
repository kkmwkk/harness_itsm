# Step 0: meta-generator-cli

## 읽어야 할 파일

- `/CLAUDE.md` — 핵심 설계 사상 (메타 3축), 절대 규칙
- `/docs/ARCHITECTURE.md` — §5 필드 타입 매핑표, §10 AI 메타 자동 생성 파이프라인
- `/docs/PRD.md` — §5-4 AI 메타 자동 생성, §9 M5
- `/docs/ADR.md` — ADR-004·ADR-005·ADR-006
- `/sql/init/03_itg_ticket_meta.sql`·`06_itg_asset_meta.sql` — 메타 시드 형태 (목표 산출물)
- `/backend/openapi/itg-api-spec.json` — 입력 데이터 (실제 사용 시 입력)
- `/scripts/execute.py` — Python 코드 스타일 참고

## 작업

이 step 의 목적은 **OpenAPI JSON + DTO 이름을 입력받아 PageMeta JSON 골격을 자동 생성하는 Python CLI 를 만들고, 단위 테스트로 매핑 정확도를 보증하는 것**이다. Claude Code 와의 통합 워크플로우는 step 2 의 책임.

### 1. CLI 스크립트 — `scripts/generate_meta.py`

#### 인터페이스

```
python3 scripts/generate_meta.py \
  --openapi backend/openapi/itg-api-spec.json \
  --request-dto TicketCreateRequest \
  --response-dto TicketSummary \
  --group-id itg-ticket \
  --title "ITSM 티켓 관리" \
  --system-type ITSM \
  --package-type PACKAGE \
  --major 1 --minor 1 \
  --api /api/tickets \
  [--output sql/init/_generated/itg-ticket-v1-1.sql]
```

출력:
- stdout: PageMeta JSON (pretty)
- `--output` 지정 시: `page_meta INSERT SQL` 파일 (ON CONFLICT DO NOTHING)

#### 필수 검증

생성 시점에 다음을 검사하여 누락 시 즉시 비정상 종료:
- `--system-type` ∈ {ITSM, ITAM, PMS, COMMON, SYSTEM}
- `--package-type` ∈ {PACKAGE, CUSTOM}
- `--group-id` non-empty
- `--major`·`--minor` >= 1
- `--api` non-empty
- OpenAPI JSON 에서 `--request-dto` / `--response-dto` 가 components.schemas 에 존재

#### 매핑 규칙 (ARCHITECTURE §5)

OpenAPI 의 schema property `type`·`format` → FieldType:

| OpenAPI type/format | FieldType |
|---|---|
| `string` (default) | `text` |
| `string` + `enum` | `select` (options 자동 채움) |
| `string` + `format: date` | `date` |
| `string` + `format: date-time` | `date` |
| `string` + `format: uuid` | `text` |
| `string` + `format: email`·`uri` | `text` |
| `integer`·`number` | `number` |
| `boolean` | `checkbox` |
| 그 외 객체 / 배열 | `textarea` (fallback) |

추가 휴리스틱:
- property 이름이 `priority` → `priority`
- property 이름이 `status` → `status`
- property 이름이 `*ContentBody`·`*Description` 또는 maxLength > 500 → `textarea`
- property 이름이 `assigneeId`·`ownerId` 같은 `*Id` 패턴 + 코드값이 사용자라면 `user-picker` (휴리스틱)

#### 필수 컬럼

생성 결과 PageMeta 는 반드시 다음을 포함:
- `id` = `"{groupId}-v{major}-{minor}"`
- `title`, `systemType`, `packageType`, `groupId`, `majorVersion`, `minorVersion`
- `metaStatus` = `"DRAFT"` (항상 — 신규 생성은 ADR-006)
- `api`
- `grid.columns` (response DTO 의 property 에서 추출)
- `form.fields` (request DTO 의 property 에서 추출)
- `actions` = `[{ "id": "create", "label": "등록", "type": "dialog-form" }]` (기본)

#### 코드 구조

```python
#!/usr/bin/env python3
"""generate_meta.py — OpenAPI → PageMeta DRAFT JSON / INSERT SQL"""

import argparse, json, sys
from pathlib import Path

SYSTEM_TYPES  = {"ITSM","ITAM","PMS","COMMON","SYSTEM"}
PACKAGE_TYPES = {"PACKAGE","CUSTOM"}
FIELD_TYPE_BY_OPENAPI = {
    ("string", None):          "text",
    ("string", "date"):        "date",
    ("string", "date-time"):   "date",
    ("integer", None):         "number",
    ("number",  None):         "number",
    ("boolean", None):         "checkbox",
}
LONG_TEXT_THRESHOLD = 500


def detect_field_type(name: str, schema: dict) -> str:
    if name in {"priority"}: return "priority"
    if name in {"status"}:   return "status"
    if "enum" in schema:     return "select"
    t   = schema.get("type", "string")
    fmt = schema.get("format")
    if name.endswith("Id") and name not in {"id"}:
        return "user-picker"
    if t == "string":
        if schema.get("maxLength", 0) > LONG_TEXT_THRESHOLD: return "textarea"
        if "Description" in name or "Content" in name or "Memo" in name: return "textarea"
    return FIELD_TYPE_BY_OPENAPI.get((t, fmt), "text")


def build_form_fields(request_schema: dict) -> list[dict]:
    fields = []
    required = set(request_schema.get("required", []))
    for name, sub in request_schema.get("properties", {}).items():
        field = {"name": name, "label": sub.get("description") or name,
                 "type": detect_field_type(name, sub)}
        if name in required: field["required"] = True
        if "enum" in sub:
            field["options"] = [{"value": v, "label": v} for v in sub["enum"]]
        ml = sub.get("maxLength")
        if isinstance(ml, int): field["maxLength"] = ml
        if sub.get("description") and field["label"] == name:
            field["label"] = sub["description"]
        fields.append(field)
    return fields


def build_grid_columns(response_schema: dict) -> list[dict]:
    cols = []
    for name, sub in response_schema.get("properties", {}).items():
        # 보통 일부 컬럼만 노출 — 휴리스틱: pk·content·createdAt 등은 그대로, 큰 본문은 제외
        if name == "content" and sub.get("type") == "string": continue
        col = {"field": name, "label": sub.get("description") or name,
               "type": detect_field_type(name, sub)}
        cols.append(col)
    return cols


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--openapi", required=True)
    p.add_argument("--request-dto", required=True)
    p.add_argument("--response-dto", required=True)
    p.add_argument("--group-id", required=True)
    p.add_argument("--title",   required=True)
    p.add_argument("--system-type",   required=True, choices=sorted(SYSTEM_TYPES))
    p.add_argument("--package-type",  required=True, choices=sorted(PACKAGE_TYPES))
    p.add_argument("--major", type=int, required=True)
    p.add_argument("--minor", type=int, required=True)
    p.add_argument("--api",   required=True)
    p.add_argument("--output")
    args = p.parse_args()

    if args.major < 1 or args.minor < 1:
        sys.exit("major/minor must be >= 1")

    spec = json.loads(Path(args.openapi).read_text("utf-8"))
    schemas = spec.get("components", {}).get("schemas", {})
    req = schemas.get(args.request_dto)
    res = schemas.get(args.response_dto)
    if not req: sys.exit(f"request DTO 없음: {args.request_dto}")
    if not res: sys.exit(f"response DTO 없음: {args.response_dto}")

    meta = {
        "id":           f"{args.group_id}-v{args.major}-{args.minor}",
        "title":        args.title,
        "systemType":   args.system_type,
        "packageType":  args.package_type,
        "groupId":      args.group_id,
        "majorVersion": args.major,
        "minorVersion": args.minor,
        "metaStatus":   "DRAFT",
        "api":          args.api,
        "grid":         {"columns": build_grid_columns(res)},
        "form":         {"layout": "two-column", "fields": build_form_fields(req)},
        "actions":      [{"id":"create","label":"등록","type":"dialog-form"}],
    }

    json_text = json.dumps(meta, ensure_ascii=False, indent=2)
    print(json_text)

    if args.output:
        out = Path(args.output)
        out.parent.mkdir(parents=True, exist_ok=True)
        # page_meta INSERT SQL (멱등 ON CONFLICT)
        # meta_json 은 JSONB 캐스트
        meta_for_sql = dict(meta)
        # 본문 그대로 jsonb
        meta_inner = {k:v for k,v in meta.items()
                       if k in {"api","grid","form","detail","actions"}}
        body_json = json.dumps(meta_inner, ensure_ascii=False)
        body_json_escaped = body_json.replace("'", "''")
        sql = (
            f"INSERT INTO page_meta (id, title, system_type, package_type, group_id,\n"
            f"                       major_version, minor_version, meta_status, meta_json)\n"
            f"VALUES ('{meta['id']}', '{args.title}', '{args.system_type}', '{args.package_type}',\n"
            f"        '{args.group_id}', {args.major}, {args.minor}, 'DRAFT',\n"
            f"        '{body_json_escaped}'::jsonb)\n"
            f"ON CONFLICT (id) DO NOTHING;\n"
        )
        out.write_text(sql, "utf-8")

if __name__ == "__main__":
    main()
```

### 2. 단위 테스트 — `scripts/test_generate_meta.py`

`unittest` 또는 `pytest`. **`pytest` 권장** (간결).

```bash
pip3 install pytest  # 시스템 또는 venv 에 설치 — 미설치 환경이면 unittest 로 작성
```

> 만약 pip 권한 문제로 pytest 설치 어려우면 `unittest` 사용. 본 step 의 테스트가 단순하므로 둘 다 가능.

테스트 케이스 (이름은 한글 허용 — pytest function name 또는 unittest method name):

1. `detect_field_type_priority_name_은_priority` — `detect_field_type("priority", {"type":"string"})` == `"priority"`.
2. `detect_field_type_status_name_은_status`.
3. `detect_field_type_enum_string_은_select`.
4. `detect_field_type_date_format_은_date`.
5. `detect_field_type_number_integer_은_number`.
6. `detect_field_type_boolean_은_checkbox`.
7. `detect_field_type_assigneeId_은_user-picker`.
8. `detect_field_type_long_string_은_textarea` — maxLength > 500.
9. `build_form_fields_required_표시`.
10. `build_form_fields_enum_options_생성`.
11. `build_grid_columns_content_제외`.
12. `main_metaStatus_항상_DRAFT` — 산출물 stdout JSON parse 후 `metaStatus == "DRAFT"`.
13. `main_id_패턴_{groupId}-v{major}-{minor}`.
14. `main_invalid_system_type_종료`.

테스트는 임시 OpenAPI 파일을 만들어 사용 (`tempfile`).

### 3. 수동 검증 (실 OpenAPI 로)

`backend/openapi/itg-api-spec.json` 의 `TicketCreateRequest`·`TicketSummary` 로 메타 생성 → 출력이 `sql/init/03_itg_ticket_meta.sql` 의 형태와 (form.fields·grid.columns 수준에서) 비슷한지 확인.

```bash
python3 scripts/generate_meta.py \
  --openapi backend/openapi/itg-api-spec.json \
  --request-dto TicketCreateRequest --response-dto TicketSummary \
  --group-id itg-ticket --title "ITSM 티켓 관리" \
  --system-type ITSM --package-type PACKAGE \
  --major 1 --minor 9 --api /api/tickets > /tmp/itg-ticket-generated.json
# /tmp/itg-ticket-generated.json 의 form.fields 가 title·priority·content 등을 포함하는지 검증
python3 -c "import json; m=json.load(open('/tmp/itg-ticket-generated.json')); \
            names={f['name'] for f in m['form']['fields']}; \
            assert {'title','priority','content'}.issubset(names), names; \
            assert m['metaStatus']=='DRAFT'"
```

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM

# 1) 파일
test -f scripts/generate_meta.py
test -f scripts/test_generate_meta.py
chmod +x scripts/generate_meta.py

# 2) 단위 테스트
python3 -m pytest scripts/test_generate_meta.py -q 2>&1 || \
  python3 -m unittest scripts.test_generate_meta -v

# 3) 실 OpenAPI 로 생성 동작 확인
python3 scripts/generate_meta.py --help | grep -q openapi
python3 scripts/generate_meta.py \
  --openapi backend/openapi/itg-api-spec.json \
  --request-dto TicketCreateRequest --response-dto TicketSummary \
  --group-id itg-ticket --title "ITSM 티켓 관리" \
  --system-type ITSM --package-type PACKAGE \
  --major 1 --minor 9 --api /api/tickets > /tmp/_meta.json
python3 -c "import json; m=json.load(open('/tmp/_meta.json')); \
            assert m['metaStatus']=='DRAFT'; \
            assert m['id']=='itg-ticket-v1-9'; \
            assert any(f['name']=='title' for f in m['form']['fields'])"

# 4) --output 으로 SQL 파일 생성
python3 scripts/generate_meta.py \
  --openapi backend/openapi/itg-api-spec.json \
  --request-dto TicketCreateRequest --response-dto TicketSummary \
  --group-id itg-ticket --title "ITSM 티켓 관리" \
  --system-type ITSM --package-type PACKAGE \
  --major 1 --minor 9 --api /api/tickets \
  --output /tmp/_generated.sql > /dev/null
grep -q "INSERT INTO page_meta" /tmp/_generated.sql
grep -q "'DRAFT'" /tmp/_generated.sql
grep -q "ON CONFLICT (id) DO NOTHING" /tmp/_generated.sql

# 5) 회귀: 기존 backend 빌드 영향 없음
cd backend && ./gradlew build -x test 2>&1 | tail -3
```

## 검증 절차

1. AC 모두 통과.
2. 아키텍처 체크:
   - 생성된 메타의 `metaStatus` 가 항상 `DRAFT`? (ADR-006)
   - `systemType`·`packageType` Enum 값만 허용? (ADR-005)
   - `id` 패턴이 `{groupId}-v{major}-{minor}`?
   - SQL 산출이 `ON CONFLICT DO NOTHING` 멱등?
   - ARCHITECTURE §5 매핑표(`status`·`priority`·`enum`→`select` 등) 일치?
3. step 0 업데이트:
   - 성공 → `"summary": "scripts/generate_meta.py (OpenAPI JSON + DTO 이름 → PageMeta DRAFT JSON + 선택 --output 으로 INSERT SQL 생성, ARCHITECTURE §5 매핑표 + status/priority/enum/assigneeId 휴리스틱, 필수 인자 검증, metaStatus 항상 DRAFT 강제). 단위 테스트 14 케이스. 실 OpenAPI(TicketCreateRequest/TicketSummary) 로 itg-ticket-v1-9 생성 검증."`

## 금지사항

- `metaStatus` 를 DRAFT 외 값으로 강제 가능하게 만들지 마라. CLI 옵션으로도 노출 금지. ADR-006.
- `--system-type` 의 자유 입력 허용 금지. Enum 값만.
- 외부 LLM API 호출 코드를 이 CLI 에 넣지 마라. 본 step 은 OpenAPI 파싱 + 매핑 규칙. AI 통합은 step 2 의 워크플로우.
- 프런트엔드 코드 수정 금지.
- backend 코드 수정 금지 (단, MetaValidationService 는 step 1 의 책임).
- 생성 산출물(SQL) 을 자동으로 DB 에 적용하지 마라. CLI 는 파일 출력까지만. INSERT 는 사용자가 명시적으로.
- 매핑이 모호한 경우 임의 추정하지 마라 — 휴리스틱은 위 명세에 박힌 규칙만. 그 외는 fallback `text`.
- 운영 코드에 `print(debug)` 잔류 금지.
