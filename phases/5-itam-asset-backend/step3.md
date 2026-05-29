# Step 3: e2e-with-itg-asset-meta

## 읽어야 할 파일

- `/CLAUDE.md` — 핵심 설계 사상·절대 규칙
- `/docs/PRD.md` — §9 M4 (ITAM 자산원장 + 자산 이력 복원), §5-2 활용 사례
- `/docs/ADR.md` — ADR-004·ADR-006·ADR-011
- `/phases/5-itam-asset-backend/step0~2.md` — 산출물 전체
- `/backend/E2E_REPORT_PHASE3.md` — 형식 참고
- `/sql/init/03_itg_ticket_meta.sql` — 메타 시드 패턴

## 작업

이 step 의 목적은 **`itg-asset` PUBLISHED 메타 + 샘플 자산을 시드하고, **자산 이력 메타 보존** 시나리오까지 검증하며, `backend/E2E_REPORT_PHASE5.md` 와 OpenAPI 산출물을 갱신하는 것**이다.

### 1. 시드 SQL — `sql/init/06_itg_asset_meta.sql`

```sql
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-asset-v1-1', 'ITAM 자산원장', 'ITAM', 'PACKAGE', 'itg-asset',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/assets",
  "grid": {
    "columns": [
      { "field": "assetNo",    "label": "자산번호", "type": "text",   "width": 140, "pinned": "left" },
      { "field": "name",       "label": "자산명",   "type": "text",   "flex": 1 },
      { "field": "assetType",  "label": "유형",     "type": "text",   "width": 110 },
      { "field": "status",     "label": "상태",     "type": "status", "width": 110 },
      { "field": "assigneeId", "label": "소유자",   "type": "text",   "width": 140 },
      { "field": "acquiredAt", "label": "취득일",   "type": "date",   "width": 120 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "name",        "label": "자산명",   "type": "text",     "required": true, "span": 2 },
      { "name": "assetType",   "label": "유형",     "type": "select",   "required": true, "options": [
          { "value": "HARDWARE", "label": "하드웨어" },
          { "value": "SOFTWARE", "label": "소프트웨어" },
          { "value": "LICENSE",  "label": "라이선스" },
          { "value": "CONTRACT", "label": "계약" },
          { "value": "SERVICE",  "label": "서비스" }
      ] },
      { "name": "category",    "label": "분류",     "type": "text" },
      { "name": "model",       "label": "모델",     "type": "text" },
      { "name": "serialNo",    "label": "시리얼",   "type": "text" },
      { "name": "assigneeId",  "label": "소유자",   "type": "user-picker" },
      { "name": "location",    "label": "위치",     "type": "text" },
      { "name": "acquiredAt",  "label": "취득일",   "type": "date" },
      { "name": "pageGroupId", "label": "메타 그룹", "type": "text",     "required": true,
        "helpText": "보통 itg-asset 고정. 수정하지 않음." }
    ]
  },
  "actions": [
    { "id": "create", "label": "등록", "type": "dialog-form" }
  ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;
```

### 2. 샘플 자산 5건 시드 — `sql/init/07_sample_assets.sql`

```sql
INSERT INTO asset (asset_no, name, asset_type, status, model, serial_no, category,
                   assignee_id, location, acquired_at, page_meta_id_at_registration)
VALUES
  ('AST-SAMPLE-001', '샘플 노트북 1', 'HARDWARE', 'ACTIVE',   'SAMPLE-MODEL-X1', 'SN-SAMPLE-00001', '노트북',   'assignee-sample-1', '본사 3층', '2026-01-15', 'itg-asset-v1-1'),
  ('AST-SAMPLE-002', '샘플 모니터',   'HARDWARE', 'ACTIVE',   'SAMPLE-MODEL-M2', 'SN-SAMPLE-00002', '모니터',   'assignee-sample-2', '본사 3층', '2026-02-01', 'itg-asset-v1-1'),
  ('AST-SAMPLE-003', '샘플 라이선스', 'LICENSE',  'ACTIVE',   NULL,              NULL,             '라이선스', 'assignee-sample-1', NULL,        '2026-03-10', 'itg-asset-v1-1'),
  ('AST-SAMPLE-004', '샘플 서버',     'HARDWARE', 'STORAGE',  'SAMPLE-SERVER-9', 'SN-SAMPLE-00004', '서버',     NULL,                  '창고',     '2025-11-20', 'itg-asset-v1-1'),
  ('AST-SAMPLE-005', '샘플 폐기 자산','HARDWARE', 'RETIRED',  'SAMPLE-MODEL-X0', 'SN-SAMPLE-00005', '노트북',   'assignee-sample-3', '폐기 보관', '2024-05-01', 'itg-asset-v1-1')
ON CONFLICT (asset_no) DO NOTHING;

UPDATE asset SET disposed_at = '2026-04-01'
 WHERE asset_no = 'AST-SAMPLE-005' AND disposed_at IS NULL;
```

### 3. E2E 시나리오 (cURL)

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d

# 시드 적용 (기존 컨테이너 수동)
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/05_asset.sql 2>/dev/null || true
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/06_itg_asset_meta.sql
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/07_sample_assets.sql

cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
BE_PID=$!
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q UP

# 1) 메타 확인
curl -fsS http://localhost:8080/api/meta/active/itg-asset | grep -q '"id":"itg-asset-v1-1"'

# 2) 자산 목록 (5건+)
curl -fsS "http://localhost:8080/api/assets?page=0&size=20" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert d['data']['totalElements']>=5"

# 3) 상태 필터
curl -fsS "http://localhost:8080/api/assets?status=ACTIVE" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); \
                 st={r['status'] for r in d['data']['content']}; assert st=={'ACTIVE'}, st"

# 4) 신규 자산 생성 → assetNo + pageMetaIdAtRegistration 자동 부여 확인
NEW_ID=$(curl -fsS -X POST http://localhost:8080/api/assets \
  -H 'Content-Type: application/json' \
  -d '{"name":"E2E 생성 자산","assetType":"HARDWARE","model":"SAMPLE-NEW","serialNo":"SN-NEW","category":"노트북","assigneeId":"assignee-sample-9","location":"본사 5층","acquiredAt":"2026-05-29","pageGroupId":"itg-asset"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['id'])")
test -n "$NEW_ID"

# 5) 단건 + assetNo 패턴 + 메타 보존
curl -fsS "http://localhost:8080/api/assets/$NEW_ID" | python3 -c "import json,sys,re; \
   d=json.load(sys.stdin)['data']; \
   assert re.fullmatch(r'AST-\\d{5}', d['assetNo']), d['assetNo']; \
   assert d['pageMetaIdAtRegistration']=='itg-asset-v1-1', d['pageMetaIdAtRegistration']; \
   assert d['status']=='ACTIVE'"

# 6) /registration-meta — 이력 복원
curl -fsS "http://localhost:8080/api/assets/$NEW_ID/registration-meta" \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 assert d['id']=='itg-asset-v1-1', d; \
                 assert d['metaStatus']=='PUBLISHED'"

# 7) 상태 전이 ACTIVE → STORAGE
curl -fsS -X PATCH "http://localhost:8080/api/assets/$NEW_ID/status" \
  -H 'Content-Type: application/json' -d '{"next":"STORAGE"}' \
  | grep -q '"status":"STORAGE"'

# 8) STORAGE → REPLACED 거부 (매트릭스 외)
test "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH \
  http://localhost:8080/api/assets/$NEW_ID/status \
  -H 'Content-Type: application/json' -d '{"next":"REPLACED"}')" = "400"

# 9) STORAGE → ACTIVE 복귀
curl -fsS -X PATCH "http://localhost:8080/api/assets/$NEW_ID/status" \
  -H 'Content-Type: application/json' -d '{"next":"ACTIVE"}' \
  | grep -q '"status":"ACTIVE"'

# 10) ACTIVE → RETIRED + disposedAt set 확인
curl -fsS -X PATCH "http://localhost:8080/api/assets/$NEW_ID/status" \
  -H 'Content-Type: application/json' -d '{"next":"RETIRED"}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 assert d['status']=='RETIRED'; assert d['disposedAt'] is not None"

# 11) RETIRED 후 update 거부
test "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH \
  http://localhost:8080/api/assets/$NEW_ID \
  -H 'Content-Type: application/json' -d '{"name":"수정"}')" = "400"

# 12) RETIRED 후 assign 거부
test "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH \
  http://localhost:8080/api/assets/$NEW_ID/assign \
  -H 'Content-Type: application/json' -d '{"assigneeId":"x"}')" = "400"

# 13) 이력 복원 시나리오 — 메타 v1-2 DRAFT 만들고 publish 하여 v1-1 DEPRECATE
#     → 기존 자산의 /registration-meta 는 여전히 v1-1 (DEPRECATED) 반환
curl -fsS -X POST http://localhost:8080/api/meta/itg-asset-v1-1/copy   # v1-2 DRAFT 생성
curl -fsS -X PATCH http://localhost:8080/api/meta/itg-asset-v1-2/publish   # v1-1 자동 DEPRECATE
curl -fsS "http://localhost:8080/api/assets/$NEW_ID/registration-meta" \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 assert d['id']=='itg-asset-v1-1', d; \
                 assert d['metaStatus']=='DEPRECATED', d  # PRD §5-2 메타 보존 핵심 검증"

# 14) 현재 PUBLISHED 메타는 v1-2 (현재 시점 자산은 v1-2 로 등록)
curl -fsS http://localhost:8080/api/meta/active/itg-asset \
  | grep -q '"id":"itg-asset-v1-2"'

# 15) OpenAPI 산출
mkdir -p openapi
curl -fsS http://localhost:8080/v3/api-docs      > openapi/itg-api-spec.json
curl -fsS http://localhost:8080/v3/api-docs.yaml > openapi/itg-api-spec.yaml

kill $BE_PID

# 시드 보존 — 다음 phase 가 사용
```

### 4. 결과 보고서 — `backend/E2E_REPORT_PHASE5.md`

다음 섹션:
- 환경
- 시드 메타 + 자산 5건 + 메타 v1-2 PUBLISHED (시나리오 13 후)
- 시나리오 15 단계 결과 표
- 핵심 검증 사실:
  - **PRD §5-2 자산 이력 메타 보존**: 자산 등록 시점의 메타 id 가 `pageMetaIdAtRegistration` 에 저장되어, 메타가 v1-1 → DEPRECATED 로 전이되어도 `/registration-meta` 가 그대로 반환 (시나리오 13).
  - 상태 전이 매트릭스 (ACTIVE↔STORAGE, ACTIVE→REPLACED, REPLACED→RETIRED, RETIRED→불가).
  - `assetNo` AST-{id5} 자동 부여.
  - 메타 v1-2 PUBLISHED 전환 시 v1-1 자동 DEPRECATE 검증 (ADR-006).
- 한계 (다음 phase 범위): 프런트 통합·이력 화면 복원 UI.
- 산출물.

## Acceptance Criteria

```bash
# 시드 파일
test -f /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/sql/init/06_itg_asset_meta.sql
test -f /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/sql/init/07_sample_assets.sql

# 위 §3 의 15 단계 모두 통과

# OpenAPI
test -s backend/openapi/itg-api-spec.json
python3 -c "import json; d=json.load(open('backend/openapi/itg-api-spec.json')); \
            need={'/api/assets','/api/assets/{id}','/api/assets/{id}/registration-meta'}; \
            assert need.issubset(d['paths'].keys())"

# 보고서
test -s backend/E2E_REPORT_PHASE5.md
grep -q "pageMetaIdAtRegistration" backend/E2E_REPORT_PHASE5.md
grep -q "PRD §5-2" backend/E2E_REPORT_PHASE5.md

# 시드 잔존 (다음 phase 사용)
docker exec -i itg-postgres psql -U itg -d itgdb -c \
  "SELECT count(*) FROM asset WHERE asset_no LIKE 'AST-SAMPLE-%';" | grep -q "5"
docker exec -i itg-postgres psql -U itg -d itgdb -c \
  "SELECT id, meta_status FROM page_meta WHERE group_id='itg-asset' ORDER BY minor_version;"
# itg-asset-v1-1 DEPRECATED + itg-asset-v1-2 PUBLISHED 표시 기대 (시나리오 13 결과)
```

## 검증 절차

1. AC + §3 시나리오 15 단계 모두 통과.
2. 아키텍처 체크리스트:
   - `itg-asset-v1-1` 메타 form/grid 가 backend 의 AssetCreateRequest·AssetSummary 와 일치하는가?
   - `pageMetaIdAtRegistration` 이 신규 생성 자산에 자동 캡처되는가? (시나리오 5)
   - 메타가 DEPRECATED 로 전이되어도 자산의 등록 메타가 보존되는가? (시나리오 13 — PRD §5-2 핵심)
   - 상태 매트릭스 매트릭스 외 전이 거부?
   - 시드가 정리되지 않고 보존? (다음 phase 사용)
3. step 3 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "06_itg_asset_meta.sql (itg-asset-v1-1 PUBLISHED, ITAM/PACKAGE, grid 6컬럼 + form 9필드 + dialog-form) + 07_sample_assets.sql (5건 ACTIVE/STORAGE/RETIRED). E2E 시나리오 15 단계 PASS — 메타 v1-2 publish 후 v1-1 자동 DEPRECATE + 자산의 pageMetaIdAtRegistration 유지로 PRD §5-2 자산 이력 메타 보존 검증 + 상태 매트릭스(ACTIVE↔STORAGE/REPLACED/RETIRED) + AST-{id5} 자동 부여 + OpenAPI 갱신. backend/E2E_REPORT_PHASE5.md 작성. M4 백엔드 완료. 시드 유지(다음 phase 사용)."`

## 금지사항

- 운영 코드 수정 금지.
- 시드 정리 금지 (DELETE 금지). 다음 phase 가 사용.
- 백엔드 새 엔드포인트 추가 금지.
- 프런트엔드 수정 금지.
- 자동 e2e 테스트(JUnit) 추가 금지. 본 step 은 cURL + 보고서.
- 보고서에 실 운영 데이터 금지.
- 메타 v1-2 publish 후 v1-1 을 ARCHIVED 로 만들지 마라 (이력 보존 일관성). DEPRECATED 자연 전이.
