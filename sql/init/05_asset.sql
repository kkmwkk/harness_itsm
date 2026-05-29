-- Polestar10 ITG v2 — ITAM 자산원장 스키마 (PRD §4 M4 대상)
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해
-- 01_schema.sql → 02_ticket.sql 다음(파일명 사전순)으로 자동 실행된다.
-- 멱등성 유지: CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / DROP TRIGGER IF EXISTS
--
-- ================================================================
-- asset : ITAM 자산원장 (PRD §4 M4 대상)
-- 컬럼:
--   id           BIGSERIAL  PK
--   asset_no     VARCHAR(32) NULL · UNIQUE 표시용 (예: 'AST-00001').
--                            IDENTITY 전략은 save() 시점에 INSERT 를 즉시 실행하므로
--                            asset_no 는 INSERT 시점에 한 트랜잭션 안에서 잠시 null
--                            이며, dirty checking 으로 직후 update 된다. UNIQUE 제약은
--                            NULL 허용이므로 동시성 충돌 없음 (ticket 패턴 동일).
--   name         VARCHAR(200) NOT NULL
--   asset_type   VARCHAR(20)  NOT NULL CHECK (HARDWARE/SOFTWARE/LICENSE/CONTRACT/SERVICE)
--   status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
--                                    CHECK (ACTIVE/STORAGE/RETIRED/REPLACED)
--   model        VARCHAR(100) NULL
--   serial_no    VARCHAR(60)  NULL
--   category     VARCHAR(40)  NULL
--   assignee_id  VARCHAR(60)  NULL (사용자 모듈은 별도 phase — FK 없음)
--   location     VARCHAR(100) NULL
--   acquired_at  DATE         NULL
--   disposed_at  DATE         NULL (status=RETIRED 전이 시 set)
--   page_meta_id_at_registration VARCHAR(100) NOT NULL · FK → page_meta(id)
--                            자산 등록 시점의 메타 ID 보존 (PRD §5-2 활용 사례).
--                            메타가 DEPRECATED·ARCHIVED 되어도 자산 행에서 참조 가능
--                            (메타는 물리 삭제 안 함, 상태만 전이).
--   created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
--   updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
-- ================================================================
CREATE TABLE IF NOT EXISTS asset (
  id           BIGSERIAL PRIMARY KEY,
  asset_no     VARCHAR(32),
  name         VARCHAR(200) NOT NULL,
  asset_type   VARCHAR(20)  NOT NULL
                CONSTRAINT chk_asset_type
                CHECK (asset_type IN ('HARDWARE','SOFTWARE','LICENSE','CONTRACT','SERVICE')),
  status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                CONSTRAINT chk_asset_status
                CHECK (status IN ('ACTIVE','STORAGE','RETIRED','REPLACED')),
  model        VARCHAR(100),
  serial_no    VARCHAR(60),
  category     VARCHAR(40),
  assignee_id  VARCHAR(60),
  location     VARCHAR(100),
  acquired_at  DATE,
  disposed_at  DATE,
  -- ────────────────────────────────────────────────────────────────
  -- 자산 등록 시점의 메타 ID 보존 (PRD §5-2 활용 사례)
  -- ────────────────────────────────────────────────────────────────
  page_meta_id_at_registration  VARCHAR(100) NOT NULL,                  -- 예: 'itg-asset-v1-1'
  created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_asset_no UNIQUE (asset_no),
  CONSTRAINT fk_asset_meta FOREIGN KEY (page_meta_id_at_registration)
    REFERENCES page_meta(id)
);

-- ============================================================================
-- 인덱스
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_asset_status     ON asset(status);
CREATE INDEX IF NOT EXISTS idx_asset_type       ON asset(asset_type);
CREATE INDEX IF NOT EXISTS idx_asset_assignee   ON asset(assignee_id);
CREATE INDEX IF NOT EXISTS idx_asset_created_at ON asset(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_asset_meta_at_reg ON asset(page_meta_id_at_registration);

-- ============================================================================
-- updated_at 자동 갱신 트리거
-- 01_schema.sql 에 이미 정의된 fn_touch_updated_at() 을 그대로 재사용한다.
-- ============================================================================
DROP TRIGGER IF EXISTS trg_asset_touch_updated_at ON asset;
CREATE TRIGGER trg_asset_touch_updated_at
BEFORE UPDATE ON asset
FOR EACH ROW
EXECUTE FUNCTION fn_touch_updated_at();

-- ============================================================================
-- 기존 컨테이너 호환 (멱등): asset_no NOT NULL 가 있던 스키마에 대해 제약 해제.
-- 신규 컨테이너 (위 CREATE TABLE) 에서는 이미 NULL 허용이라 no-op.
-- ============================================================================
ALTER TABLE asset ALTER COLUMN asset_no DROP NOT NULL;
