-- Polestar10 ITG v2 — ITSM 티켓 스키마 (PRD §4 M3 대상)
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해
-- 01_schema.sql 다음(파일명 사전순)으로 자동 실행된다.
-- 멱등성 유지: CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / DROP TRIGGER IF EXISTS

-- ================================================================
-- ticket : ITSM 티켓 (PRD §4 M3 대상)
-- 컬럼:
--   id           BIGSERIAL  PK
--   ticket_no    TEXT       UNIQUE 표시용 (예: 'ITSM-00001')
--   title        TEXT       NOT NULL
--   content      TEXT       NULL
--   priority     TEXT       NOT NULL CHECK (LOW/MEDIUM/HIGH/CRITICAL)
--   status       TEXT       NOT NULL DEFAULT 'OPEN'
--                                   CHECK (OPEN/IN_PROGRESS/RESOLVED/CLOSED)
--   category     TEXT       NULL (BUG/REQ/QNA 등 가벼운 코드 문자열)
--   assignee_id  TEXT       NULL (사용자 모듈은 다음 phase — FK 없음)
--   created_at   TIMESTAMP  NOT NULL DEFAULT NOW()
--   updated_at   TIMESTAMP  NOT NULL DEFAULT NOW()
--   closed_at    TIMESTAMP  NULL  (status=CLOSED 전이 시 set)
-- ================================================================
CREATE TABLE IF NOT EXISTS ticket (
  id           BIGSERIAL PRIMARY KEY,
  ticket_no    VARCHAR(32)  NOT NULL,
  title        VARCHAR(200) NOT NULL,
  content      TEXT,
  priority     VARCHAR(10)  NOT NULL
                CONSTRAINT chk_ticket_priority
                CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
                CONSTRAINT chk_ticket_status
                CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
  category     VARCHAR(40),
  assignee_id  VARCHAR(60),
  created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
  closed_at    TIMESTAMP,
  CONSTRAINT uq_ticket_no UNIQUE (ticket_no)
);

-- ============================================================================
-- 인덱스
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_ticket_status     ON ticket(status);
CREATE INDEX IF NOT EXISTS idx_ticket_priority   ON ticket(priority);
CREATE INDEX IF NOT EXISTS idx_ticket_assignee   ON ticket(assignee_id);
CREATE INDEX IF NOT EXISTS idx_ticket_created_at ON ticket(created_at DESC);

-- ============================================================================
-- updated_at 자동 갱신 트리거
-- 01_schema.sql 에 이미 정의된 fn_touch_updated_at() 을 그대로 재사용한다.
-- ============================================================================
DROP TRIGGER IF EXISTS trg_ticket_touch_updated_at ON ticket;
CREATE TRIGGER trg_ticket_touch_updated_at
BEFORE UPDATE ON ticket
FOR EACH ROW
EXECUTE FUNCTION fn_touch_updated_at();
