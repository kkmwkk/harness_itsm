-- Polestar10 ITG v2.1 — ITSM 요청 유형·워크플로우 정의 시드 (PRD §3 / §4-2 / ARCHITECTURE §15, ADR-015)
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해
-- 파일명 사전순(11_itsm_workflow.sql · 14_*.sql 다음)으로 자동 실행된다.
-- 멱등성 유지: ON CONFLICT DO NOTHING — 기존 컨테이너 수동 적용 시 중복 INSERT 안 함.
--
-- 전제: ticket_request_type / workflow_definition 테이블은 11_itsm_workflow.sql 에 정의됨.
--   role 테이블은 10_auth.sql 에 정의됨 (uq_role_code UNIQUE(code)).
-- workflow_definition.steps 의 assignee_role_code 는 ROLE_USER / ROLE_IT_SUPPORT /
--   ROLE_TEAM_LEAD / ROLE_ADMIN 만 사용 (실재 role.code).

-- ============================================================================
-- ROLE_TEAM_LEAD 추가 (시드 9 의 권한·역할 확장 — 1차 승인자)
-- ============================================================================
INSERT INTO role (code, name, description) VALUES
  ('ROLE_TEAM_LEAD', '팀 리더', '1차 승인자')
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- 요청 유형 5종 (장애·서비스요청·변경·문제·문의)
--   form_meta_group_id 는 16_request_type_metas.sql 의 PageMeta group_id 와 1:1.
--   default_workflow_code 는 아래 workflow_definition.code 와 1:1.
-- ============================================================================
INSERT INTO ticket_request_type (code, label, form_meta_group_id, default_workflow_code, sla_minutes_default) VALUES
  ('INCIDENT',        '장애',       'itg-ticket-incident', 'WF_INCIDENT_STD', 240),
  ('SERVICE_REQUEST', '서비스 요청', 'itg-ticket-srvreq',   'WF_SERVICE_STD',  480),
  ('CHANGE',          '변경',       'itg-ticket-change',   'WF_CHANGE_STD',   1440),
  ('PROBLEM',         '문제',       'itg-ticket-problem',  'WF_PROBLEM_STD',  2880),
  ('QNA',             '문의',       'itg-ticket-qna',      'WF_QNA_STD',      480)
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- 워크플로우 정의 5종 (steps JSONB — ARCHITECTURE §15-1 단계 배열)
-- ============================================================================
INSERT INTO workflow_definition (code, name, version, steps) VALUES
  ('WF_INCIDENT_STD', '장애 표준 워크플로우', 1, '
   [{"index":0,"name":"접수",      "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":30, "allowed_actions":["FORWARD","COMPLETE"]},
    {"index":1,"name":"원인 분석", "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":120,"allowed_actions":["COMPLETE","REJECT"]},
    {"index":2,"name":"해결",      "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":480,"allowed_actions":["COMPLETE"]},
    {"index":3,"name":"종결 확인","assignee_role_code":"ROLE_USER",       "sla_minutes":null,"allowed_actions":["CONFIRM","REOPEN"]}]'::jsonb),
  ('WF_SERVICE_STD', '서비스 요청 표준', 1, '
   [{"index":0,"name":"접수",      "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":60, "allowed_actions":["FORWARD","COMPLETE"]},
    {"index":1,"name":"승인",      "assignee_role_code":"ROLE_TEAM_LEAD", "sla_minutes":240,"allowed_actions":["APPROVE","REJECT"]},
    {"index":2,"name":"처리",      "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":480,"allowed_actions":["COMPLETE"]},
    {"index":3,"name":"종결 확인","assignee_role_code":"ROLE_USER",       "sla_minutes":null,"allowed_actions":["CONFIRM","REOPEN"]}]'::jsonb),
  ('WF_CHANGE_STD',  '변경 관리 표준', 1, '
   [{"index":0,"name":"제안",     "assignee_role_code":"ROLE_USER",      "sla_minutes":null,"allowed_actions":["COMPLETE"]},
    {"index":1,"name":"1차 검토", "assignee_role_code":"ROLE_TEAM_LEAD", "sla_minutes":480, "allowed_actions":["APPROVE","REJECT"]},
    {"index":2,"name":"CAB 승인", "assignee_role_code":"ROLE_ADMIN",     "sla_minutes":1440,"allowed_actions":["APPROVE","REJECT"]},
    {"index":3,"name":"적용",     "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":480, "allowed_actions":["COMPLETE"]},
    {"index":4,"name":"종결",     "assignee_role_code":"ROLE_USER",      "sla_minutes":null,"allowed_actions":["CONFIRM"]}]'::jsonb),
  ('WF_PROBLEM_STD', '문제 관리 표준', 1, '
   [{"index":0,"name":"등록",      "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":120, "allowed_actions":["FORWARD"]},
    {"index":1,"name":"근본 원인", "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":1440,"allowed_actions":["COMPLETE"]},
    {"index":2,"name":"해결책 검토","assignee_role_code":"ROLE_TEAM_LEAD","sla_minutes":1440,"allowed_actions":["APPROVE","REJECT"]},
    {"index":3,"name":"종결",      "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":null, "allowed_actions":["COMPLETE"]}]'::jsonb),
  ('WF_QNA_STD',     '문의 표준', 1, '
   [{"index":0,"name":"접수",      "assignee_role_code":"ROLE_IT_SUPPORT","sla_minutes":60, "allowed_actions":["COMPLETE"]},
    {"index":1,"name":"종결 확인","assignee_role_code":"ROLE_USER",       "sla_minutes":null,"allowed_actions":["CONFIRM","REOPEN"]}]'::jsonb)
ON CONFLICT (code) DO NOTHING;
