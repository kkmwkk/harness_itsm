# E2E 검증 보고서 — Phase 2: dynamic-render

## 환경
- 검증 일시: 2026-05-29 07:27 (KST)
- 백엔드: Spring Boot 3.5.0 + Java 21.0.11 (LTS, Gradle foojay 툴체인 자동 프로비저닝) + PostgreSQL 16 (Docker `itg-postgres`, `localhost:5432`/`itgdb`), profile `local`, SpringDoc 2.8.6
- 프런트: Vite 6.4.2 + Vue 3 + TypeScript strict + Tailwind v4 + shadcn/vue + TanStack Table (`@tanstack/vue-table`) + AG Grid Community 32.x (`ag-grid-community`/`ag-grid-vue3`)
- Node v24.16.0 / pnpm 11.4.0
- 라우트: `/_dev/dynamic-page`, `/itsm`, `/itam`, `/pms`, `/common`, `/system/meta`

## 검증 대상 메타 (시드, PUBLISHED — 시나리오 종료 시 DELETE)
- `itg-mock-grid-v1-1` (ITSM/PACKAGE) — 5컬럼 그리드 + 4필드 폼 + `dialog-form` 액션. `inlineEdit` 없음 → shadcn DataTable.
- `itg-mock-aggrid-v1-1` (ITAM/PACKAGE) — 5컬럼 그리드 + 2필드 폼. `grid.inlineEdit: true` → AG Grid 분기.

## 시나리오 결과
| # | 단계 | 기대 | 결과 |
|---|------|------|------|
| 1 | 백엔드 + DB 부팅 (`docker-compose` Postgres + `bootRun` + `/actuator/health`) | UP | PASS (`{"status":"UP"}`) |
| 2 | 시드 INSERT (itg-mock-grid, itg-mock-aggrid) | 2건 PUBLISHED | PASS (`INSERT 0 1` ×2) |
| 3 | GET /api/meta/active/itg-mock-grid | 200, `id=itg-mock-grid-v1-1` | PASS |
| 4 | GET /api/meta/active/itg-mock-aggrid | 200, `id=itg-mock-aggrid-v1-1` | PASS |
| 5 | GET /api/meta/active/itg-mock-missing | 404 `META_NOT_PUBLISHED` | PASS (`success:false`) |
| 6 | DynamicPageSampler 진입 + 3 모드 동작 | 시각 PASS | PASS |
| 7 | itg-mock-grid → DataTable 18행 정상 렌더 | 헤더 sticky / 정렬 / 페이지네이션 | PASS |
| 8 | itg-mock-aggrid (inlineEdit) → AG Grid 분기 | AG Grid 25행 | PASS |
| 9 | itg-mock-missing → notPublished 카드 | 경고 표시 | PASS |
| 10 | 등록 버튼 → DynamicForm 다이얼로그 | 필드(제목·분류·우선순위·내용) 렌더, 필수 `*`, mock submit | PASS |
| 11 | /itsm·/itam·/pms·/common → notPublished 카드 | 메타 미존재이므로 정상 | PASS |
| 12 | 시드 정리 (DELETE) | 잔여 0 | PASS (`DELETE 2`, count `0`) |

### 정적 + 단위 테스트 회귀 (프런트)
| 항목 | 결과 |
|------|------|
| `pnpm type-check` (vue-tsc) | PASS (0건) |
| `pnpm lint` (eslint `--max-warnings=0`) | PASS (0건) |
| `pnpm build` (vue-tsc -b + vite build) | PASS |
| `pnpm test` (vitest) | PASS (3 파일 / 26 테스트) |

### SPA 라우트 200
`/_dev/dynamic-page`·`/itsm`·`/itam`·`/pms`·`/common`·`/system/meta` 전부 HTTP 200.

## 핵심 검증 사실
- **ADR-004 No-code 약속 이행**: 5 모듈 페이지(`/itsm`·`/itam`·`/pms`·`/common`·`/system/meta`)가 별도 Vue 파일 없이 router 의 `meta.groupId` 와 단일 `DynamicPage` 컴포넌트로 동적 생성됨. 검증 페이지(`DynamicPageSampler`)는 `DynamicPage` 에 `group-id` + mock `rows` 를 props 로 직접 전달.
- **ADR-007 그리드 이원화**: 메타의 `grid.inlineEdit` 플래그가 AG Grid 분기를 트리거(행 수와 무관). `itg-mock-grid`(18행, inlineEdit 없음)는 shadcn DataTable, `itg-mock-aggrid`(25행, `inlineEdit:true`)는 AG Grid. 분기 판정은 `useGridColumns#decideRenderer`(단위 테스트 6 케이스 보증).
- **ADR-006 DRAFT/미배포 노출 차단**: `itg-mock-missing`(미존재 그룹) 조회 시 백엔드는 404 `META_NOT_PUBLISHED`, 프런트는 `notPublished` 경고 카드만 표시하고 그리드·폼 렌더를 차단.
- **타입 안전**: `asPageMetaBody` type guard 가 깨진 메타 본문에 대해 `MetaBodyShapeError` 를 던지고 페이지는 명시적 에러 카드를 노출(페이지 자체는 깨지지 않음).
- **mock 데이터 경로**: 본 phase 의 `DynamicPage` 는 `props.rows` 가 주어지면 우선 사용하므로 backend `/api/_mock/...` 엔드포인트를 추가하지 않고도 그리드 렌더를 검증 가능(backend 무수정).

## 한계 (다음 phase 범위)
- 실 모듈 백엔드 (티켓·자산 CRUD): M3 phase.
- AG Grid 인라인 편집의 실 저장: 다음 phase ADR.
- `date`/`date-range`/`user-picker`/`file` 의 풀 구현(달력·사용자 검색·업로드): 다음 phase.
- `meta.api` 의 실 fetch + 서버 페이지네이션·필터: 다음 phase.
- 자동화 e2e(Playwright 등): 별도 phase 의 ADR (본 step 은 보고서 + 시각/시나리오 검증).

## 산출물
- `frontend/src/components/dynamic/{DynamicPage,DynamicGrid,DynamicForm}.vue`
- `frontend/src/composables/{usePageMeta,useGridColumns,useFormSchema}.ts`
- `frontend/src/lib/meta-body.ts` (type guard)
- `frontend/src/types/meta-body.ts`
- `frontend/src/views/_dev/{DataTableSampler,AgGridSampler,DynamicGridSampler,DynamicFormSampler,DynamicPageSampler}.vue`
- `frontend/E2E_REPORT_PHASE2.md` (본 파일)

> 본 보고서의 모든 예시 값(자산번호·사용자명·항목명 등)은 가상 샘플(`AST-…`, `샘플 …`)이며 실 운영 데이터를 포함하지 않는다 (ADR-011).
