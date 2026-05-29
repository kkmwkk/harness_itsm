-- Polestar10 ITG v2 — 샘플 자산 5건 시드 (PRD §9 M4 — ITAM 자산원장 e2e)
-- 본 스크립트는 컨테이너 최초 기동 시 06_itg_asset_meta.sql 다음(파일명 사전순)으로 자동 실행된다.
--   page_meta_id_at_registration 은 itg-asset-v1-1 을 참조하므로 06 시드가 선행되어야 한다 (FK fk_asset_meta).
-- 멱등성 유지: ON CONFLICT (asset_no) DO NOTHING.
-- 모든 값은 가상 샘플(ADR-011) — 실 운영 데이터(시리얼·서버명·사번 등) 없음.
--   상태 혼합: ACTIVE 3 · STORAGE 1 · RETIRED 1.

INSERT INTO asset (asset_no, name, asset_type, status, model, serial_no, category,
                   assignee_id, location, acquired_at, page_meta_id_at_registration)
VALUES
  ('AST-SAMPLE-001', '샘플 노트북 1', 'HARDWARE', 'ACTIVE',   'SAMPLE-MODEL-X1', 'SN-SAMPLE-00001', '노트북',   'assignee-sample-1', '본사 3층', '2026-01-15', 'itg-asset-v1-1'),
  ('AST-SAMPLE-002', '샘플 모니터',   'HARDWARE', 'ACTIVE',   'SAMPLE-MODEL-M2', 'SN-SAMPLE-00002', '모니터',   'assignee-sample-2', '본사 3층', '2026-02-01', 'itg-asset-v1-1'),
  ('AST-SAMPLE-003', '샘플 라이선스', 'LICENSE',  'ACTIVE',   NULL,              NULL,             '라이선스', 'assignee-sample-1', NULL,        '2026-03-10', 'itg-asset-v1-1'),
  ('AST-SAMPLE-004', '샘플 서버',     'HARDWARE', 'STORAGE',  'SAMPLE-SERVER-9', 'SN-SAMPLE-00004', '서버',     NULL,                  '창고',     '2025-11-20', 'itg-asset-v1-1'),
  ('AST-SAMPLE-005', '샘플 폐기 자산','HARDWARE', 'RETIRED',  'SAMPLE-MODEL-X0', 'SN-SAMPLE-00005', '노트북',   'assignee-sample-3', '폐기 보관', '2024-05-01', 'itg-asset-v1-1')
ON CONFLICT (asset_no) DO NOTHING;

UPDATE asset SET disposed_at = '2026-04-01'
 WHERE asset_no = 'AST-SAMPLE-005' AND disposed_at IS NULL;
