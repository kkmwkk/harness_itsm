# Phase 6 E2E 검증 보고서 — ITAM 자산 프런트엔드 메타 회전 복원 (PRD §9 M4)

> 본 보고서는 백엔드(Spring Boot 3 / Java 21) + 프런트엔드(Vue 3 / Vite dev) 를 동시에 부팅한 상태에서,
> **자산 등록 시점 메타 버전(`itg-asset-v1-1` DEPRECATED / `itg-asset-v1-2` PUBLISHED)에 따라 자산 상세 화면이
> 등록 당시 메타로 복원되는지**를 e2e 로 검증한 결과다. 운영 코드(Vue·composable·router·Entity·Service) 변경은 없다.
> 자동화 e2e(Playwright) 는 도입하지 않았고, §F 의 시각 검증은 부팅된 SPA 라우트 + 백엔드 API 응답 + 렌더링 코드 경로로 확인했다.

## 환경
- 검증 일시: 2026-05-29 (KST)
- Docker: `postgres:16` (`itg-postgres`, healthy, `localhost:5432` / `itgdb`)
- Backend: Spring Boot 3.x / Java 21, profile `local`, `http://localhost:8080` (actuator health `UP`)
- Frontend: Vue 3.5 / Vite 6 dev 서버, Node v24.16.0 / pnpm 11.4.0, `http://localhost:5173`
- 보안: phase 한정 `/api/meta/**`·`/api/assets/**` permitAll (JWT 도입 전)
- 정적 게이트: `vue-tsc` 0건 · `eslint --max-warnings=0` 0건 · build OK · vitest 50 passed

## 메타 회전 상태 (phase 5 §13 결과 유지 — 멱등성, no-op)

| 메타 id | majorVersion·minorVersion | metaStatus | active(`/api/meta/active/itg-asset`) |
|---------|---------------------------|------------|--------------------------------------|
| `itg-asset-v1-1` | 1 · 1 | **DEPRECATED** | — |
| `itg-asset-v1-2` | 1 · 2 | **PUBLISHED** | ✅ `itg-asset-v1-2` |

> `itg-asset-v1-2` 가 이미 PUBLISHED 이므로 §B 의 copy/publish 는 멱등 no-op 으로 통과.
> `itg-asset-v1-1` 은 ADR-006 의 자동 DEPRECATE 결과로 DEPRECATED 상태를 유지(ARCHIVED 로 전이하지 않음).

## 시나리오 결과 (§A~F — 전부 PASS)

| # | 단계 | 기대 | 결과 | 비고 |
|---|------|------|------|------|
| A | 백엔드 + 프런트 동시 부팅 | health UP · vite ready | PASS | actuator health 200, Tomcat 8080, Vite 5173 |
| A | 정적 게이트 (type-check·lint·build·test) | 전부 0건/통과 | PASS | vue-tsc 0 · eslint 0 · build OK · 50 tests passed |
| B | 메타 회전 상태 보장 (멱등) | v1-1 DEPRECATED·v1-2 PUBLISHED·active=v1-2 | PASS | 이미 회전됨 → no-op |
| C | SPA 라우트 `/itam` | 200 | PASS | 그리드 화면 (DynamicPage groupId=itg-asset) |
| C | SPA 라우트 `/itam/1` | 200 | PASS | 시드 자산 상세 (DetailPage) |
| C | SPA 라우트 `/itam/7` | 200 | PASS | 신규 자산 상세 (DetailPage) |
| C | SPA 라우트 `/system/meta` | 200 | PASS | 메타 버전 관리 화면 |
| D | 시드 자산 `AST-SAMPLE-001`(id=1) `/registration-meta` | `itg-asset-v1-1` · DEPRECATED | PASS | 등록 당시 메타 보존 |
| E | 신규 자산 `POST /api/assets` 생성 | `AST-00007`(id=7) · ACTIVE | PASS | `pageMetaIdAtRegistration=itg-asset-v1-2` |
| E | 신규 자산 `/registration-meta` | `itg-asset-v1-2` · PUBLISHED | PASS | 현 PUBLISHED 메타로 캡처 |
| F | 그리드 자산 공존 (`/itam`) | 시드 5 + AST-00006 + AST-00007 ≈ 7건 | PASS | totalElements=7 (아래 표) |
| F | 행 클릭 → `/itam/:id` 라우팅 | detailUrlTemplate 동작 | PASS | router meta `detailUrlTemplate:'/itam/{id}'` + `itam/:id(\d+)` 라우트 |
| F | `AST-SAMPLE-001` 상세 폼 복원 | v1-1 form.fields 로 렌더 | PASS | reg-meta v1-1 의 form fields 9개 반환, DetailPage 가 metaId 모드로 fetch |
| F | `Phase6 E2E 자산` 상세 폼 복원 | v1-2 form.fields 로 렌더 | PASS | reg-meta v1-2 의 form fields 9개 반환 |
| F | 버전 라벨 차이 | v1.1·DEPRECATED vs v1.2·PUBLISHED | PASS | DetailPage `statusColor()` 시맨틱 토큰 (neutral / success) |

### §F 그리드 자산 목록 (totalElements = 7)

| assetNo | 자산명 | assetType | status | 등록 메타(시점) |
|---------|--------|-----------|--------|------------------|
| AST-SAMPLE-001 | 샘플 노트북 1 | HARDWARE | ACTIVE | `itg-asset-v1-1` (DEPRECATED) |
| AST-SAMPLE-002 | 샘플 모니터 | HARDWARE | ACTIVE | `itg-asset-v1-1` (DEPRECATED) |
| AST-SAMPLE-003 | 샘플 라이선스 | LICENSE | ACTIVE | `itg-asset-v1-1` (DEPRECATED) |
| AST-SAMPLE-004 | 샘플 서버 | HARDWARE | STORAGE | `itg-asset-v1-1` (DEPRECATED) |
| AST-SAMPLE-005 | 샘플 폐기 자산 | HARDWARE | RETIRED | `itg-asset-v1-1` (DEPRECATED) |
| AST-00006 | E2E 생성 자산 | HARDWARE | RETIRED | (phase 5 E2E 생성분) |
| AST-00007 | Phase6 E2E 자산 | HARDWARE | ACTIVE | `itg-asset-v1-2` (PUBLISHED) |

> **핵심: 같은 `/itam` 그리드 안에서 v1-1(DEPRECATED) 등록 자산과 v1-2(PUBLISHED) 등록 자산이 공존한다.**
> 모든 값은 가상 샘플(ADR-011) — 실 운영 데이터 없음.

### §F 시각 검증 항목 (1~6) — 부팅 SPA + API + 코드 경로로 확인

1. 그리드: 총 7건(시드 5 + AST-00006 + AST-00007) — `GET /api/assets?size=50` totalElements=7 확인. ✅
2. status 컬럼: 자산 status(ACTIVE·STORAGE·RETIRED)는 `StatusBadge` fallback(중립색 + 원본 value)으로 표시 — 본 phase 범위. ✅
3. 행 클릭 → `/itam/:id`: router `itg-asset` 라우트 meta `detailUrlTemplate:'/itam/{id}'` + `_DynamicRoute` row-click 핸들러(step 2)로 `router.push`. ✅
4. `AST-SAMPLE-001`(id=1) 상세 → 메타 라벨 `v1.1 · DEPRECATED`(neutral 색). 폼은 v1-1 form.fields(`name·assetType·category·model·serialNo·assigneeId·location·acquiredAt·pageGroupId`)로 렌더 — `pageGroupId` helpText 포함. ✅
5. `Phase6 E2E 자산`(id=7) 상세 → 메타 라벨 `v1.2 · PUBLISHED`(success 색). 폼은 v1-2 form.fields 로 렌더. ✅
6. v1-1·v1-2 의 form.fields 가 동일(시드 copy 가 단순 복사) → 폼 시각 차이 없음, **버전 라벨만 다름**. 핵심 약속(시점 다른 메타로 복원) 검증됨. ✅

## 핵심 검증 사실

### PRD §5-2 활용 사례 이행 (이 phase 의 핵심)
- `AST-SAMPLE-*`(등록 시점 v1-1) 자산은 `/registration-meta` 가 **`itg-asset-v1-1`(DEPRECATED)** 를 반환하여 상세 화면이 v1-1 메타로 복원된다.
- `Phase6 E2E 자산`(등록 시점 v1-2) 자산은 **`itg-asset-v1-2`(PUBLISHED)** 로 렌더된다.
- 결과: **같은 그리드 안에서 시점이 다른 메타로 그려진 자산이 공존**한다. 양식이 버전 업되어도 과거 자산은 등록 당시 화면으로 복원 — 자산 이력 정합성이 메타 버전 변화에 영향받지 않음을 프런트 + 백엔드 동시 부팅 경로로 확인.

### DynamicPage metaId override 활용
- `DetailPage.vue` 가 `usePageMeta({ metaId: asset.pageMetaIdAtRegistration })`(step 0 의 metaId 모드)로 등록 시점 메타를 직접 fetch.
- 현 PUBLISHED(`/api/meta/active`)가 아니라 **등록 당시 버전**을 조회 → 자산 상세 폼 복원에 사용.

### DEPRECATED 메타도 단건 조회 가능 (ADR-006)
- `itg-asset-v1-1` 의 metaStatus 가 **DEPRECATED** 임에도 `/api/meta/itg-asset-v1-1` 및 `/api/assets/1/registration-meta` 로 정상 반환.
- 화면 노출용 라우팅(`/api/meta/active`)은 PUBLISHED(`itg-asset-v1-2`)만 반환하지만, 이력 복원용 단건 조회는 DEPRECATED 도 보존한다.

### `_DynamicRoute.vue` detailUrlTemplate — 도메인 하드코딩 없음 (ADR-004)
- 상세 라우팅은 router meta 의 `detailUrlTemplate`(`/itam/{id}`)을 `{id}` replace 하여 `router.push` — 도메인-중립.
- ITAM 전용 분기가 동적 렌더링 코어에 없으므로 No-code 원칙(메타가 화면을 결정) 유지.

## 한계 (다음 phase 범위)
- 자산 상세 폼 **저장**(PATCH/PUT) — 현재 read-only(이력 보기 의도, `onSubmit` no-op).
- AG Grid 인라인 편집 · 상세 페이지의 자산 이력 timeline UI.
- 자산 status 전용 뱃지 매핑(현재 `StatusBadge` fallback) — 별도 ADR 확장.
- 인증(JWT) — phase 한정 permitAll 상태.

## 산출물
- 본 보고서: `frontend/E2E_REPORT_PHASE6.md` (신규)
- 운영 코드 변경 없음 (step 0~2 산출물 그대로 검증)

> **시드 유지**: `itg-asset-v1-1`(DEPRECATED)·`itg-asset-v1-2`(PUBLISHED) 메타와 `AST-SAMPLE-001~005`,
> E2E 생성 자산(`AST-00006`·`AST-00007`)은 DELETE 하지 않는다.
