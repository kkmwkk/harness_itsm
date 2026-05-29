-- Polestar10 ITG v2 — itg-asset PageMeta 시드 (PRD §9 M4 — ITAM 자산원장 e2e)
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해
-- 05_asset.sql 다음(파일명 사전순)으로 자동 실행된다.
-- 멱등성 유지: ON CONFLICT (id) DO NOTHING — 기존 컨테이너 수동 적용 시 중복 INSERT 안 함.
--
-- itg-asset-v1-1 PUBLISHED 메타 (ITAM/PACKAGE)
--   grid.columns 의 field 명은 AssetSummary Record 필드명과 1:1 일치해야 한다
--   (다음 phase 의 DynamicGrid 가 accessorKey 로 사용):
--   assetNo · name · assetType · status · assigneeId · acquiredAt
--   form.fields 는 AssetCreateRequest 필드명과 일치 (pageGroupId 포함).

INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-asset-v1-1', 'ITAM 자산원장', 'ITAM', 'PACKAGE', 'itg-asset',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/assets",
  "grid": {
    "columns": [
      { "field": "assetNo",    "label": "자산번호", "type": "text",   "width": 140, "pinned": "left" },
      { "field": "name",       "label": "자산명",   "type": "text",   "flex": 1 },
      { "field": "assetType",  "label": "유형",     "type": "text",   "width": 110 },
      { "field": "status",     "label": "상태",     "type": "status", "width": 110 },
      { "field": "assigneeId", "label": "소유자",   "type": "text",   "width": 140 },
      { "field": "acquiredAt", "label": "취득일",   "type": "date",   "width": 120 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "name",        "label": "자산명",   "type": "text",     "required": true, "span": 2 },
      { "name": "assetType",   "label": "유형",     "type": "select",   "required": true, "options": [
          { "value": "HARDWARE", "label": "하드웨어" },
          { "value": "SOFTWARE", "label": "소프트웨어" },
          { "value": "LICENSE",  "label": "라이선스" },
          { "value": "CONTRACT", "label": "계약" },
          { "value": "SERVICE",  "label": "서비스" }
      ] },
      { "name": "category",    "label": "분류",     "type": "text" },
      { "name": "model",       "label": "모델",     "type": "text" },
      { "name": "serialNo",    "label": "시리얼",   "type": "text" },
      { "name": "assigneeId",  "label": "소유자",   "type": "user-picker" },
      { "name": "location",    "label": "위치",     "type": "text" },
      { "name": "acquiredAt",  "label": "취득일",   "type": "date" },
      { "name": "pageGroupId", "label": "메타 그룹", "type": "text",     "required": true,
        "helpText": "보통 itg-asset 고정. 수정하지 않음." }
    ]
  },
  "actions": [
    { "id": "create", "label": "등록", "type": "dialog-form" }
  ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;
