# Step 3: e2e-with-itg-ticket-meta

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "핵심 설계 사상" (메타 모델 3축), "절대 규칙" (`@Schema(example)` 민감정보 금지)
- `/docs/PRD.md` — §9 M3 마일스톤 (ITSM 티켓 모듈 e2e)
- `/docs/ADR.md` — ADR-004 (No-code: itg-ticket 메타 한 건이 화면을 만든다), ADR-006 (DRAFT 노출 차단·복사본 DRAFT)
- `/docs/ARCHITECTURE.md` — §3-4 PageMeta JSON 본문 예시 (특히 itg-ticket-v1-2 의 grid/form/actions 구조)
- `/phases/3-itsm-ticket-backend/step0~2.md` — Ticket 전체 산출물
- `/backend/E2E_REPORT.md` (M1) — E2E 보고서 형식 참고
- `/backend/openapi/itg-api-spec.json` — 직전 OpenAPI 사양 (변경 전후 비교)

## 작업

이 step 의 목적은 **`itg-ticket` PUBLISHED 메타와 샘플 티켓을 시드하고, cURL 시나리오로 ITSM 티켓 모듈 end-to-end 동작을 검증하며, `backend/E2E_REPORT_PHASE3.md` 보고서·OpenAPI 산출물을 갱신하는 것**이다. 운영 코드 변경은 없다.

### 1. 시드 SQL — `sql/init/03_itg_ticket_meta.sql`

`/Users/mwjeon/Projects/ai-work/harness_framework_ITSM/sql/init/03_itg_ticket_meta.sql`. 컨테이너 최초 기동 시 `02_ticket.sql` 다음에 자동 실행. 멱등성: `ON CONFLICT DO NOTHING`.

```sql
-- itg-ticket-v1-1 PUBLISHED 메타 (ITSM/PACKAGE)
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-ticket-v1-1', 'ITSM 티켓 관리', 'ITSM', 'PACKAGE', 'itg-ticket',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/tickets",
  "grid": {
    "columns": [
      { "field": "ticketNo",   "label": "티켓번호", "type": "text",     "width": 140, "pinned": "left" },
      { "field": "title",      "label": "제목",     "type": "text",     "flex": 1 },
      { "field": "status",     "label": "상태",     "type": "status",   "width": 120 },
      { "field": "priority",   "label": "우선순위", "type": "priority", "width": 110 },
      { "field": "assigneeId", "label": "담당자",   "type": "text",     "width": 140 },
      { "field": "createdAt",  "label": "등록일",   "type": "date",     "width": 120 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "title",     "label": "제목",     "type": "text",     "required": true, "span": 2 },
      { "name": "category",  "label": "분류",     "type": "select",   "options": [
          { "value": "BUG", "label": "버그" },
          { "value": "REQ", "label": "요청" },
          { "value": "QNA", "label": "문의" }
      ] },
      { "name": "priority",  "label": "우선순위", "type": "radio",    "options": [
          { "value": "LOW",      "label": "낮음" },
          { "value": "MEDIUM",   "label": "보통" },
          { "value": "HIGH",     "label": "높음" },
          { "value": "CRITICAL", "label": "긴급" }
      ] },
      { "name": "assigneeId","label": "담당자",   "type": "user-picker" },
      { "name": "content",   "label": "내용",     "type": "textarea", "span": 2 }
    ]
  },
  "actions": [
    { "id": "create", "label": "등록", "type": "dialog-form" }
  ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;
```

> `ON CONFLICT DO NOTHING` 으로 멱등성 확보 — 기존 컨테이너에 수동 적용 시 중복 INSERT 안 함.

> 컬럼 `assigneeId`·`createdAt`·`ticketNo` 는 `TicketSummary` Record 의 필드명과 정확히 일치해야 함 (다음 phase 의 DynamicGrid 가 `accessorKey` 로 사용).

### 2. 샘플 티켓 5건 시드 — `sql/init/04_sample_tickets.sql`

```sql
-- 샘플 티켓 5건. ticket_no 는 ITSM-{id5} 패턴이지만 시드는 명시.
INSERT INTO ticket (ticket_no, title, content, priority, status, category, assignee_id)
VALUES
  ('ITSM-SAMPLE-001', '샘플 티켓 1 — 로그인 오류',     '재현 절차 …',  'HIGH',     'OPEN',        'BUG', 'assignee-sample-1'),
  ('ITSM-SAMPLE-002', '샘플 티켓 2 — 기능 요청',       '신규 메뉴 …',  'MEDIUM',   'IN_PROGRESS', 'REQ', 'assignee-sample-2'),
  ('ITSM-SAMPLE-003', '샘플 티켓 3 — 문의',           '운영 시간 …',  'LOW',      'RESOLVED',    'QNA', 'assignee-sample-1'),
  ('ITSM-SAMPLE-004', '샘플 티켓 4 — 장애 보고',       '서비스 영향 …','CRITICAL', 'OPEN',        'BUG', NULL),
  ('ITSM-SAMPLE-005', '샘플 티켓 5 — 종료된 항목',     '완료 …',       'MEDIUM',   'CLOSED',      'REQ', 'assignee-sample-3')
ON CONFLICT (ticket_no) DO NOTHING;

-- CLOSED 시드의 closed_at 보정
UPDATE ticket SET closed_at = created_at + INTERVAL '1 hour'
 WHERE ticket_no = 'ITSM-SAMPLE-005' AND closed_at IS NULL;
```

> 시드 ticket_no 는 `ITSM-SAMPLE-001~005` (`Service.create` 가 부여하는 `ITSM-{id5}` 와 구분). 운영 코드는 SAMPLE 패턴을 인식하지 않는다 — 단순 표시용.

### 3. E2E 시나리오 (cURL)

```bash
# 0) 인프라
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d

# 1) 스키마/시드 적용 (기존 컨테이너 — 자동 실행 안 됨, 수동)
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/02_ticket.sql            >/dev/null 2>&1 || true
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/03_itg_ticket_meta.sql
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/04_sample_tickets.sql

# 2) 백엔드 부팅
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
BE_PID=$!
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'

# 3) 메타 확인
curl -fsS http://localhost:8080/api/meta/active/itg-ticket | grep -q '"id":"itg-ticket-v1-1"'

# 4) 시드 티켓 목록 확인 (5건)
curl -fsS "http://localhost:8080/api/tickets?page=0&size=20" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); \
                 assert d['data']['totalElements'] >= 5, d"

# 5) 필터 — status=OPEN
curl -fsS "http://localhost:8080/api/tickets?status=OPEN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); \
                 statuses={r['status'] for r in d['data']['content']}; \
                 assert statuses == {'OPEN'}, statuses"

# 6) 신규 생성 (id=다음 시퀀스, ticket_no=ITSM-{id5})
NEW_ID=$(curl -fsS -X POST http://localhost:8080/api/tickets \
  -H 'Content-Type: application/json' \
  -d '{"title":"E2E 생성 샘플","content":"본문","priority":"HIGH","category":"BUG","assigneeId":"assignee-sample-9"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['id'])")
test -n "$NEW_ID"

# 7) 단건 조회 + ticket_no 패턴
curl -fsS "http://localhost:8080/api/tickets/$NEW_ID" | python3 -c \
  "import json,sys,re; d=json.load(sys.stdin)['data']; \
   assert re.fullmatch(r'ITSM-\\d{5}', d['ticketNo']), d['ticketNo']; \
   assert d['status']=='OPEN', d"

# 8) 상태 전이 OPEN → IN_PROGRESS
curl -fsS -X PATCH "http://localhost:8080/api/tickets/$NEW_ID/status" \
  -H 'Content-Type: application/json' -d '{"next":"IN_PROGRESS"}' \
  | grep -q '"status":"IN_PROGRESS"'

# 9) IN_PROGRESS → OPEN 거부 (400, errorCode INVALID_REQUEST)
test "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH \
  http://localhost:8080/api/tickets/$NEW_ID/status \
  -H 'Content-Type: application/json' -d '{"next":"OPEN"}')" = "400"

# 10) IN_PROGRESS → CLOSED
curl -fsS -X PATCH "http://localhost:8080/api/tickets/$NEW_ID/status" \
  -H 'Content-Type: application/json' -d '{"next":"CLOSED"}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 assert d['status']=='CLOSED', d; \
                 assert d['closedAt'] is not None, d"

# 11) CLOSED 후 update 거부 (400)
test "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH \
  http://localhost:8080/api/tickets/$NEW_ID \
  -H 'Content-Type: application/json' -d '{"title":"수정 시도"}')" = "400"

# 12) CLOSED 후 status 전이 거부 (400)
test "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH \
  http://localhost:8080/api/tickets/$NEW_ID/status \
  -H 'Content-Type: application/json' -d '{"next":"IN_PROGRESS"}')" = "400"

# 13) OpenAPI 산출 갱신
mkdir -p openapi
curl -fsS http://localhost:8080/v3/api-docs      > openapi/itg-api-spec.json
curl -fsS http://localhost:8080/v3/api-docs.yaml > openapi/itg-api-spec.yaml

# 14) 정리 — bootRun kill (시드는 다음 phase 가 사용하므로 유지)
kill $BE_PID
```

> 시드는 다음 phase (frontend 통합) 가 사용하므로 **DELETE 하지 않는다**. 보고서에 "시드 유지" 명시.

### 4. 결과 보고서 — `backend/E2E_REPORT_PHASE3.md`

다음 섹션 포함:

- 환경 (Spring Boot 버전·Java·Postgres·SpringDoc).
- 검증 메타 (`itg-ticket-v1-1` PUBLISHED) + 시드 티켓 5건 표.
- 시나리오 결과 표 (위 §3 의 14 단계, 기대 / 결과 / 비고).
- 핵심 검증 사실:
  - PRD M3 ITSM 모듈 백엔드 e2e 완료.
  - 상태 전이 매트릭스 검증 (OPEN→IN_PROGRESS→CLOSED 통과, CLOSED→IN_PROGRESS 거부, IN_PROGRESS→OPEN 역방향 거부).
  - CLOSED 가드 (update·status·priority·assign 모두 400).
  - `ticket_no` 자동 부여 (`ITSM-\d{5}`).
  - 메타 `itg-ticket` PUBLISHED 확인 — 다음 phase 의 DynamicPage 가 `/itsm` 진입 시 이 메타로 자동 렌더.
- 한계 (다음 phase 범위): 프런트 통합·실 submit 동작·사용자 검색 UI.
- 산출물: SQL 시드 3 종, OpenAPI JSON/YAML, 본 보고서.

### 5. 코드 변경 범위

- `sql/init/02_ticket.sql` — step 0 산출물 (그대로).
- `sql/init/03_itg_ticket_meta.sql` — 신규.
- `sql/init/04_sample_tickets.sql` — 신규.
- `backend/openapi/itg-api-spec.{json,yaml}` — 갱신.
- `backend/E2E_REPORT_PHASE3.md` — 신규.

운영 코드(Entity·Service·Controller 등)는 변경하지 않는다.

## Acceptance Criteria

```bash
# 1) 시드 SQL 파일 존재
test -f /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/sql/init/03_itg_ticket_meta.sql
test -f /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/sql/init/04_sample_tickets.sql

# 2) 위 §3 시나리오 14 단계 모두 통과

# 3) OpenAPI 산출
test -s backend/openapi/itg-api-spec.json
test -s backend/openapi/itg-api-spec.yaml
python3 -c "import json; d=json.load(open('backend/openapi/itg-api-spec.json')); \
            need={'/api/tickets','/api/tickets/{id}','/api/tickets/{id}/status'}; \
            assert need.issubset(d['paths'].keys())"

# 4) 보고서
test -s backend/E2E_REPORT_PHASE3.md
grep -q "itg-ticket-v1-1" backend/E2E_REPORT_PHASE3.md
grep -q "CLOSED" backend/E2E_REPORT_PHASE3.md

# 5) 시드 메타가 DB 에 남아 있어야 함 (다음 phase 가 사용)
docker exec -i itg-postgres psql -U itg -d itgdb -c \
  "SELECT id FROM page_meta WHERE id='itg-ticket-v1-1';" | grep -q "itg-ticket-v1-1"
docker exec -i itg-postgres psql -U itg -d itgdb -c \
  "SELECT count(*) FROM ticket WHERE ticket_no LIKE 'ITSM-SAMPLE-%';" | grep -q "5"
```

## 검증 절차

1. 위 AC + §3 시나리오 14 단계 모두 통과.
2. 아키텍처 체크리스트:
   - `itg-ticket-v1-1` 메타가 PRD/ARCHITECTURE 의 form/grid 구조를 따르는가?
   - 메타의 `api` 가 `/api/tickets` (TicketController 베이스 경로) 와 일치하는가?
   - 컬럼 field 명이 `TicketSummary` 의 필드명과 1:1 일치하는가? (다음 phase 의 그리드 렌더링을 위해)
   - 상태 전이 매트릭스 (OPEN→IN_PROGRESS→CLOSED 허용, IN_PROGRESS→OPEN 거부, CLOSED→어떤 전이도 거부) 가 시나리오로 검증되었는가?
   - CLOSED 가드 (update·priority·assign 모두 400) 가 시나리오로 검증되었는가?
   - OpenAPI 산출물에 ticket 경로 6개 이상 등록?
   - 시드가 정리되지 않고 DB 에 보존되었는가? (다음 phase 사용)
3. step 3 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "sql/init/03_itg_ticket_meta.sql (itg-ticket-v1-1 PUBLISHED, ITSM/PACKAGE, grid 6컬럼 + form 6필드 + create dialog-form) + 04_sample_tickets.sql (5건, OPEN/IN_PROGRESS/RESOLVED/CLOSED 혼합). E2E 시나리오 14 단계 PASS — 상태 매트릭스(OPEN→IN_PROGRESS→CLOSED, IN_PROGRESS→OPEN 거부) + CLOSED 가드(update/priority/assign 400) + ticket_no ITSM-{id5} 자동 부여 + OpenAPI 산출 갱신. backend/E2E_REPORT_PHASE3.md 작성. M3 백엔드 완료. 시드 유지(다음 phase frontend 통합 사용)."`
   - 결함 → `"status": "blocked"`, `"blocked_reason"` 후 중단.

## 금지사항

- 운영 코드 (Entity·Service·Controller·Config) 수정 금지. 이유: 본 step 은 시드·시나리오·보고서. 결함 발견 시 step 0~2 로 되돌린다.
- 시나리오 종료 후 시드(`itg-ticket-v1-1` 메타·`ITSM-SAMPLE-001~005`) DELETE 금지. 이유: 다음 phase (frontend 통합) 가 사용.
- E2E 생성 단계(§3 의 6번)에서 만든 임시 티켓은 닫지 않고 유지해도 무방하지만 다음 phase 와 충돌 회피를 위해 `title` 에 `E2E 생성 샘플` 표식이 있으므로 그대로 둔다.
- 프런트엔드 수정 금지.
- `docker-compose down -v` 금지. 이유: 사용자 다른 작업 영향.
- 새 엔드포인트 추가 금지.
- 보고서에 실 운영 데이터 적지 마라. 가상 샘플 (`assignee-sample-*`, `샘플 티켓`).
- OpenAPI 산출에 `paths` 가 비거나 ticket 경로가 누락되면 step 2 결함 — 그 때는 step 2 로 status 되돌리고 blocked.
