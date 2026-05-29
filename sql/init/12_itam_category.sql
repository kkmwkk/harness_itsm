-- Polestar10 ITG v2.1 — ITAM 자산 분류 트리 + 자산 이력 이벤트 (PRD §4-3 / ARCHITECTURE §14-3, M8)
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해
-- 05_asset.sql 다음(파일명 사전순)으로 자동 실행된다.
-- 멱등성 유지: CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / ADD COLUMN IF NOT EXISTS / DROP TRIGGER IF EXISTS
--
-- 분류 path('/HW/HW_LAPTOP/')는 Service(AssetCategoryService)가 계산·유지한다 — DB 트리거 의존 없음.
-- ================================================================

-- asset_category : 자산 분류 트리 (code 식별, 자기참조)
CREATE TABLE IF NOT EXISTS asset_category (
  code              VARCHAR(40) PRIMARY KEY,     -- 'HW_LAPTOP'/'HW_SERVER'/'SW_LICENSE'/'CONTRACT_NDA'
  label             VARCHAR(80) NOT NULL,
  parent_code       VARCHAR(40),
  path              VARCHAR(255) NOT NULL,       -- '/HW/HW_LAPTOP/' 형태
  form_meta_group_id VARCHAR(100),               -- 'itg-asset-hw-laptop'
  active            BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order        INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_ac_parent FOREIGN KEY (parent_code) REFERENCES asset_category(code)
);
CREATE INDEX IF NOT EXISTS idx_ac_parent ON asset_category(parent_code);
CREATE INDEX IF NOT EXISTS idx_ac_path   ON asset_category(path);

-- asset 확장: 분류 코드 (멱등 ALTER)
ALTER TABLE asset ADD COLUMN IF NOT EXISTS category_code VARCHAR(40);
ALTER TABLE asset
  ADD CONSTRAINT fk_asset_category FOREIGN KEY (category_code) REFERENCES asset_category(code);
CREATE INDEX IF NOT EXISTS idx_asset_category ON asset(category_code);

-- asset_lifecycle_event : 자산 이력 이벤트 (취득·이관·수리·폐기·갱신)
CREATE TABLE IF NOT EXISTS asset_lifecycle_event (
  id            BIGSERIAL PRIMARY KEY,
  asset_id      BIGINT NOT NULL,
  event_type    VARCHAR(20) NOT NULL
                CONSTRAINT chk_ale_event CHECK (event_type IN
                  ('ACQUIRED','TRANSFERRED','REPAIRED','DISPOSED','RENEWED')),
  event_date    DATE NOT NULL DEFAULT CURRENT_DATE,
  by_user_id    BIGINT,
  payload       JSONB,
  created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_ale_asset FOREIGN KEY (asset_id) REFERENCES asset(id)
);
CREATE INDEX IF NOT EXISTS idx_ale_asset ON asset_lifecycle_event(asset_id, event_date DESC);

-- updated_at 자동 갱신 트리거 (01_schema.sql 의 fn_touch_updated_at() 재사용)
DROP TRIGGER IF EXISTS trg_ac_touch ON asset_category;
CREATE TRIGGER trg_ac_touch BEFORE UPDATE ON asset_category FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
