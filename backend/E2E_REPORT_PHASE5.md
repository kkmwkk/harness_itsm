# Phase 5 E2E 검증 보고서 — ITAM 자산원장 모듈 (PRD §9 M4)

> 본 보고서는 `itg-asset` PUBLISHED 메타 + 샘플 자산 5건 시드 위에서 ITAM 자산 백엔드의
> end-to-end 동작(목록·필터·생성·상태 전이 매트릭스·RETIRED 가드·이력 메타 보존)을
> cURL 시나리오로 검증한 결과다. 운영 코드(Entity·Service·Controller·Config) 변경은 없다.

## 환경
- 검증 일시: 2026-05-29 (KST)
- Docker 이미지: `postgres:16` (`itg-postgres`, healthy)
- Spring Boot: 3.x / Java 21 (Gradle toolchain `languageVersion = 21`)
- profile: `local` (`SPRING_PROFILES_ACTIVE=local`)
- DB 접속: `localhost:5432` / `itgdb` / `itg`
- OpenAPI: SpringDoc, OAS 3.1.0
- 보안: phase 한정 `/api/meta/**`·`/api/assets/**` permitAll (JWT 도입 전, step 2 SecurityConfig)

## 검증 메타 — `itg-asset-v1-1` → (시나리오 13 후) `itg-asset-v1-2`

| 항목 | 값 |
|------|-----|
| id (초기) | `itg-asset-v1-1` (PUBLISHED) |
| title | ITAM 자산원장 |
| systemType / packageType | `ITAM` / `PACKAGE` |
| groupId · major · minor | `itg-asset` · 1 · 1 |
| api | `/api/assets` (AssetController 베이스 경로와 일치) |
| grid.columns (6) | `assetNo` · `name` · `assetType` · `status` · `assigneeId` · `acquiredAt` — **`AssetSummary` 필드명과 1:1 일치** |
| form.fields (9) | `name`(text·span2·required) · `assetType`(select·required, 5옵션) · `category`(text) · `model`(text) · `serialNo`(text) · `assigneeId`(user-picker) · `location`(text) · `acquiredAt`(date) · `pageGroupId`(text·required, helpText) |
| actions | `create` (dialog-form) |

> grid 컬럼 `field` 명이 `AssetSummary` Record(`id·assetNo·name·assetType·status·assigneeId·acquiredAt`)의
> 필드명과 일치, form `name` 이 `AssetCreateRequest`(`name·assetType·model·serialNo·category·assigneeId·location·acquiredAt·pageGroupId`)와
> 일치 — 다음 phase 의 `DynamicGrid`/`DynamicForm` 이 그대로 바인딩한다.

## 시드 자산 5건 (`AST-SAMPLE-001~005`, 시드 유지)
| asset_no | 자산명 | assetType | status | category | assignee | 비고 |
|----------|--------|-----------|--------|----------|----------|------|
| AST-SAMPLE-001 | 샘플 노트북 1 | HARDWARE | ACTIVE | 노트북 | assignee-sample-1 | |
| AST-SAMPLE-002 | 샘플 모니터 | HARDWARE | ACTIVE | 모니터 | assignee-sample-2 | |
| AST-SAMPLE-003 | 샘플 라이선스 | LICENSE | ACTIVE | 라이선스 | assignee-sample-1 | model/serial null |
| AST-SAMPLE-004 | 샘플 서버 | HARDWARE | STORAGE | 서버 | (null) | 창고 보관 |
| AST-SAMPLE-005 | 샘플 폐기 자산 | HARDWARE | RETIRED | 노트북 | assignee-sample-3 | `disposed_at` UPDATE 보정 |

> 상태 혼합(ACTIVE·STORAGE·RETIRED) + 유형 혼합(HARDWARE·LICENSE) 포함. 모든 값은 가상 샘플(ADR-011) — 실 운영 데이터 없음.

## 시나리오 결과 (15 단계 — 전부 PASS)
| # | 단계 | HTTP | 결과 | 비고 |
|---|------|------|------|------|
| 1 | `GET /api/meta/active/itg-asset` | 200 | PASS | `id=itg-asset-v1-1` (PUBLISHED 라우팅) |
| 2 | `GET /api/assets?page=0&size=20` | 200 | PASS | `totalElements = 5` |
| 3 | `GET /api/assets?status=ACTIVE` | 200 | PASS | content status 집합 = `{ACTIVE}` (필터 동작) |
| 4 | `POST /api/assets` 신규 생성 | 201 | PASS | `data.id=6` 반환 |
| 5 | `GET /api/assets/{id}` | 200 | PASS | `assetNo` ↔ `^AST-\d{5}$`(AST-00006), `pageMetaIdAtRegistration=itg-asset-v1-1`, `status=ACTIVE` |
| 6 | `GET /api/assets/{id}/registration-meta` | 200 | PASS | `id=itg-asset-v1-1`, `metaStatus=PUBLISHED` |
| 7 | `PATCH .../{id}/status` ACTIVE→STORAGE | 200 | PASS | `status=STORAGE` |
| 8 | `PATCH .../{id}/status` STORAGE→REPLACED | 400 | PASS | 매트릭스 외 전이 거부 |
| 9 | `PATCH .../{id}/status` STORAGE→ACTIVE | 200 | PASS | 복귀 허용 |
| 10 | `PATCH .../{id}/status` ACTIVE→RETIRED | 200 | PASS | `status=RETIRED`, `disposedAt=2026-05-29` 자동 set |
| 11 | `PATCH .../{id}` (RETIRED 후 본문 수정) | 400 | PASS | RETIRED 가드 |
| 12 | `PATCH .../{id}/assign` (RETIRED 후 할당) | 400 | PASS | RETIRED 가드 |
| 13 | 메타 v1-2 copy → publish (v1-1 자동 DEPRECATE), 등록 메타 재조회 | 200 | PASS | `/registration-meta` 가 여전히 `itg-asset-v1-1` 반환, `metaStatus=DEPRECATED` — **PRD §5-2 핵심** |
| 14 | `GET /api/meta/active/itg-asset` | 200 | PASS | `id=itg-asset-v1-2` (현 시점 등록은 v1-2 캡처) |
| 15 | OpenAPI 산출 (`/v3/api-docs(.yaml)`) | 200 | PASS | json 35,441B · yaml 45,750B, asset 경로 6개 |

## 핵심 검증 사실

### PRD §5-2 — 자산 이력 메타 보존 (이 phase 의 핵심)
자산 등록 시점에 `AssetService.create` 가 `MetaService.getActive` 로 그 시점 PUBLISHED 메타 ID 를
`pageMetaIdAtRegistration` 컬럼에 캡처한다(시나리오 5). 이후 메타가 v1-2 publish 로 인해
`itg-asset-v1-1` → `DEPRECATED` 로 전이되어도(시나리오 13), 기존 자산의 `/registration-meta` 는
**여전히 `itg-asset-v1-1`(DEPRECATED)** 을 반환한다. 즉 양식이 버전 업되어도 과거 자산은 등록 당시 화면으로
복원 가능 — 자산 이력 정합성이 메타 버전 변화에 영향받지 않음을 실 HTTP 경로로 확인.

### 상태 전이 매트릭스 (Entity 도메인 메서드 `changeStatus`, step 0)
- **허용**: `ACTIVE → STORAGE`(7) · `STORAGE → ACTIVE`(9) · `ACTIVE → RETIRED`(10, `disposedAt` 자동 set).
- **거부(400)**: `STORAGE → REPLACED`(8, 매트릭스 외) · RETIRED 이후 모든 상태 전이.
- 모든 거부는 `IllegalStateException` → `GlobalExceptionHandler` → `400 / errorCode=INVALID_REQUEST`.

### RETIRED 가드 (변경 엔드포인트 거부)
| 엔드포인트 | RETIRED 자산 결과 |
|------------|------------------|
| `PATCH /{id}` (본문 수정) | 400 (11) |
| `PATCH /{id}/assign` (담당자) | 400 (12) |
| `PATCH /{id}/status` (재전이) | 400 (시나리오 10 이후 종료 상태) |

RETIRED 자산은 어떤 변경도 받지 않음 — Entity 도메인 가드(step 0)가 전 경로에서 일관 적용됨을 확인.

### `asset_no` 자동 부여
신규 생성 자산의 `assetNo` 가 `^AST-\d{5}$`(예: `AST-00006`) 패턴으로 자동 부여됨 확인 —
`Service.create` 의 save 후 `AST-{id5}` 부여 로직(step 1) 정상 동작. 시드의 `AST-SAMPLE-NNN` 과 구분됨.

### 메타 v1-2 PUBLISHED 전환 시 v1-1 자동 DEPRECATE (ADR-006)
`POST /api/meta/itg-asset-v1-1/copy` 가 `itg-asset-v1-2`(DRAFT, minor+1) 를 생성하고,
`PATCH /api/meta/itg-asset-v1-2/publish` 시 동일 `groupId` 의 기존 PUBLISHED(`v1-1`)가 자동 `DEPRECATED` 로 전이됨을
DB 상태(`page_meta` 조회)와 `/api/meta/active/itg-asset`(=v1-2 반환)으로 이중 확인.

## 한계 (다음 phase 범위)
- 프런트엔드 통합(`DynamicPage`/`DynamicForm`/`DynamicGrid`) 및 실제 폼 submit 동작.
- 자산 이력 화면 복원 UI — `/registration-meta` 응답으로 과거 양식을 렌더링하는 화면.
- 담당자(`user-picker`) 검색 UI · 옵션 소스.

## 산출물
- SQL 시드 2종: `sql/init/06_itg_asset_meta.sql`(신규) · `sql/init/07_sample_assets.sql`(신규)
- OpenAPI: `backend/openapi/itg-api-spec.json` (35,441B) · `backend/openapi/itg-api-spec.yaml` (45,750B) — asset 경로 6개 등록
- 본 보고서: `backend/E2E_REPORT_PHASE5.md`

> **시드 유지**: `itg-asset-v1-1`(DEPRECATED)·`itg-asset-v1-2`(PUBLISHED) 메타와 `AST-SAMPLE-001~005` 자산,
> E2E 생성 자산(`AST-00006`)은 DELETE 하지 않는다 — 다음 phase(frontend 통합)가 사용한다.
