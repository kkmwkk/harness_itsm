# Step 0: workflow-schema-and-engine

## 읽어야 할 파일

- `/CLAUDE.md` v2.1
- `/docs/PRD.md` §4-2 ITSM 도메인, §5-3 워크플로우 엔진
- `/docs/ARCHITECTURE.md` §14-2 ITSM ERD, §15 워크플로우 엔진 흐름
- `/docs/ADR.md` ADR-015 자체 단계 엔진 MVP, ADR-017 BPMN deferred
- `/sql/init/02_ticket.sql` (기존 ticket — 컬럼 추가 필요)
- `/backend/src/main/java/com/nkia/itg/itsm/ticket/entity/Ticket.java`

## 작업

이 step 의 목적은 **요청 유형(TicketRequestType) + 워크플로우 정의/인스턴스/단계 이력 + 기존 ticket 확장 컬럼 + WorkflowEngineService MVP** 를 만드는 것이다.

### 1. DB 스키마 — `sql/init/11_itsm_workflow.sql`

```sql
-- ticket 테이블 확장 (멱등 ALTER)
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS request_type_code VARCHAR(40);
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS workflow_instance_id BIGINT;
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS requester_user_id BIGINT;
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS page_meta_id_at_registration VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_ticket_request_type ON ticket(request_type_code);
CREATE INDEX IF NOT EXISTS idx_ticket_requester    ON ticket(requester_user_id);
CREATE INDEX IF NOT EXISTS idx_ticket_workflow     ON ticket(workflow_instance_id);

-- 요청 유형
CREATE TABLE IF NOT EXISTS ticket_request_type (
  code                  VARCHAR(40) PRIMARY KEY,    -- 'INCIDENT'/'SERVICE_REQUEST'/'CHANGE'/'PROBLEM'/'QNA'
  label                 VARCHAR(80) NOT NULL,
  form_meta_group_id    VARCHAR(100),               -- 'itg-ticket-incident' 등
  default_workflow_code VARCHAR(40),                -- FK soft (workflow_definition.code)
  sla_minutes_default   INTEGER,
  active                BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 워크플로우 정의
CREATE TABLE IF NOT EXISTS workflow_definition (
  code        VARCHAR(40) PRIMARY KEY,               -- 'WF_INCIDENT_STD'
  name        VARCHAR(120) NOT NULL,
  version     INTEGER NOT NULL DEFAULT 1,
  steps       JSONB NOT NULL,                        -- ARCHITECTURE §15-1 의 steps 배열
  active      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 워크플로우 인스턴스 (티켓 1개당 1개)
CREATE TABLE IF NOT EXISTS workflow_instance (
  id                  BIGSERIAL PRIMARY KEY,
  workflow_def_code   VARCHAR(40) NOT NULL,
  ticket_id           BIGINT      NOT NULL,
  current_step_index  INTEGER     NOT NULL DEFAULT 0,
  status              VARCHAR(20) NOT NULL DEFAULT 'RUNNING'
                      CONSTRAINT chk_wfi_status CHECK (status IN ('RUNNING','COMPLETED','CANCELED','REJECTED')),
  started_at  TIMESTAMP NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMP,
  CONSTRAINT fk_wfi_workflow FOREIGN KEY (workflow_def_code) REFERENCES workflow_definition(code),
  CONSTRAINT uq_wfi_ticket   UNIQUE (ticket_id)
);
CREATE INDEX IF NOT EXISTS idx_wfi_status ON workflow_instance(status);

-- 단계 실행 이력
CREATE TABLE IF NOT EXISTS workflow_instance_step (
  id                BIGSERIAL PRIMARY KEY,
  instance_id       BIGINT NOT NULL,
  step_index        INTEGER NOT NULL,
  step_name         VARCHAR(120) NOT NULL,           -- 스냅샷 (정의 변경에도 이력 보존)
  assignee_role     VARCHAR(40),                     -- Role.code 스냅샷
  assigned_to_user_id BIGINT,
  started_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  completed_at      TIMESTAMP,
  sla_due_at        TIMESTAMP,
  action            VARCHAR(20)                      -- APPROVE/REJECT/FORWARD/COMPLETE/CONFIRM/REOPEN
                    CONSTRAINT chk_wfis_action CHECK (action IS NULL
                       OR action IN ('APPROVE','REJECT','FORWARD','COMPLETE','CONFIRM','REOPEN')),
  action_by_user_id BIGINT,
  action_comment    TEXT,
  CONSTRAINT fk_wfis_instance FOREIGN KEY (instance_id) REFERENCES workflow_instance(id)
);
CREATE INDEX IF NOT EXISTS idx_wfis_instance ON workflow_instance_step(instance_id);

DROP TRIGGER IF EXISTS trg_wfi_touch  ON workflow_instance;
CREATE TRIGGER trg_wfi_touch  BEFORE UPDATE ON workflow_instance  FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
DROP TRIGGER IF EXISTS trg_wfd_touch  ON workflow_definition;
CREATE TRIGGER trg_wfd_touch  BEFORE UPDATE ON workflow_definition FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
DROP TRIGGER IF EXISTS trg_trt_touch  ON ticket_request_type;
CREATE TRIGGER trg_trt_touch  BEFORE UPDATE ON ticket_request_type FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
```

### 2. 패키지·Entity

```
backend/src/main/java/com/nkia/itg/itsm/
├── requesttype/
│   ├── entity/TicketRequestType.java
│   └── domain/StepAction.java  enum { APPROVE, REJECT, FORWARD, COMPLETE, CONFIRM, REOPEN }
└── workflow/
    ├── entity/WorkflowDefinition.java
    ├── entity/WorkflowInstance.java
    ├── entity/WorkflowInstanceStep.java
    └── domain/WorkflowStatus.java  enum { RUNNING, COMPLETED, CANCELED, REJECTED }
```

`Ticket` Entity 확장: `requestTypeCode`·`workflowInstanceId`·`requesterUserId`·`pageMetaIdAtRegistration` 컬럼 추가.

### 3. `WorkflowEngineService` — `com.nkia.itg.itsm.workflow.service`

핵심 메서드:

```java
@Service @Transactional @RequiredArgsConstructor
public class WorkflowEngineService {
    private final WorkflowDefinitionRepository defRepo;
    private final WorkflowInstanceRepository    instanceRepo;
    private final WorkflowInstanceStepRepository stepRepo;
    private final TicketRepository              ticketRepo;

    /** 티켓 생성 시 호출 — request_type 의 default_workflow 로 인스턴스 시작. */
    public WorkflowInstance start(Ticket ticket, String workflowDefCode);

    /** 단계 액션 실행 — 현재 사용자가 단계의 assignee_role 보유한지 검증. */
    public WorkflowInstance executeAction(
        Long instanceId, int stepIndex, StepAction action,
        Long actorUserId, Set<String> actorRoles, String comment);

    /** SLA 초과 단계 조회 (배치 또는 polling 용). */
    @Transactional(readOnly = true)
    List<WorkflowInstanceStep> findOverdue();
}
```

전이 로직:
- `APPROVE`/`FORWARD`/`COMPLETE` → 다음 단계 또는 종결.
- `REJECT` → status=REJECTED + 종결.
- `CONFIRM` → status=COMPLETED + Ticket.status=CLOSED.
- `REOPEN` → status=RUNNING + current_step_index=0 (CLOSED 였던 경우).
- 권한 위반·status=COMPLETED/CANCELED/REJECTED 인 인스턴스의 액션 시도 → `ITGException("INVALID_WORKFLOW_ACTION", 400)`.
- 한 트랜잭션 안에서: 기존 step row 의 completed_at/action 갱신 + 다음 step row 새로 추가 + instance.current_step_index 갱신.

### 4. JSONB 매핑 (steps)

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "steps", columnDefinition = "jsonb", nullable = false)
private List<Map<String, Object>> steps;
```

또는 정형 record 매핑. 본 step 은 단순화 위해 `List<Map<String,Object>>` (PageMeta JSONB 패턴 재사용).

### 5. 단위 테스트

`WorkflowEngineServiceTest` (Mockito):

1. `start_요청유형의_workflow_로_인스턴스_생성_current_step_0_첫_step_row_추가`.
2. `executeAction_APPROVE_다음_단계_전이_step_row_추가`.
3. `executeAction_REJECT_status_REJECTED_종결`.
4. `executeAction_assignee_role_없는_사용자_INVALID_WORKFLOW_ACTION_400`.
5. `executeAction_COMPLETED_인스턴스_재호출_거부`.
6. `executeAction_마지막_단계_COMPLETE_status_COMPLETED_Ticket_CLOSED`.
7. `executeAction_REOPEN_CLOSED_재오픈_current_step_0`.

Entity 도메인 메서드:
- `WorkflowInstance.advance(nextStep, actor)`·`reject(actor)`·`complete()`·`reopen()`.
- `WorkflowInstanceStep.finalize(action, actor, comment)`.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
test -f sql/init/11_itsm_workflow.sql
grep -q "CREATE TABLE IF NOT EXISTS ticket_request_type" sql/init/11_itsm_workflow.sql
grep -q "CREATE TABLE IF NOT EXISTS workflow_definition" sql/init/11_itsm_workflow.sql
grep -q "CREATE TABLE IF NOT EXISTS workflow_instance"   sql/init/11_itsm_workflow.sql

cd backend
test -f src/main/java/com/nkia/itg/itsm/requesttype/entity/TicketRequestType.java
test -f src/main/java/com/nkia/itg/itsm/workflow/entity/WorkflowDefinition.java
test -f src/main/java/com/nkia/itg/itsm/workflow/entity/WorkflowInstance.java
test -f src/main/java/com/nkia/itg/itsm/workflow/entity/WorkflowInstanceStep.java
test -f src/main/java/com/nkia/itg/itsm/workflow/service/WorkflowEngineService.java
test -f src/main/java/com/nkia/itg/itsm/requesttype/domain/StepAction.java
test -f src/main/java/com/nkia/itg/itsm/workflow/domain/WorkflowStatus.java

./gradlew test --tests "com.nkia.itg.itsm.workflow.*"
./gradlew test
./gradlew build

docker-compose up -d
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/11_itsm_workflow.sql
cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q UP
kill %1
```

## 금지사항

- 단계 전이 매트릭스를 Service 안 if/else 흩뿌리지 마라. Entity 의 도메인 메서드 `advance/reject/complete/reopen` 으로.
- BPMN/Camunda 의존성 추가 금지 — ADR-017 deferred.
- 단계 정의(steps) 를 별도 normalize 테이블로 만들지 마라 — JSONB 단순화.
- 인스턴스 status=COMPLETED 이후 액션 허용 금지.
- 프런트엔드 수정 금지.
- ticket·asset·meta·system 기존 모듈 깨뜨리지 마라 — 컬럼 추가는 ALTER 멱등으로.
