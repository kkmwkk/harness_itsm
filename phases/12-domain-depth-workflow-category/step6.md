# Step 6: e2e-workflow-and-category

## 읽어야 할 파일
- `/CLAUDE.md`·`/docs/PRD.md` §9 M8·`/docs/ARCHITECTURE.md` §15
- `/phases/12-domain-depth-workflow-category/step0~5.md` 산출물 전체
- `/backend/E2E_REPORT_PHASE9.md` (시드 사용자), `_PHASE10.md`

## 작업
M8 의 e2e — 요청 유형별 워크플로우 + 자산 분류별 원장 동작을 시드 위에서 end-to-end 검증 + 보고서.

### 1. 시나리오 (cURL 자동)

#### A. 환경
```bash
docker-compose up -d
for f in sql/init/11_*.sql sql/init/12_*.sql sql/init/15_*.sql sql/init/16_*.sql sql/init/17_*.sql sql/init/18_*.sql; do
  docker exec -i itg-postgres psql -U itg -d itgdb < "$f"
done
cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10

ADMIN=$(curl -fsS -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-sample-1234"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])")
SUPPORT=$(curl -fsS -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"it-support","password":"it-sample-1234"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])")
USER=$(curl -fsS -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"user-sample-1","password":"user-sample-1234"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])")
```

#### B. ITSM 워크플로우 — 장애 요청 흐름
```bash
# 1) USER 가 장애 요청 생성 — INCIDENT 유형
TID=$(curl -fsS -X POST -H "Authorization: Bearer $USER" -H 'Content-Type: application/json' \
  http://localhost:8080/api/tickets -d '{
    "title":"E2E 장애 — 로그인 안됨", "content":"재현 절차…",
    "priority":"HIGH", "category":"BUG",
    "requestTypeCode":"INCIDENT"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['id'])")

# 2) 워크플로우 인스턴스 자동 생성 확인 — current_step_index=0, 첫 단계명=접수
curl -fsS -H "Authorization: Bearer $USER" "http://localhost:8080/api/workflow-instances/by-ticket/$TID" \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 assert d['currentStepIndex']==0; assert d['status']=='RUNNING'; \
                 assert d['steps'][0]['name']=='접수'"

# 3) USER 는 0단계(접수, ROLE_IT_SUPPORT) 액션 불가 → 400
WID=$(curl -fsS -H "Authorization: Bearer $USER" "http://localhost:8080/api/workflow-instances/by-ticket/$TID" \
       | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['id'])")
test "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $USER" \
   -H 'Content-Type: application/json' -d '{"action":"COMPLETE"}' \
   http://localhost:8080/api/workflow-instances/$WID/step/0/action)" = "400"

# 4) IT_SUPPORT 가 0단계 COMPLETE → 1단계 진입
curl -fsS -X POST -H "Authorization: Bearer $SUPPORT" -H 'Content-Type: application/json' \
  -d '{"action":"COMPLETE","comment":"접수 완료"}' \
  http://localhost:8080/api/workflow-instances/$WID/step/0/action \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; assert d['currentStepIndex']==1"

# 5) IT_SUPPORT 가 1단계 COMPLETE → 2단계 진입
curl -fsS -X POST -H "Authorization: Bearer $SUPPORT" -H 'Content-Type: application/json' \
  -d '{"action":"COMPLETE","comment":"원인 파악"}' \
  http://localhost:8080/api/workflow-instances/$WID/step/1/action \
  | grep -q '"currentStepIndex":2'

# 6) IT_SUPPORT 가 2단계 COMPLETE → 3단계 진입 (종결 확인 = ROLE_USER)
curl -fsS -X POST -H "Authorization: Bearer $SUPPORT" -H 'Content-Type: application/json' \
  -d '{"action":"COMPLETE","comment":"해결됨"}' \
  http://localhost:8080/api/workflow-instances/$WID/step/2/action \
  | grep -q '"currentStepIndex":3'

# 7) USER (요청자) 가 3단계 CONFIRM → 인스턴스 COMPLETED + Ticket CLOSED
curl -fsS -X POST -H "Authorization: Bearer $USER" -H 'Content-Type: application/json' \
  -d '{"action":"CONFIRM","comment":"확인"}' \
  http://localhost:8080/api/workflow-instances/$WID/step/3/action \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 assert d['status']=='COMPLETED'"
curl -fsS -H "Authorization: Bearer $USER" "http://localhost:8080/api/tickets/$TID" \
  | grep -q '"status":"CLOSED"'

# 8) CLOSED 후 액션 거부
test "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $SUPPORT" \
   -H 'Content-Type: application/json' -d '{"action":"COMPLETE"}' \
   http://localhost:8080/api/workflow-instances/$WID/step/3/action)" = "400"
```

#### C. 요청 유형별 폼 메타 분기
```bash
# 9) 변경 요청 유형 — itg-ticket-change 메타
curl -fsS -H "Authorization: Bearer $ADMIN" http://localhost:8080/api/meta/active/itg-ticket-change \
  | grep -q '"id":"itg-ticket-change-v1-1"'

# 10) 장애 메타와 변경 메타의 form.fields 가 서로 다름 (도메인 깊이)
curl -fsS -H "Authorization: Bearer $ADMIN" http://localhost:8080/api/meta/active/itg-ticket-incident \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 names={f['name'] for f in d['metaJson']['form']['fields']}; \
                 assert 'affectedSystem' in names or 'priority' in names"
```

#### D. ITAM 자산 분류 — 분류별 폼 분기
```bash
# 11) 자산 분류 트리
curl -fsS -H "Authorization: Bearer $ADMIN" http://localhost:8080/api/asset-categories/tree \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 codes=set(); \
                 def walk(ns): [codes.add(n['code']) or walk(n.get('children',[])) for n in ns]; \
                 walk(d); \
                 assert {'HW','HW_LAPTOP','SW_LICENSE'}.issubset(codes)"

# 12) HW_LAPTOP 분류로 자산 등록 — itg-asset-hw-laptop 메타로
AID=$(curl -fsS -X POST -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  http://localhost:8080/api/assets -d '{
    "name":"E2E 노트북","categoryCode":"HW_LAPTOP",
    "assetType":"HARDWARE","model":"SAMPLE-MODEL-NEW","serialNo":"SN-SAMPLE-NEW",
    "assigneeId":"user-sample-1","pageGroupId":"itg-asset-hw-laptop"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['id'])")

# 13) 등록 메타 보존 — itg-asset-hw-laptop-v1-1
curl -fsS -H "Authorization: Bearer $ADMIN" "http://localhost:8080/api/assets/$AID/registration-meta" \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 assert d['id']=='itg-asset-hw-laptop-v1-1'"

# 14) 라이프사이클 이벤트 — 이전 (TRANSFER)
curl -fsS -X POST -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"eventType":"TRANSFERRED","payload":{"from":"user-sample-1","to":"user-sample-2"}}' \
  "http://localhost:8080/api/assets/$AID/lifecycle-events" \
  | grep -q '"eventType":"TRANSFERRED"'

# 15) 이벤트 조회
curl -fsS -H "Authorization: Bearer $ADMIN" "http://localhost:8080/api/assets/$AID/lifecycle-events" \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 types={e['eventType'] for e in d}; assert 'TRANSFERRED' in types"
```

#### E. OpenAPI 갱신 + 정리
```bash
mkdir -p backend/openapi
curl -fsS -H "Authorization: Bearer $ADMIN" http://localhost:8080/v3/api-docs > backend/openapi/itg-api-spec.json
kill %1
# 시드 보존 (다음 phase 사용)
```

### 2. 보고서 — `backend/E2E_REPORT_PHASE12.md`

섹션:
- 환경
- 시드 (요청유형 5/워크플로우 5/요청별 메타 5/자산분류 8/분류별 메타 5)
- 시나리오 15 단계 PASS 표
- 핵심 검증 사실:
  - **워크플로우 엔진 MVP 동작**: 장애 요청이 4단계(접수→원인→해결→종결) 자동 진행, 단계별 역할 검증, CLOSED 가드.
  - **요청 유형별 폼 분기**: incident/change 메타 폼이 서로 다른 필드.
  - **자산 분류별 원장**: HW_LAPTOP 자산이 itg-asset-hw-laptop 메타로 등록 + 메타 보존(ADR-006).
  - **라이프사이클 이벤트 추적**: TRANSFERRED 등 이벤트 기록.
  - **권한 분기**: ROLE_USER 가 ROLE_IT_SUPPORT 단계 액션 불가.
- 한계:
  - SLA 초과 알림은 stub (이메일·웹훅 미구현 — 별도 ADR).
  - BPMN 모델러 없음 — Stretch (phase 15).
  - 자산 분류별 column 동적은 본 phase 의 메타 기반. 더 깊은 검색·통계는 별도.
- 산출물.

## Acceptance Criteria
```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
# 위 시나리오 15 단계 통과

test -s backend/E2E_REPORT_PHASE12.md
grep -q "워크플로우" backend/E2E_REPORT_PHASE12.md
grep -q "자산 분류" backend/E2E_REPORT_PHASE12.md
grep -q "INCIDENT\|CHANGE" backend/E2E_REPORT_PHASE12.md

# 시드 보존
docker exec -i itg-postgres psql -U itg -d itgdb -c "SELECT count(*) FROM workflow_definition;" | grep -q -E "[5-9]"
docker exec -i itg-postgres psql -U itg -d itgdb -c "SELECT count(*) FROM asset_category WHERE active;" | grep -q "8"
```

## 금지사항
- 운영 코드 수정 금지. 결함 발견 시 해당 step 으로 되돌린다.
- 시드 정리 금지.
- 시나리오 종료 후 생성된 ticket·asset 데이터는 보존 (다음 phase 사용).
- 보고서에 실 운영 데이터 금지.
- BPMN·Camunda 도입 시도 금지 — phase 15 의 책임.
