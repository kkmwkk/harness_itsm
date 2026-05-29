# Step 2: workflow-and-category-controllers

## 읽어야 할 파일
- `/CLAUDE.md`·`/docs/ARCHITECTURE.md` §7-4·§7-5·`/docs/ADR.md` ADR-009·011
- `/phases/12-domain-depth-workflow-category/step0~1.md`
- 기존 `MetaController`·`TicketController`·`AssetController` (Swagger 패턴)

## 작업
요청유형·워크플로우·자산분류 Controller + `@PreAuthorize` + Swagger + `@WebMvcTest`.

### 1. TicketRequestTypeController — `/api/ticket-request-types`
- `GET` 활성 목록 (인증만), `GET /{code}` 단건
- `POST·PATCH /{code}` (ROLE_ADMIN)

### 2. WorkflowDefinitionController — `/api/workflow-definitions`
- `GET` 목록·`GET /{code}` 단건
- `POST·PATCH /{code}` (ROLE_ADMIN, steps JSONB 검증)

### 3. WorkflowInstanceController — `/api/workflow-instances`
- `GET /by-ticket/{ticketId}` — 인스턴스 + 단계 이력
- `POST /{id}/step/{idx}/action` — body `{ action: 'APPROVE'|..., comment }` → `WorkflowEngineService.executeAction()`
  - 인증된 사용자의 roles 를 Authentication 에서 추출하여 단계 assignee_role 검증
  - 권한 부족·전이 불가 → 400 INVALID_WORKFLOW_ACTION

### 4. TicketController 확장
- `POST /api/tickets` 요청 본문에 `requestTypeCode` 필수 → TicketService 가 RequestType 의 default_workflow 로 WorkflowInstance 자동 시작.
- 응답에 `workflowInstanceId`·`requestTypeCode` 포함.

### 5. AssetCategoryController — `/api/asset-categories`
- `GET /tree` (인증만), `GET /{code}` 단건
- `POST·PATCH /{code}` (ASSET_ADMIN), `POST /{code}/move` (ASSET_ADMIN)
- 자기·자손 이동은 백엔드 거부 → 400 INVALID_REQUEST

### 6. AssetController 확장
- `POST /api/assets` 본문에 `categoryCode` 필수.
- `GET /api/assets/{id}/lifecycle-events` (ASSET_READ) — 이벤트 이력.
- `POST /api/assets/{id}/lifecycle-events` (ASSET_ADMIN) — 이벤트 기록 (transfer·repair·dispose 등).

### 7. Swagger·@WebMvcTest
- 모든 endpoint `@Operation` + `@Parameter(example)` + `@PreAuthorize`.
- `WorkflowInstanceControllerTest` — 권한 분기·전이 시나리오.
- `AssetCategoryControllerTest` — 트리·move.

## Acceptance Criteria
```bash
cd backend
test -f src/main/java/com/nkia/itg/itsm/requesttype/controller/TicketRequestTypeController.java
test -f src/main/java/com/nkia/itg/itsm/workflow/controller/WorkflowDefinitionController.java
test -f src/main/java/com/nkia/itg/itsm/workflow/controller/WorkflowInstanceController.java
test -f src/main/java/com/nkia/itg/itam/category/controller/AssetCategoryController.java
./gradlew test --tests "com.nkia.itg.itsm.*.controller.*"
./gradlew test --tests "com.nkia.itg.itam.category.controller.*"
./gradlew test
./gradlew build

cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10
curl -fsS http://localhost:8080/v3/api-docs > /tmp/spec.json
python3 -c "import json; d=json.load(open('/tmp/spec.json')); \
            need={'/api/ticket-request-types','/api/workflow-definitions', \
                  '/api/workflow-instances/by-ticket/{ticketId}', \
                  '/api/asset-categories','/api/assets/{id}/lifecycle-events'}; \
            assert need.issubset(d['paths'].keys())"
kill %1
```

## 금지사항
- Controller 안 비즈니스 분기 금지 — Service 위임.
- 모든 새 endpoint 에 `@PreAuthorize` 또는 SecurityConfig authenticated() 둘 다 누락 금지.
- DELETE endpoint 추가 금지 (archive·deactivate 만).
- `@Schema(example)` 실 운영 데이터 금지.
- 프런트엔드 수정 금지.
