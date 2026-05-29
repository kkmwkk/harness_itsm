-- Polestar10 ITG v2 — 인증·사용자·부서·역할·권한·메뉴 스키마 (v2.1 M7 / PRD §4-1)
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해
-- 01_schema.sql ~ 07_*.sql 다음(파일명 사전순)으로 자동 실행된다.
-- 멱등성 유지: CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / DROP TRIGGER IF EXISTS /
--             ADD CONSTRAINT 는 pg_constraint 체크 DO 블록으로 멱등화.
--
-- 참고:
--   • 'user' 는 SQL 예약어이므로 user_account 로 명명.
--   • user_account.department_id ↔ department.manager_user_id 는 순환 FK 이므로,
--     department → user_account 순서로 테이블을 만든 뒤 manager FK 는 ALTER 로 후행 추가.
--   • Department.path 자동 계산은 Service 책임 (트리거로 구현하지 않음).
--   • updated_at 자동 갱신은 01_schema.sql 의 fn_touch_updated_at() 을 그대로 재사용.

-- ============================================================================
-- department : 조직 트리 (자기참조 parent_id + path)
-- ============================================================================
CREATE TABLE IF NOT EXISTS department (
  id               BIGSERIAL    PRIMARY KEY,
  code             VARCHAR(60)  NOT NULL,
  name             VARCHAR(100) NOT NULL,
  parent_id        BIGINT,                                   -- 자기참조 (NULL = 루트)
  path             VARCHAR(255),                             -- 예: '/1/3/12/' (Service 가 계산)
  manager_user_id  BIGINT,                                   -- FK → user_account(id), 후행 ALTER 추가
  active           BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_department_code UNIQUE (code),
  CONSTRAINT fk_department_parent FOREIGN KEY (parent_id) REFERENCES department(id)
);

-- ============================================================================
-- user_account : 사용자 ('user' 예약어 회피)
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_account (
  id                   BIGSERIAL    PRIMARY KEY,
  username             VARCHAR(60)  NOT NULL,
  password_hash        VARCHAR(72)  NOT NULL,                -- BCrypt 최대 60 + margin
  name                 VARCHAR(100),
  email                VARCHAR(120),
  phone                VARCHAR(30),
  department_id        BIGINT,                               -- FK → department(id), NULL 허용
  status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                       CONSTRAINT chk_user_status
                       CHECK (status IN ('ACTIVE','LOCKED','RETIRED')),
  password_changed_at  TIMESTAMP,
  last_login_at        TIMESTAMP,
  created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_user_username UNIQUE (username),
  CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES department(id)
);

-- department.manager_user_id → user_account(id) 순환 FK 후행 추가 (멱등)
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_department_manager') THEN
    ALTER TABLE department
      ADD CONSTRAINT fk_department_manager
      FOREIGN KEY (manager_user_id) REFERENCES user_account(id);
  END IF;
END $$;

-- ============================================================================
-- role : 역할
-- ============================================================================
CREATE TABLE IF NOT EXISTS role (
  id           BIGSERIAL    PRIMARY KEY,
  code         VARCHAR(60)  NOT NULL,                        -- 예: 'ROLE_ADMIN'
  name         VARCHAR(100) NOT NULL,
  description  VARCHAR(255),
  created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_role_code UNIQUE (code)
);

-- ============================================================================
-- permission : 권한 (메뉴·기능 단위)
-- ============================================================================
CREATE TABLE IF NOT EXISTS permission (
  id           BIGSERIAL    PRIMARY KEY,
  code         VARCHAR(60)  NOT NULL,                        -- 예: 'TICKET_CREATE', 'META_PUBLISH'
  name         VARCHAR(100) NOT NULL,
  description  VARCHAR(255),
  created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_permission_code UNIQUE (code)
);

-- ============================================================================
-- user_role : User ↔ Role (N:M) — JPA @ManyToMany 조인 테이블 (별도 Entity 없음)
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_role (
  user_id     BIGINT     NOT NULL,
  role_id     BIGINT     NOT NULL,
  granted_at  TIMESTAMP  NOT NULL DEFAULT NOW(),
  granted_by  BIGINT,
  CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES user_account(id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id)
);

-- ============================================================================
-- role_permission : Role ↔ Permission (N:M) — JPA @ManyToMany 조인 테이블 (별도 Entity 없음)
-- ============================================================================
CREATE TABLE IF NOT EXISTS role_permission (
  role_id        BIGINT  NOT NULL,
  permission_id  BIGINT  NOT NULL,
  CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES role(id),
  CONSTRAINT fk_role_permission_perm FOREIGN KEY (permission_id) REFERENCES permission(id)
);

-- ============================================================================
-- menu : 동적 메뉴 트리 (자기참조 + PageMeta group 연결)
--   • group_id 는 page_meta.id 가 아니라 group 키이므로 FK 로 걸지 않는다 (비-FK).
--   • permission_code 는 NULL(누구나) 또는 permission.code 매칭.
-- ============================================================================
CREATE TABLE IF NOT EXISTS menu (
  id               BIGSERIAL    PRIMARY KEY,
  code             VARCHAR(60)  NOT NULL,
  parent_id        BIGINT,                                   -- 자기참조 (NULL = 루트)
  label            VARCHAR(100) NOT NULL,
  icon             VARCHAR(60),                              -- lucide 아이콘명 (예: 'BoxesIcon')
  sort_order       INTEGER      NOT NULL DEFAULT 0,
  route            VARCHAR(200),
  group_id         VARCHAR(100),                             -- PageMeta group 키 (비-FK, NULL 허용)
  permission_code  VARCHAR(60),                              -- FK → permission(code), NULL 허용
  active           BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_menu_code UNIQUE (code),
  CONSTRAINT fk_menu_parent FOREIGN KEY (parent_id) REFERENCES menu(id),
  CONSTRAINT fk_menu_permission FOREIGN KEY (permission_code) REFERENCES permission(code)
);

-- ============================================================================
-- 인덱스
-- ============================================================================
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_username  ON user_account(username);
CREATE INDEX        IF NOT EXISTS idx_user_dept      ON user_account(department_id);
CREATE INDEX        IF NOT EXISTS idx_user_status    ON user_account(status);
CREATE INDEX        IF NOT EXISTS idx_dept_parent    ON department(parent_id);
CREATE INDEX        IF NOT EXISTS idx_dept_path      ON department(path);
CREATE INDEX        IF NOT EXISTS idx_menu_parent    ON menu(parent_id);
CREATE INDEX        IF NOT EXISTS idx_menu_sort      ON menu(sort_order);
CREATE INDEX        IF NOT EXISTS idx_role_perm_role ON role_permission(role_id);
CREATE INDEX        IF NOT EXISTS idx_user_role_user ON user_role(user_id);

-- ============================================================================
-- updated_at 자동 갱신 트리거 (01_schema.sql 의 fn_touch_updated_at() 재사용)
-- 엔티티 테이블(updated_at 보유)에만 적용. 순수 조인 테이블(user_role/role_permission)은 제외.
-- ============================================================================
DROP TRIGGER IF EXISTS trg_user_account_touch_updated_at ON user_account;
CREATE TRIGGER trg_user_account_touch_updated_at
BEFORE UPDATE ON user_account
FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();

DROP TRIGGER IF EXISTS trg_department_touch_updated_at ON department;
CREATE TRIGGER trg_department_touch_updated_at
BEFORE UPDATE ON department
FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();

DROP TRIGGER IF EXISTS trg_role_touch_updated_at ON role;
CREATE TRIGGER trg_role_touch_updated_at
BEFORE UPDATE ON role
FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();

DROP TRIGGER IF EXISTS trg_permission_touch_updated_at ON permission;
CREATE TRIGGER trg_permission_touch_updated_at
BEFORE UPDATE ON permission
FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();

DROP TRIGGER IF EXISTS trg_menu_touch_updated_at ON menu;
CREATE TRIGGER trg_menu_touch_updated_at
BEFORE UPDATE ON menu
FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
