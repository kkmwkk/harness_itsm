-- Polestar10 ITG v2.1 — ITSM 요청 유형·워크플로우 스키마 (PRD §4-2 / §5-3, ADR-015)
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해
-- 파일명 사전순(10_auth.sql 다음)으로 자동 실행된다.
-- 멱등성 유지: ALTER ... IF NOT EXISTS / CREATE TABLE IF NOT EXISTS /
--             CREATE INDEX IF NOT EXISTS / DROP TRIGGER IF EXISTS.
-- updated_at 자동 갱신은 01_schema.sql 의 fn_touch_updated_at() 재사용.

-- ============================================================================
-- ticket 테이블 확장 (멱등 ALTER) — 요청 유형·워크플로우 인스턴스·요청자·이력 메타
-- ============================================================================
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS request_type_code VARCHAR(40);
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS workflow_instance_id BIGINT;
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS requester_user_id BIGINT;
ALTER TABLE ticket ADD COLUMN IF NOT EXISTS page_meta_id_at_registration VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_ticket_request_type ON ticket(request_type_code);
CREATE INDEX IF NOT EXISTS idx_ticket_requester    ON ticket(requester_user_id);
CREATE INDEX IF NOT EXISTS idx_ticket_workflow     ON ticket(workflow_instance_id);

-- ============================================================================
-- ticket_request_type : 요청 유형 (장애·서비스요청·변경·문제·QnA)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ticket_request_type (
  code                  VARCHAR(40) PRIMARY KEY,    -- 'INCIDENT'/'SERVICE_REQUEST'/'CHANGE'/'PROBLEM'/'QNA'
  label                 VARCHAR(80) NOT NULL,
  form_meta_group_id    VARCHAR(100),               -- 'itg-ticket-incident' 등 (폼 메타 분기 키)
  default_workflow_code VARCHAR(40),                -- FK soft (workflow_definition.code)
  sla_minutes_default   INTEGER,
  active                BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- workflow_definition : 워크플로우 정의 (자체 단계 엔진 — ADR-015)
--   steps JSONB = ARCHITECTURE §15-1 의 단계 배열
--   [{ "index":0,"name":"접수","assignee_role_code":"ROLE_IT_SUPPORT",
--      "sla_minutes":60,"allowed_actions":["FORWARD","COMPLETE"] }, ...]
-- ============================================================================
CREATE TABLE IF NOT EXISTS workflow_definition (
  code        VARCHAR(40) PRIMARY KEY,               -- 'WF_INCIDENT_STD'
  name        VARCHAR(120) NOT NULL,
  version     INTEGER NOT NULL DEFAULT 1,
  steps       JSONB NOT NULL,
  active      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- workflow_instance : 워크플로우 인스턴스 (티켓 1개당 1개 — uq_wfi_ticket)
-- ============================================================================
CREATE TABLE IF NOT EXISTS workflow_instance (
  id                  BIGSERIAL PRIMARY KEY,
  workflow_def_code   VARCHAR(40) NOT NULL,
  ticket_id           BIGINT      NOT NULL,
  current_step_index  INTEGER     NOT NULL DEFAULT 0,
  status              VARCHAR(20) NOT NULL DEFAULT 'RUNNING'
                      CONSTRAINT chk_wfi_status CHECK (status IN ('RUNNING','COMPLETED','CANCELED','REJECTED')),
  started_at  TIMESTAMP NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMP,
  -- trg_wfi_touch(fn_touch_updated_at) 가 NEW.updated_at 을 갱신하므로 컬럼이 필수.
  updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_wfi_workflow FOREIGN KEY (workflow_def_code) REFERENCES workflow_definition(code),
  CONSTRAINT uq_wfi_ticket   UNIQUE (ticket_id)
);
CREATE INDEX IF NOT EXISTS idx_wfi_status ON workflow_instance(status);

-- ============================================================================
-- workflow_instance_step : 단계 실행 이력 (정의 변경에도 이력 보존 — step_name 스냅샷)
-- ============================================================================
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

-- ============================================================================
-- updated_at 자동 갱신 트리거 (01_schema.sql 의 fn_touch_updated_at 재사용)
-- ============================================================================
DROP TRIGGER IF EXISTS trg_wfi_touch  ON workflow_instance;
CREATE TRIGGER trg_wfi_touch  BEFORE UPDATE ON workflow_instance  FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
DROP TRIGGER IF EXISTS trg_wfd_touch  ON workflow_definition;
CREATE TRIGGER trg_wfd_touch  BEFORE UPDATE ON workflow_definition FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
DROP TRIGGER IF EXISTS trg_trt_touch  ON ticket_request_type;
CREATE TRIGGER trg_trt_touch  BEFORE UPDATE ON ticket_request_type FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
