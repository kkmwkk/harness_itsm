-- Polestar10 ITG v2.1 — 자산 분류 트리 시드 (PRD §4-3 / ARCHITECTURE §14-3, M8)
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해
-- 파일명 사전순(12_itam_category.sql · 16_*.sql 다음)으로 자동 실행된다.
-- 멱등성 유지: ON CONFLICT (code) DO NOTHING.
--
-- path 는 보통 AssetCategoryService 가 계산·유지하나, 시드는 트리 구조가 고정이므로 직접 명시한다.
-- parent_code 가 자식보다 먼저 INSERT 되도록 정렬 (FK fk_ac_parent 충족).
-- form_meta_group_id 는 18_category_metas.sql 의 PageMeta group_id 와 1:1 (리프 분류에만 부여).

INSERT INTO asset_category (code, label, parent_code, path, form_meta_group_id, sort_order) VALUES
  ('HW',         '하드웨어',  NULL,  '/HW/',            NULL,                  10),
  ('HW_LAPTOP',  '노트북',    'HW',  '/HW/HW_LAPTOP/',  'itg-asset-hw-laptop', 11),
  ('HW_SERVER',  '서버',      'HW',  '/HW/HW_SERVER/',  'itg-asset-hw-server', 12),
  ('HW_MONITOR', '모니터',    'HW',  '/HW/HW_MONITOR/', 'itg-asset-hw-monitor',13),
  ('SW',         '소프트웨어', NULL,  '/SW/',            NULL,                  20),
  ('SW_LICENSE', '라이선스',  'SW',  '/SW/SW_LICENSE/', 'itg-asset-sw-license',21),
  ('CONTRACT',   '계약',      NULL,  '/CONTRACT/',      'itg-asset-contract',  30),
  ('SERVICE',    '서비스',    NULL,  '/SERVICE/',       'itg-asset-service',   40)
ON CONFLICT (code) DO NOTHING;
