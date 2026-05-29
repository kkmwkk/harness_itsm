-- Polestar10 ITG v2.1 — 요청 유형별 폼 PageMeta 시드 5종 (PRD §3 / ARCHITECTURE §3-4, M8)
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해
-- 파일명 사전순(15_itsm_seed.sql 다음)으로 자동 실행된다.
-- 멱등성 유지: ON CONFLICT (id) DO NOTHING.
--
-- 각 메타는 metaJson 에 requestTypeCode + workflowDefinitionCode 를 포함한다 (ARCHITECTURE §3-4).
-- group_id 는 15_itsm_seed.sql 의 ticket_request_type.form_meta_group_id 와 1:1.
-- api 는 모두 /api/tickets (DynamicGrid 는 TicketSummary 필드를 사용).
-- 직접 PUBLISHED 로 INSERT — 각 group_id 가 고유하므로 자동 DEPRECATE 충돌 없음 (03/06 시드 패턴 동일).

-- ============================================================================
-- 1) 장애 (INCIDENT) — itg-ticket-incident-v1-1
-- ============================================================================
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-ticket-incident-v1-1', '장애 접수', 'ITSM', 'PACKAGE', 'itg-ticket-incident',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/tickets",
  "requestTypeCode": "INCIDENT",
  "workflowDefinitionCode": "WF_INCIDENT_STD",
  "grid": {
    "columns": [
      { "field": "ticketNo",   "label": "티켓번호", "type": "text",     "width": 140, "pinned": "left" },
      { "field": "title",      "label": "제목",     "type": "text",     "flex": 1 },
      { "field": "status",     "label": "상태",     "type": "status",   "width": 120 },
      { "field": "priority",   "label": "우선순위", "type": "priority", "width": 110 },
      { "field": "assigneeId", "label": "담당자",   "type": "text",     "width": 140 },
      { "field": "createdAt",  "label": "등록일",   "type": "date",     "width": 120 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "title",          "label": "제목",       "type": "text",       "required": true, "span": 2 },
      { "name": "priority",       "label": "우선순위",   "type": "radio",      "required": true, "options": [
          { "value": "LOW",      "label": "낮음" },
          { "value": "MEDIUM",   "label": "보통" },
          { "value": "HIGH",     "label": "높음" },
          { "value": "CRITICAL", "label": "긴급" }
      ] },
      { "name": "assigneeId",     "label": "담당자",     "type": "user-picker" },
      { "name": "affectedSystem", "label": "영향 시스템", "type": "text" },
      { "name": "content",        "label": "장애 내용",  "type": "textarea",   "required": true, "span": 2 }
    ]
  },
  "actions": [ { "id": "create", "label": "등록", "type": "dialog-form" } ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 2) 서비스 요청 (SERVICE_REQUEST) — itg-ticket-srvreq-v1-1
-- ============================================================================
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-ticket-srvreq-v1-1', '서비스 요청', 'ITSM', 'PACKAGE', 'itg-ticket-srvreq',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/tickets",
  "requestTypeCode": "SERVICE_REQUEST",
  "workflowDefinitionCode": "WF_SERVICE_STD",
  "grid": {
    "columns": [
      { "field": "ticketNo",   "label": "티켓번호", "type": "text",     "width": 140, "pinned": "left" },
      { "field": "title",      "label": "제목",     "type": "text",     "flex": 1 },
      { "field": "status",     "label": "상태",     "type": "status",   "width": 120 },
      { "field": "priority",   "label": "우선순위", "type": "priority", "width": 110 },
      { "field": "assigneeId", "label": "담당자",   "type": "text",     "width": 140 },
      { "field": "createdAt",  "label": "등록일",   "type": "date",     "width": 120 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "title",    "label": "제목",     "type": "text",     "required": true, "span": 2 },
      { "name": "category", "label": "요청 분류", "type": "select",   "required": true, "options": [
          { "value": "ACCOUNT",  "label": "계정" },
          { "value": "HARDWARE", "label": "하드웨어" },
          { "value": "SOFTWARE", "label": "소프트웨어" },
          { "value": "ACCESS",   "label": "접근 권한" },
          { "value": "OTHER",    "label": "기타" }
      ] },
      { "name": "priority", "label": "우선순위", "type": "radio",    "options": [
          { "value": "LOW",      "label": "낮음" },
          { "value": "MEDIUM",   "label": "보통" },
          { "value": "HIGH",     "label": "높음" },
          { "value": "CRITICAL", "label": "긴급" }
      ] },
      { "name": "dueDate",  "label": "희망 완료일", "type": "date" },
      { "name": "content",  "label": "요청 내용",   "type": "textarea", "required": true, "span": 2 }
    ]
  },
  "actions": [ { "id": "create", "label": "등록", "type": "dialog-form" } ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 3) 변경 (CHANGE) — itg-ticket-change-v1-1
-- ============================================================================
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-ticket-change-v1-1', '변경 요청', 'ITSM', 'PACKAGE', 'itg-ticket-change',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/tickets",
  "requestTypeCode": "CHANGE",
  "workflowDefinitionCode": "WF_CHANGE_STD",
  "grid": {
    "columns": [
      { "field": "ticketNo",   "label": "티켓번호", "type": "text",     "width": 140, "pinned": "left" },
      { "field": "title",      "label": "제목",     "type": "text",     "flex": 1 },
      { "field": "status",     "label": "상태",     "type": "status",   "width": 120 },
      { "field": "priority",   "label": "우선순위", "type": "priority", "width": 110 },
      { "field": "assigneeId", "label": "담당자",   "type": "text",     "width": 140 },
      { "field": "createdAt",  "label": "등록일",   "type": "date",     "width": 120 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "title",        "label": "변경 제목",   "type": "text",     "required": true, "span": 2 },
      { "name": "impact",       "label": "영향도",      "type": "select",   "required": true, "options": [
          { "value": "LOW",    "label": "낮음" },
          { "value": "MEDIUM", "label": "보통" },
          { "value": "HIGH",   "label": "높음" }
      ] },
      { "name": "riskLevel",    "label": "위험도",      "type": "select",   "required": true, "options": [
          { "value": "LOW",    "label": "낮음" },
          { "value": "MEDIUM", "label": "보통" },
          { "value": "HIGH",   "label": "높음" }
      ] },
      { "name": "plannedStart", "label": "변경 예정 시작", "type": "date" },
      { "name": "plannedEnd",   "label": "변경 예정 종료", "type": "date" },
      { "name": "rollback",     "label": "롤백 계획",   "type": "textarea", "span": 2 },
      { "name": "content",      "label": "변경 내용",   "type": "textarea", "required": true, "span": 2 }
    ]
  },
  "actions": [ { "id": "create", "label": "등록", "type": "dialog-form" } ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 4) 문제 (PROBLEM) — itg-ticket-problem-v1-1
-- ============================================================================
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-ticket-problem-v1-1', '문제 등록', 'ITSM', 'PACKAGE', 'itg-ticket-problem',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/tickets",
  "requestTypeCode": "PROBLEM",
  "workflowDefinitionCode": "WF_PROBLEM_STD",
  "grid": {
    "columns": [
      { "field": "ticketNo",   "label": "티켓번호", "type": "text",     "width": 140, "pinned": "left" },
      { "field": "title",      "label": "제목",     "type": "text",     "flex": 1 },
      { "field": "status",     "label": "상태",     "type": "status",   "width": 120 },
      { "field": "priority",   "label": "우선순위", "type": "priority", "width": 110 },
      { "field": "assigneeId", "label": "담당자",   "type": "text",     "width": 140 },
      { "field": "createdAt",  "label": "등록일",   "type": "date",     "width": 120 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "title",           "label": "문제 제목",   "type": "text",     "required": true, "span": 2 },
      { "name": "linkedIncidents", "label": "연관 장애",   "type": "text",
        "helpText": "연관된 장애 티켓번호를 쉼표로 구분 (예: SAMPLE-INC-001)" },
      { "name": "priority",        "label": "우선순위",    "type": "radio",    "options": [
          { "value": "LOW",      "label": "낮음" },
          { "value": "MEDIUM",   "label": "보통" },
          { "value": "HIGH",     "label": "높음" },
          { "value": "CRITICAL", "label": "긴급" }
      ] },
      { "name": "content",         "label": "문제 내용",   "type": "textarea", "required": true, "span": 2 }
    ]
  },
  "actions": [ { "id": "create", "label": "등록", "type": "dialog-form" } ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 5) 문의 (QNA) — itg-ticket-qna-v1-1
-- ============================================================================
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-ticket-qna-v1-1', '문의', 'ITSM', 'PACKAGE', 'itg-ticket-qna',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/tickets",
  "requestTypeCode": "QNA",
  "workflowDefinitionCode": "WF_QNA_STD",
  "grid": {
    "columns": [
      { "field": "ticketNo",   "label": "티켓번호", "type": "text",   "width": 140, "pinned": "left" },
      { "field": "title",      "label": "제목",     "type": "text",   "flex": 1 },
      { "field": "status",     "label": "상태",     "type": "status", "width": 120 },
      { "field": "assigneeId", "label": "담당자",   "type": "text",   "width": 140 },
      { "field": "createdAt",  "label": "등록일",   "type": "date",   "width": 120 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "title",    "label": "제목",     "type": "text",     "required": true, "span": 2 },
      { "name": "category", "label": "문의 분류", "type": "select",   "options": [
          { "value": "USAGE",   "label": "사용법" },
          { "value": "ACCOUNT", "label": "계정" },
          { "value": "ETC",     "label": "기타" }
      ] },
      { "name": "content",  "label": "문의 내용", "type": "textarea", "required": true, "span": 2 }
    ]
  },
  "actions": [ { "id": "create", "label": "등록", "type": "dialog-form" } ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;
