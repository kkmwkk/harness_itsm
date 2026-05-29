-- Polestar10 ITG v2 — 샘플 티켓 5건 시드 (PRD §9 M3 — ITSM 티켓 모듈 e2e)
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해
-- 03_itg_ticket_meta.sql 다음(파일명 사전순)으로 자동 실행된다.
-- 멱등성 유지: ON CONFLICT (ticket_no) DO NOTHING.
--
-- 시드 ticket_no 는 ITSM-SAMPLE-001~005 (Service.create 가 부여하는 ITSM-{id5} 와 구분).
-- 운영 코드는 SAMPLE 패턴을 인식하지 않는다 — 단순 표시용.
-- 상태 혼합: OPEN / IN_PROGRESS / RESOLVED / CLOSED.
-- 민감정보 금지(ADR-011): 담당자는 가상 샘플(assignee-sample-*), 제목·본문은 가상 문구.

INSERT INTO ticket (ticket_no, title, content, priority, status, category, assignee_id)
VALUES
  ('ITSM-SAMPLE-001', '샘플 티켓 1 — 로그인 오류',     '재현 절차 …',  'HIGH',     'OPEN',        'BUG', 'assignee-sample-1'),
  ('ITSM-SAMPLE-002', '샘플 티켓 2 — 기능 요청',       '신규 메뉴 …',  'MEDIUM',   'IN_PROGRESS', 'REQ', 'assignee-sample-2'),
  ('ITSM-SAMPLE-003', '샘플 티켓 3 — 문의',           '운영 시간 …',  'LOW',      'RESOLVED',    'QNA', 'assignee-sample-1'),
  ('ITSM-SAMPLE-004', '샘플 티켓 4 — 장애 보고',       '서비스 영향 …','CRITICAL', 'OPEN',        'BUG', NULL),
  ('ITSM-SAMPLE-005', '샘플 티켓 5 — 종료된 항목',     '완료 …',       'MEDIUM',   'CLOSED',      'REQ', 'assignee-sample-3')
ON CONFLICT (ticket_no) DO NOTHING;

-- CLOSED 시드의 closed_at 보정 (도메인 메서드 경유가 아닌 직접 INSERT 이므로 명시 set)
UPDATE ticket SET closed_at = created_at + INTERVAL '1 hour'
 WHERE ticket_no = 'ITSM-SAMPLE-005' AND closed_at IS NULL;
