-- Polestar10 ITG v2 — itg-ticket PageMeta 시드 (PRD §9 M3 — ITSM 티켓 모듈 e2e)
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해
-- 02_ticket.sql 다음(파일명 사전순)으로 자동 실행된다.
-- 멱등성 유지: ON CONFLICT (id) DO NOTHING — 기존 컨테이너 수동 적용 시 중복 INSERT 안 함.
--
-- itg-ticket-v1-1 PUBLISHED 메타 (ITSM/PACKAGE)
--   grid.columns 의 field 명은 TicketSummary Record 필드명과 1:1 일치해야 한다
--   (다음 phase 의 DynamicGrid 가 accessorKey 로 사용):
--   ticketNo · title · status · priority · assigneeId · createdAt

INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-ticket-v1-1', 'ITSM 티켓 관리', 'ITSM', 'PACKAGE', 'itg-ticket',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/tickets",
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
      { "name": "title",     "label": "제목",     "type": "text",     "required": true, "span": 2 },
      { "name": "category",  "label": "분류",     "type": "select",   "options": [
          { "value": "BUG", "label": "버그" },
          { "value": "REQ", "label": "요청" },
          { "value": "QNA", "label": "문의" }
      ] },
      { "name": "priority",  "label": "우선순위", "type": "radio",    "options": [
          { "value": "LOW",      "label": "낮음" },
          { "value": "MEDIUM",   "label": "보통" },
          { "value": "HIGH",     "label": "높음" },
          { "value": "CRITICAL", "label": "긴급" }
      ] },
      { "name": "assigneeId","label": "담당자",   "type": "user-picker" },
      { "name": "content",   "label": "내용",     "type": "textarea", "span": 2 }
    ]
  },
  "actions": [
    { "id": "create", "label": "등록", "type": "dialog-form" }
  ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;
