# Step 3: request-type-meta-seeds

## 읽어야 할 파일
- `/CLAUDE.md`·`/docs/PRD.md` §3·`/docs/ARCHITECTURE.md` §15
- `/phases/12-domain-depth-workflow-category/step0~2.md`
- 기존 `sql/init/03_itg_ticket_meta.sql`·`06_itg_asset_meta.sql` (메타 시드 패턴)

## 작업
**요청 유형 5종 + 자산 분류 트리 + 메타 시드 + 워크플로우 정의 시드** 일괄.

### 1. 요청 유형·워크플로우 시드 — `sql/init/15_itsm_seed.sql`

```sql
-- 요청 유형 5종
INSERT INTO ticket_request_type (code, label, form_meta_group_id, default_workflow_code, sla_minutes_default) VALUES
  ('INCIDENT',        '장애',     'itg-ticket-incident',  'WF_INCIDENT_STD',  240),
  ('SERVICE_REQUEST', '서비스 요청','itg-ticket-srvreq',   'WF_SERVICE_STD',   480),
  ('CHANGE',          '변경',     'itg-ticket-change',    'WF_CHANGE_STD',    1440),
  ('PROBLEM',         '문제',     'itg-ticket-problem',   'WF_PROBLEM_STD',   2880),
  ('QNA',             '문의',     'itg-ticket-qna',       'WF_QNA_STD',       480)
ON CONFLICT (code) DO NOTHING;

-- 워크플로우 정의 (steps JSONB)
INSERT INTO workflow_definition (code, name, version, steps) VALUES
  ('WF_INCIDENT_STD', '장애 표준 워크플로우', 1, '
   [{"index":0,"name":"접수",      "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":30, "allowed_actions":["FORWARD","COMPLETE"]},
    {"index":1,"name":"원인 분석", "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":120,"allowed_actions":["COMPLETE","REJECT"]},
    {"index":2,"name":"해결",      "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":480,"allowed_actions":["COMPLETE"]},
    {"index":3,"name":"종결 확인","assignee_role_code":"ROLE_USER",       "sla_minutes":null,"allowed_actions":["CONFIRM","REOPEN"]}]'::jsonb),
  ('WF_SERVICE_STD', '서비스 요청 표준', 1, '
   [{"index":0,"name":"접수",       "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":60, "allowed_actions":["FORWARD","COMPLETE"]},
    {"index":1,"name":"승인",       "assignee_role_code":"ROLE_TEAM_LEAD", "sla_minutes":240,"allowed_actions":["APPROVE","REJECT"]},
    {"index":2,"name":"처리",       "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":480,"allowed_actions":["COMPLETE"]},
    {"index":3,"name":"종결 확인","assignee_role_code":"ROLE_USER",       "sla_minutes":null,"allowed_actions":["CONFIRM","REOPEN"]}]'::jsonb),
  ('WF_CHANGE_STD',  '변경 관리 표준', 1, '
   [{"index":0,"name":"제안",       "assignee_role_code":"ROLE_USER",       "sla_minutes":null,"allowed_actions":["COMPLETE"]},
    {"index":1,"name":"1차 검토",   "assignee_role_code":"ROLE_TEAM_LEAD",  "sla_minutes":480,"allowed_actions":["APPROVE","REJECT"]},
    {"index":2,"name":"CAB 승인",   "assignee_role_code":"ROLE_ADMIN",      "sla_minutes":1440,"allowed_actions":["APPROVE","REJECT"]},
    {"index":3,"name":"적용",       "assignee_role_code":"ROLE_IT_SUPPORT", "sla_minutes":480,"allowed_actions":["COMPLETE"]},
    {"index":4,"name":"종결",       "assignee_role_code":"ROLE_USER",       "sla_minutes":null,"allowed_actions":["CONFIRM"]}]'::jsonb),
  ('WF_PROBLEM_STD', '문제 관리 표준', 1, '
   [{"index":0,"name":"등록",       "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":120, "allowed_actions":["FORWARD"]},
    {"index":1,"name":"근본 원인",  "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":1440,"allowed_actions":["COMPLETE"]},
    {"index":2,"name":"해결책 검토","assignee_role_code":"ROLE_TEAM_LEAD", "sla_minutes":1440,"allowed_actions":["APPROVE","REJECT"]},
    {"index":3,"name":"종결",       "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":null, "allowed_actions":["COMPLETE"]}]'::jsonb),
  ('WF_QNA_STD',     '문의 표준', 1, '
   [{"index":0,"name":"접수",       "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":60, "allowed_actions":["COMPLETE"]},
    {"index":1,"name":"종결 확인","assignee_role_code":"ROLE_USER",       "sla_minutes":null,"allowed_actions":["CONFIRM","REOPEN"]}]'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- ROLE_TEAM_LEAD 추가 (시드 9 의 권한·역할 확장)
INSERT INTO role (code, name, description) VALUES
  ('ROLE_TEAM_LEAD', '팀 리더', '1차 승인자')
ON CONFLICT (code) DO NOTHING;
```

### 2. 요청 유형별 폼 메타 시드 — `sql/init/16_request_type_metas.sql`

요청 유형 5종 각각의 PageMeta DRAFT INSERT + 즉시 PUBLISHED. 본 step 에서는 5개 메타를 일괄 시드:

- `itg-ticket-incident-v1-1` (장애) — title·priority·assigneeId·affectedSystem·content
- `itg-ticket-srvreq-v1-1` (서비스 요청) — title·category·priority·dueDate·content
- `itg-ticket-change-v1-1` (변경) — title·impact·riskLevel·plannedStart·plannedEnd·rollback·content
- `itg-ticket-problem-v1-1` (문제) — title·linkedIncidents·priority·content
- `itg-ticket-qna-v1-1` (문의) — title·category·content

각 메타는 `requestTypeCode` 와 `workflowDefinitionCode` 를 metaJson 에 포함 (ARCHITECTURE §3-4).

각 INSERT 는 `ON CONFLICT (id) DO NOTHING`.

### 3. 자산 분류 트리 시드 — `sql/init/17_asset_categories.sql`

```sql
INSERT INTO asset_category (code, label, parent_code, path, form_meta_group_id, sort_order) VALUES
  ('HW',         '하드웨어',  NULL,         '/HW/',                  NULL,                       10),
  ('HW_LAPTOP',  '노트북',    'HW',         '/HW/HW_LAPTOP/',        'itg-asset-hw-laptop',      11),
  ('HW_SERVER',  '서버',      'HW',         '/HW/HW_SERVER/',        'itg-asset-hw-server',      12),
  ('HW_MONITOR', '모니터',    'HW',         '/HW/HW_MONITOR/',       'itg-asset-hw-monitor',     13),
  ('SW',         '소프트웨어','NULL',        '/SW/',                  NULL,                       20),
  ('SW_LICENSE', '라이선스',  'SW',         '/SW/SW_LICENSE/',       'itg-asset-sw-license',     21),
  ('CONTRACT',   '계약',      NULL,         '/CONTRACT/',            'itg-asset-contract',       30),
  ('SERVICE',    '서비스',    NULL,         '/SERVICE/',             'itg-asset-service',        40)
ON CONFLICT (code) DO NOTHING;
```

### 4. 자산 분류별 폼 메타 시드 — `sql/init/18_category_metas.sql`

분류별 폼 메타 5종 (HW_LAPTOP / HW_SERVER / HW_MONITOR / SW_LICENSE / CONTRACT). 각 분류 특화 필드:

- `itg-asset-hw-laptop`: name·model·serialNo·cpu·ramGb·storage·assigneeId·location·acquiredAt
- `itg-asset-hw-server`: name·model·serialNo·cpu·ramGb·storage·os·ip(가상 192.0.2.x)·dataCenter
- `itg-asset-hw-monitor`: name·model·serialNo·size·resolution·assigneeId
- `itg-asset-sw-license`: name·vendor·licenseKey(가상)·seats·startDate·endDate
- `itg-asset-contract`: name·vendor·contractNo(가상 SAMPLE-)·startDate·endDate·amount

모든 `@Schema(example)`·시드 값은 가상 샘플 (ADR-011).

## Acceptance Criteria
```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
test -f sql/init/15_itsm_seed.sql
test -f sql/init/16_request_type_metas.sql
test -f sql/init/17_asset_categories.sql
test -f sql/init/18_category_metas.sql

docker-compose up -d
for f in sql/init/15_*.sql sql/init/16_*.sql sql/init/17_*.sql sql/init/18_*.sql; do
  docker exec -i itg-postgres psql -U itg -d itgdb < "$f"
done

docker exec -i itg-postgres psql -U itg -d itgdb -c "SELECT count(*) FROM ticket_request_type;" | grep -q -E "[5-9]"
docker exec -i itg-postgres psql -U itg -d itgdb -c "SELECT count(*) FROM workflow_definition;" | grep -q -E "[5-9]"
docker exec -i itg-postgres psql -U itg -d itgdb -c "SELECT count(*) FROM asset_category;"     | grep -q "8"
docker exec -i itg-postgres psql -U itg -d itgdb -c \
  "SELECT id FROM page_meta WHERE group_id LIKE 'itg-ticket-%' OR group_id LIKE 'itg-asset-%';" | grep -c "v1-1" | xargs -I{} test {} -ge 8

# 백엔드 부팅 후 active 메타 조회
cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10
ADMIN=$(curl -fsS -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-sample-1234"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])")
curl -fsS -H "Authorization: Bearer $ADMIN" http://localhost:8080/api/meta/active/itg-ticket-incident | grep -q PUBLISHED
curl -fsS -H "Authorization: Bearer $ADMIN" http://localhost:8080/api/meta/active/itg-asset-hw-laptop | grep -q PUBLISHED
kill %1
```

## 금지사항
- 메타 시드에 실 운영 데이터 금지. licenseKey·serialNo·IP 모두 SAMPLE 또는 RFC 5737.
- 시드 종료 후 정리 금지 — 다음 step 들이 사용.
- workflow_definition.steps 의 assignee_role_code 가 존재하지 않는 role.code 금지 — ROLE_USER/ROLE_IT_SUPPORT/ROLE_TEAM_LEAD/ROLE_ADMIN 만.
- ROLE_TEAM_LEAD 외 새 역할 임의 추가 금지.
- 운영 코드 수정 금지.
- 프런트엔드 수정 금지.
