-- Polestar10 ITG v2 — page_meta 스키마
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해 자동 실행된다.
-- 멱등성 유지: CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / CREATE OR REPLACE / DROP TRIGGER IF EXISTS

-- ============================================================================
-- 테이블 page_meta
-- ============================================================================
CREATE TABLE IF NOT EXISTS page_meta (
  id              VARCHAR(100)  PRIMARY KEY,
  title           VARCHAR(200)  NOT NULL,
  system_type     VARCHAR(20)   NOT NULL
                  CONSTRAINT chk_system_type
                  CHECK (system_type IN ('ITSM','ITAM','PMS','COMMON','SYSTEM')),
  package_type    VARCHAR(10)   NOT NULL
                  CONSTRAINT chk_package_type
                  CHECK (package_type IN ('PACKAGE','CUSTOM')),
  group_id        VARCHAR(100)  NOT NULL,
  major_version   INTEGER       NOT NULL DEFAULT 1
                  CONSTRAINT chk_major_version CHECK (major_version >= 1),
  minor_version   INTEGER       NOT NULL DEFAULT 1
                  CONSTRAINT chk_minor_version CHECK (minor_version >= 1),
  meta_status     VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                  CONSTRAINT chk_meta_status
                  CHECK (meta_status IN ('DRAFT','PUBLISHED','DEPRECATED','ARCHIVED')),
  meta_json       JSONB         NOT NULL,
  active          BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_group_version UNIQUE (group_id, major_version, minor_version)
);

-- ============================================================================
-- 인덱스
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_page_meta_system          ON page_meta(system_type);
CREATE INDEX IF NOT EXISTS idx_page_meta_package         ON page_meta(package_type);
CREATE INDEX IF NOT EXISTS idx_page_meta_system_package  ON page_meta(system_type, package_type);
CREATE INDEX IF NOT EXISTS idx_page_meta_group           ON page_meta(group_id);
CREATE INDEX IF NOT EXISTS idx_page_meta_group_status    ON page_meta(group_id, meta_status);
CREATE INDEX IF NOT EXISTS idx_page_meta_json            ON page_meta USING GIN(meta_json);

-- ============================================================================
-- 화면 노출용 뷰: groupId 별 PUBLISHED 최신 1건
-- ============================================================================
CREATE OR REPLACE VIEW page_meta_active AS
SELECT DISTINCT ON (group_id) *
FROM   page_meta
WHERE  meta_status = 'PUBLISHED'
  AND  active      = TRUE
ORDER  BY group_id, major_version DESC, minor_version DESC;

-- ============================================================================
-- 자동 DEPRECATE 트리거
-- 새 행이 PUBLISHED 로 전환되면 동일 group_id 의 기존 PUBLISHED 를 DEPRECATED 로 갱신한다.
-- AFTER UPDATE OF meta_status 에만 동작 — INSERT 시점에는 작동하지 않는다 (Service 레이어 책임).
-- OLD.meta_status IS DISTINCT FROM 'PUBLISHED' 로 no-op UPDATE 시 비활성.
-- ============================================================================
CREATE OR REPLACE FUNCTION fn_auto_deprecate_on_publish()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.meta_status = 'PUBLISHED'
     AND (OLD.meta_status IS DISTINCT FROM 'PUBLISHED') THEN
    UPDATE page_meta
       SET meta_status = 'DEPRECATED',
           updated_at  = NOW()
     WHERE group_id    = NEW.group_id
       AND id          <> NEW.id
       AND meta_status = 'PUBLISHED';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_auto_deprecate ON page_meta;
CREATE TRIGGER trg_auto_deprecate
AFTER UPDATE OF meta_status ON page_meta
FOR EACH ROW
EXECUTE FUNCTION fn_auto_deprecate_on_publish();

-- ============================================================================
-- updated_at 자동 갱신 트리거 — 모든 UPDATE 에 적용
-- ============================================================================
CREATE OR REPLACE FUNCTION fn_touch_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_touch_updated_at ON page_meta;
CREATE TRIGGER trg_touch_updated_at
BEFORE UPDATE ON page_meta
FOR EACH ROW
EXECUTE FUNCTION fn_touch_updated_at();
