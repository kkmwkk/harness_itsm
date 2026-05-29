-- Polestar10 ITG v2.1 — 자산 분류별 폼 PageMeta 시드 5종 (PRD §4-3 / ARCHITECTURE §3-4, M8)
-- 본 스크립트는 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약에 의해
-- 파일명 사전순(17_asset_categories.sql 다음)으로 자동 실행된다.
-- 멱등성 유지: ON CONFLICT (id) DO NOTHING.
--
-- 각 메타는 metaJson 에 categoryCode 를 포함한다 (ARCHITECTURE §3-4).
-- group_id 는 17_asset_categories.sql 의 asset_category.form_meta_group_id 와 1:1.
-- api 는 모두 /api/assets (DynamicGrid 는 AssetSummary 필드를 사용).
-- 모든 helpText 의 예시값은 가상 샘플(ADR-011) — IP 는 RFC 5737 192.0.2.x, 키·번호는 SAMPLE-.
-- 직접 PUBLISHED 로 INSERT — 각 group_id 가 고유하므로 자동 DEPRECATE 충돌 없음.

-- ============================================================================
-- 1) 노트북 (HW_LAPTOP) — itg-asset-hw-laptop-v1-1
-- ============================================================================
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-asset-hw-laptop-v1-1', '노트북 원장', 'ITAM', 'PACKAGE', 'itg-asset-hw-laptop',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/assets",
  "categoryCode": "HW_LAPTOP",
  "grid": {
    "columns": [
      { "field": "assetNo",    "label": "자산번호", "type": "text",   "width": 140, "pinned": "left" },
      { "field": "name",       "label": "자산명",   "type": "text",   "flex": 1 },
      { "field": "status",     "label": "상태",     "type": "status", "width": 110 },
      { "field": "assigneeId", "label": "사용자",   "type": "text",   "width": 140 },
      { "field": "acquiredAt", "label": "취득일",   "type": "date",   "width": 120 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "name",       "label": "자산명",   "type": "text",        "required": true, "span": 2 },
      { "name": "model",      "label": "모델",     "type": "text" },
      { "name": "serialNo",   "label": "시리얼",   "type": "text",
        "helpText": "예: SAMPLE-SN-0001" },
      { "name": "cpu",        "label": "CPU",      "type": "text" },
      { "name": "ramGb",      "label": "메모리(GB)","type": "number" },
      { "name": "storage",    "label": "저장장치", "type": "text" },
      { "name": "assigneeId", "label": "사용자",   "type": "user-picker" },
      { "name": "location",   "label": "위치",     "type": "text" },
      { "name": "acquiredAt", "label": "취득일",   "type": "date" }
    ]
  },
  "actions": [ { "id": "create", "label": "등록", "type": "dialog-form" } ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 2) 서버 (HW_SERVER) — itg-asset-hw-server-v1-1
-- ============================================================================
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-asset-hw-server-v1-1', '서버 원장', 'ITAM', 'PACKAGE', 'itg-asset-hw-server',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/assets",
  "categoryCode": "HW_SERVER",
  "grid": {
    "columns": [
      { "field": "assetNo",    "label": "자산번호", "type": "text",   "width": 140, "pinned": "left" },
      { "field": "name",       "label": "자산명",   "type": "text",   "flex": 1 },
      { "field": "status",     "label": "상태",     "type": "status", "width": 110 },
      { "field": "assigneeId", "label": "관리자",   "type": "text",   "width": 140 },
      { "field": "acquiredAt", "label": "취득일",   "type": "date",   "width": 120 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "name",       "label": "자산명",   "type": "text",   "required": true, "span": 2 },
      { "name": "model",      "label": "모델",     "type": "text" },
      { "name": "serialNo",   "label": "시리얼",   "type": "text",
        "helpText": "예: SAMPLE-SRV-0001" },
      { "name": "cpu",        "label": "CPU",      "type": "text" },
      { "name": "ramGb",      "label": "메모리(GB)","type": "number" },
      { "name": "storage",    "label": "저장장치", "type": "text" },
      { "name": "os",         "label": "운영체제", "type": "text" },
      { "name": "ip",         "label": "IP 주소",  "type": "text",
        "helpText": "예: 192.0.2.10 (RFC 5737 문서용 대역)" },
      { "name": "dataCenter", "label": "데이터센터","type": "text" }
    ]
  },
  "actions": [ { "id": "create", "label": "등록", "type": "dialog-form" } ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 3) 모니터 (HW_MONITOR) — itg-asset-hw-monitor-v1-1
-- ============================================================================
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-asset-hw-monitor-v1-1', '모니터 원장', 'ITAM', 'PACKAGE', 'itg-asset-hw-monitor',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/assets",
  "categoryCode": "HW_MONITOR",
  "grid": {
    "columns": [
      { "field": "assetNo",    "label": "자산번호", "type": "text",   "width": 140, "pinned": "left" },
      { "field": "name",       "label": "자산명",   "type": "text",   "flex": 1 },
      { "field": "status",     "label": "상태",     "type": "status", "width": 110 },
      { "field": "assigneeId", "label": "사용자",   "type": "text",   "width": 140 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "name",       "label": "자산명",   "type": "text",        "required": true, "span": 2 },
      { "name": "model",      "label": "모델",     "type": "text" },
      { "name": "serialNo",   "label": "시리얼",   "type": "text",
        "helpText": "예: SAMPLE-MON-0001" },
      { "name": "size",       "label": "화면 크기(인치)", "type": "number" },
      { "name": "resolution", "label": "해상도",   "type": "text" },
      { "name": "assigneeId", "label": "사용자",   "type": "user-picker" }
    ]
  },
  "actions": [ { "id": "create", "label": "등록", "type": "dialog-form" } ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 4) 라이선스 (SW_LICENSE) — itg-asset-sw-license-v1-1
-- ============================================================================
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-asset-sw-license-v1-1', '라이선스 원장', 'ITAM', 'PACKAGE', 'itg-asset-sw-license',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/assets",
  "categoryCode": "SW_LICENSE",
  "grid": {
    "columns": [
      { "field": "assetNo",    "label": "자산번호", "type": "text",   "width": 140, "pinned": "left" },
      { "field": "name",       "label": "라이선스명","type": "text",  "flex": 1 },
      { "field": "status",     "label": "상태",     "type": "status", "width": 110 },
      { "field": "acquiredAt", "label": "취득일",   "type": "date",   "width": 120 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "name",       "label": "라이선스명","type": "text",   "required": true, "span": 2 },
      { "name": "vendor",     "label": "공급사",   "type": "text" },
      { "name": "licenseKey", "label": "라이선스 키","type": "text",
        "helpText": "예: SAMPLE-KEY-XXXX-XXXX" },
      { "name": "seats",      "label": "사용 좌석 수","type": "number" },
      { "name": "startDate",  "label": "시작일",   "type": "date" },
      { "name": "endDate",    "label": "만료일",   "type": "date" }
    ]
  },
  "actions": [ { "id": "create", "label": "등록", "type": "dialog-form" } ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 5) 계약 (CONTRACT) — itg-asset-contract-v1-1
-- ============================================================================
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-asset-contract-v1-1', '계약 원장', 'ITAM', 'PACKAGE', 'itg-asset-contract',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/assets",
  "categoryCode": "CONTRACT",
  "grid": {
    "columns": [
      { "field": "assetNo",    "label": "자산번호", "type": "text",   "width": 140, "pinned": "left" },
      { "field": "name",       "label": "계약명",   "type": "text",   "flex": 1 },
      { "field": "status",     "label": "상태",     "type": "status", "width": 110 },
      { "field": "acquiredAt", "label": "체결일",   "type": "date",   "width": 120 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "name",       "label": "계약명",   "type": "text",   "required": true, "span": 2 },
      { "name": "vendor",     "label": "공급사",   "type": "text" },
      { "name": "contractNo", "label": "계약번호", "type": "text",
        "helpText": "예: SAMPLE-CTR-0001" },
      { "name": "startDate",  "label": "시작일",   "type": "date" },
      { "name": "endDate",    "label": "종료일",   "type": "date" },
      { "name": "amount",     "label": "계약 금액", "type": "number" }
    ]
  },
  "actions": [ { "id": "create", "label": "등록", "type": "dialog-form" } ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;
