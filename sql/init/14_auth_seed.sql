-- Polestar10 ITG v2 — 인증 초기 시드 (v2.1 M7 / Phase 9 Step 4)
-- 초기 admin 사용자 + 기본 역할·권한·부서·메뉴 트리를 시드한다.
-- 10_auth.sql (스키마) 다음(파일명 사전순)으로 컨테이너 기동 시 자동 실행된다.
--
-- 멱등성: 모든 INSERT 는 ON CONFLICT DO NOTHING. 재실행해도 중복·갱신 없음.
--
-- 보안 주의 (ADR-011):
--   • 비밀번호는 평문이 아니라 BCrypt($2a$, cost 10) 해시로만 박는다.
--   • 비밀번호·이메일·이름은 모두 가상 샘플 — 운영 데이터 절대 금지.
--   • 시드 사용자 평문(테스트용): admin-sample-1234 / it-sample-1234 / user-sample-1234.
--     운영 배포 시 즉시 변경해야 한다.
--
-- 참고 — ROLE_ADMIN 은 역할 코드이자 권한 코드로 동시에 쓰인다:
--   menu.permission_code 는 fk_menu_permission 으로 permission(code) 를 참조한다.
--   메뉴 트리(시스템 관리·역할)의 권한 키가 'ROLE_ADMIN' 이므로 permission 목록에
--   'ROLE_ADMIN' 이 포함된다(아래 13종 중 하나). ROLE_ADMIN 역할은 '모든 권한' 을 보유해
--   이 권한도 자연히 가지며, 메뉴 필터(authority 기반)·@PreAuthorize 와 일관적이다.

-- ============================================================================
-- 1) 권한 (Permission) — 모듈/액션 코드 13종 (ROLE_ADMIN 포함, 위 참고 참조).
-- ============================================================================
INSERT INTO permission (code, name, description) VALUES
  ('USER_READ',         '사용자 조회',      '사용자 목록·단건 조회'),
  ('USER_ADMIN',        '사용자 관리',      '사용자 생성·수정·상태·역할 관리'),
  ('DEPT_READ',         '부서 조회',        '부서 트리 조회'),
  ('DEPT_ADMIN',        '부서 관리',        '부서 생성·수정·이동'),
  ('ROLE_ADMIN',        '역할/관리자',      '역할·권한 관리 및 시스템 관리자 권한'),
  ('MENU_ADMIN',        '메뉴 관리',        '메뉴 트리 생성·수정·이동'),
  ('META_READ',         '메타 조회',        '페이지 메타 조회'),
  ('META_PUBLISH',      '메타 배포',        '페이지 메타 발행·전이'),
  ('META_EDIT',         '메타 편집',        '페이지 메타 신규 생성·본문 편집(DRAFT)'),
  ('TICKET_READ',       '티켓 조회',        'ITSM 티켓 조회'),
  ('TICKET_CREATE',     '티켓 등록',        'ITSM 티켓 생성'),
  ('TICKET_APPROVE_L1', '티켓 1차 승인',    'ITSM 티켓 1차 승인'),
  ('ASSET_READ',        '자산 조회',        'ITAM 자산 조회'),
  ('ASSET_ADMIN',       '자산 관리',        'ITAM 자산 생성·수정·폐기')
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- 2) 역할 (Role) 3종
-- ============================================================================
INSERT INTO role (code, name, description) VALUES
  ('ROLE_ADMIN',      '시스템 관리자', '모든 권한 보유'),
  ('ROLE_IT_SUPPORT', 'IT 지원',       '티켓 처리·자산 조회'),
  ('ROLE_USER',       '일반 사용자',   '티켓 등록·자산 조회')
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- 3) 역할 ↔ 권한 (role_permission)
-- ============================================================================
-- ROLE_ADMIN : 모든 권한
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r CROSS JOIN permission p
WHERE r.code = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- ROLE_IT_SUPPORT : USER_READ + TICKET_READ + TICKET_CREATE + TICKET_APPROVE_L1 + ASSET_READ
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p
  ON p.code IN ('USER_READ','TICKET_READ','TICKET_CREATE','TICKET_APPROVE_L1','ASSET_READ')
WHERE r.code = 'ROLE_IT_SUPPORT'
ON CONFLICT DO NOTHING;

-- ROLE_USER : TICKET_READ + TICKET_CREATE + ASSET_READ
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p
  ON p.code IN ('TICKET_READ','TICKET_CREATE','ASSET_READ')
WHERE r.code = 'ROLE_USER'
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 4) 부서 (Department) — ROOT(/1/) + IT(/1/2/) + 운영(/1/3/)
--    명시 id 로 path 를 결정적으로 박는다. 시퀀스는 끝에서 보정.
-- ============================================================================
INSERT INTO department (id, code, name, parent_id, path, active) VALUES
  (1, 'ROOT', '전사',     NULL, '/1/',   TRUE),
  (2, 'IT',   'IT부문',   1,    '/1/2/', TRUE),
  (3, 'OPS',  '운영부문', 1,    '/1/3/', TRUE)
ON CONFLICT (code) DO NOTHING;

SELECT setval('department_id_seq', GREATEST((SELECT MAX(id) FROM department), 1));

-- ============================================================================
-- 5) 사용자 (user_account) — 명시 id (시나리오가 user-sample-1 = id 3 가정)
--    password_hash 는 BCrypt($2a$, cost 10) — 평문 저장 금지.
-- ============================================================================
INSERT INTO user_account (id, username, password_hash, name, email, department_id, status, password_changed_at) VALUES
  (1, 'admin',        '$2a$10$iJi49gTgEI6/5i8d2ruk5u4GaikdMLW/emjiF.xLHHlViN6La.Adu', '관리자',      'admin@example.com',         2, 'ACTIVE', NOW()),
  (2, 'it-support',   '$2a$10$G9ko44S3.pUI59Bgn1VzeetQ63izfv1JKI/7MPzsyQ3poFGEK5ueK', 'IT 지원담당', 'it-support@example.com',    2, 'ACTIVE', NOW()),
  (3, 'user-sample-1','$2a$10$iMlrKg8ADhCzgY677ydX/uVvgTQFcLZ/oQp.kBPKMInTdXWCmUBWG', '샘플 사용자', 'user-sample-1@example.com', 3, 'ACTIVE', NOW())
ON CONFLICT (username) DO NOTHING;

SELECT setval('user_account_id_seq', GREATEST((SELECT MAX(id) FROM user_account), 1));

-- ============================================================================
-- 6) 사용자 ↔ 역할 (user_role)
-- ============================================================================
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM user_account u JOIN role r ON r.code = 'ROLE_ADMIN'
WHERE u.username = 'admin'
ON CONFLICT DO NOTHING;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM user_account u JOIN role r ON r.code = 'ROLE_IT_SUPPORT'
WHERE u.username = 'it-support'
ON CONFLICT DO NOTHING;

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM user_account u JOIN role r ON r.code = 'ROLE_USER'
WHERE u.username = 'user-sample-1'
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 7) 메뉴 트리 (menu) — 명시 id 로 자기참조 parent 를 결정적으로 박는다.
--    permission_code 가 NULL 이면 누구나, 값이 있으면 해당 권한(또는 ROLE) 보유자만 노출.
-- ============================================================================
INSERT INTO menu (id, code, parent_id, label, icon, sort_order, route, group_id, permission_code, active) VALUES
  (1,  'DASHBOARD',     NULL, '대시보드',     'LayoutDashboardIcon', 0, '/',                 NULL,          NULL,           TRUE),
  (2,  'ITSM',          NULL, 'ITSM',         'TicketIcon',          1, '/itsm',             'itg-ticket',  'TICKET_READ',  TRUE),
  (3,  'ITSM_CREATE',   2,    '티켓 등록',    'PlusIcon',            0, '/itsm',             'itg-ticket',  'TICKET_CREATE',TRUE),
  (4,  'ITAM',          NULL, 'ITAM',         'BoxesIcon',           2, '/itam',             'itg-asset',   'ASSET_READ',   TRUE),
  (5,  'PMS',           NULL, 'PMS',          'FolderKanbanIcon',    3, '/pms',              NULL,          NULL,           TRUE),
  (6,  'COMMON',        NULL, '공통',         'LayersIcon',          4, '/common',           NULL,          NULL,           TRUE),
  (7,  'SYSTEM',        NULL, '시스템 관리',  'SettingsIcon',        5, '/system',           NULL,          'ROLE_ADMIN',   TRUE),
  (8,  'SYSTEM_USERS',  7,    '사용자',       'UsersIcon',           0, '/system/users',     NULL,          'USER_READ',    TRUE),
  (9,  'SYSTEM_DEPTS',  7,    '부서',         'NetworkIcon',         1, '/system/depts',     NULL,          'DEPT_READ',    TRUE),
  (10, 'SYSTEM_ROLES',  7,    '역할',         'ShieldIcon',          2, '/system/roles',     NULL,          'ROLE_ADMIN',   TRUE),
  (11, 'SYSTEM_MENUS',  7,    '메뉴',         'MenuIcon',            3, '/system/menus',     NULL,          'MENU_ADMIN',   TRUE),
  (12, 'SYSTEM_META',   7,    '메타 관리',    'FileJsonIcon',        4, '/system/meta',      NULL,          'META_READ',    TRUE),
  (13, 'SYSTEM_META_ED',7,    '메타 편집기',  'PencilRulerIcon',     5, '/system/meta-editor',NULL,         'META_EDIT',    TRUE)
ON CONFLICT (code) DO NOTHING;

SELECT setval('menu_id_seq', GREATEST((SELECT MAX(id) FROM menu), 1));
