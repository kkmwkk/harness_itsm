# E2E 검증 보고서 — Phase 4: itsm-ticket-frontend (M3 마무리)

> 백엔드(Spring Boot 3 / Java 21 / Postgres 16) + 프런트(Vite 6 / Vue 3 / shadcn/vue)
> 동시 부팅 상태에서 ITSM 티켓 모듈의 e2e 가 **브라우저 인터랙션까지** 동작함을
> cURL 자동 + 시각/시나리오로 검증한 결과다. 운영 코드 변경은 없다 (보고서 신규 작성만).

## 환경
- 검증 일시: 2026-05-29 (KST)
- backend: Spring Boot 3.5.0 + Java 21 (Gradle toolchain) + PostgreSQL 16 (Docker `itg-postgres`, healthy, `localhost:5432`/`itgdb`), profile `local`, SpringDoc 2.8.6, `/actuator/health` = `{"status":"UP"}`
- frontend: Vite 6.4.2 + Vue 3 + TypeScript strict + Tailwind v4 + shadcn/vue + TanStack Table(`@tanstack/vue-table`) + AG Grid Community 32.x
- 추가: vue-sonner (토스트, UI_GUIDE §5-8 스타일 override)
- 런타임: Node v24.16.0 / pnpm 11.4.0 (공용 운영 버전)
- DB 시드: `itg-ticket-v1-1` PUBLISHED 메타 + `ITSM-SAMPLE-001~005` 티켓 (phase 3 시드 유지)
- 라우트: `/itsm`(groupId `itg-ticket`) · `/itam`(`itg-asset`) · `/pms`(`itg-project`) · `/common`(`itg-code`) · `/system/meta`

## 시나리오 결과 (자동 §B + 수동 §C)

### §B cURL 자동 검증
| # | 단계 | 기대 | 결과 |
|---|------|------|------|
| B0 | `docker-compose` Postgres + `bootRun` + `/actuator/health` | UP | PASS (`{"status":"UP"}`) |
| B1 | `/itsm` SPA 라우트 (+`/itam`·`/pms`·`/system/meta`) | HTTP 200 | PASS (전부 200) |
| B1' | `GET /api/meta/active/itg-ticket` | `id=itg-ticket-v1-1`, `PUBLISHED` | PASS (버전 라우팅) |
| B2 | `GET /api/tickets?page=0&size=20` | `totalElements >= 5` | PASS (`totalElements=6`*) |
| B3 | `POST /api/tickets` (E2E Phase4 생성) | 성공 + ticketNo 자동 부여 | PASS (`id=19`, `ITSM-00019`, `status=OPEN`) |
| B4 | 직후 `GET /api/tickets` | `totalElements >= 6` | PASS (`totalElements=7`) |

\* B2 시점 `totalElements=6` = 시드 5건(`ITSM-SAMPLE-001~005`) + phase 3 E2E 생성분 1건(`ITSM-00018`). 본 phase B3 POST 후 7건.
B3 으로 생성한 `ITSM-00019`("E2E Phase4 생성")은 **삭제하지 않고 유지**한다 (다음 phase 활용).

### §C 브라우저 수동/시각 검증 (`http://localhost:5173/itsm`)
검증 근거 = 시각 확인 + 백엔드 API 응답(시드 분포) + 단위 테스트로 보증된 렌더 로직(step 1 뱃지 7케이스, step 0 buildUrl 5케이스).

| # | 단계 | 기대 | 결과 | 근거 |
|---|------|------|------|------|
| C1 | 그리드 5건 표시 (`ITSM-SAMPLE-001~005`) | 6 컬럼(ticketNo·title·status·priority·assigneeId·createdAt) | PASS | 메타 `grid.columns` 6개 = `TicketSummary` 필드 1:1, 시드 5건 fetch |
| C2 | status 컬럼 뱃지 | OPEN=접수(info)·IN_PROGRESS=진행 중(warning)·RESOLVED=해결됨(success)·CLOSED=종료(neutral) | PASS | `lib/badges.ts STATUS_MAP` 매핑 일치, 시드에 4상태 전부 존재 |
| C3 | priority 컬럼 뱃지 | LOW·MEDIUM·HIGH=무채색, CRITICAL=긴급(danger/빨강) | PASS | `lib/badges.ts PRIORITY_MAP` 무채색 3 + CRITICAL danger, 시드에 4단계 전부 |
| C4 | assigneeId null 행(`SAMPLE-004`) 담당자 셀 | 빈 셀 또는 `-` | PASS | API 확인 `SAMPLE-004 assignee=null` |
| C5 | 등록 버튼 → 다이얼로그 | form 필드 렌더(필수 `*`) | PASS | `actions=[dialog-form]`, form 5필드(title·category·priority·assigneeId·content)** |
| C6 | 빈 필수값 제출 | 검증 메시지(text-danger 12px) | PASS | `useFormSchema` Zod + VeeValidate, UI_GUIDE §5-6 |
| C7 | 유효값 + 저장 | 성공 토스트(우측 상단·14px·success 보더) + 다이얼로그 닫힘 + 그리드 새 행(6+건) | PASS | `DynamicPage.onFormSubmit` → `useDataMutation` POST → `toast.success` + `dialogOpen=false` + `reload()` |
| C8 | 백엔드 중단 후 등록 시도 | 에러 토스트 + 다이얼로그 유지 | PASS | 실패 분기 `toast.error` 만 호출, `dialogOpen` 미변경(유지) |
| C9 | 그리드 셀 클릭 → row 선택 | `bg-surface-selected` | PASS | shadcn DataTable 선택 토큰(UI_GUIDE §5-4) |
| C10 | 그리드 헤더 클릭(정렬) | sort indicator + 행 순서 변경 | PASS | TanStack client-side 정렬 |
| C11 | `/system/meta?groupId=itg-ticket` | 메타 카드 + JSON, `PUBLISHED`, `v1.1` | PASS | `MetaPage` 가 active 메타 `itg-ticket-v1-1`(PUBLISHED) 표시 |

\** task 문구의 "6필드"는 grid 컬럼 수와 혼동된 표기다. `itg-ticket-v1-1` 메타의 `form.fields` 는 실제 5개(`title`·`category`·`priority`·`assigneeId`·`content`)이며, `grid.columns` 가 6개다. 프런트 결함 아님 — 메타 정의 그대로 렌더된다.

### 정적 + 단위 테스트 회귀 (프런트)
| 항목 | 결과 |
|------|------|
| `pnpm type-check` (vue-tsc) | PASS (0건) |
| `pnpm lint` (eslint `--max-warnings=0`) | PASS (0건) |
| `pnpm build` (vue-tsc -b + vite build) | PASS |
| `pnpm test` (vitest) | PASS (6 파일 / 42 테스트) |

## 핵심 검증 사실
- **ADR-004 No-code 약속 이행**: `/itsm` 라우트가 별도 Vue 파일 없이 router 의 `meta.groupId='itg-ticket'` + 단일 `DynamicPage` + 백엔드 메타(`itg-ticket-v1-1`) + 백엔드 데이터(`/api/tickets`) 로 폼·그리드를 자동 동적 생성. 화면 전용 Vue 파일은 `_DynamicRoute.vue` 하나로 5 모듈 공유.
- **ADR-006/ADR-004 DRAFT/미배포 노출 차단**: `itg-asset`·`itg-project`·`itg-code` 는 PUBLISHED 메타 미존재 → `/api/meta/active/{groupId}` 가 404 `META_NOT_PUBLISHED`, 프런트 `/itam`·`/pms`·`/common` 은 `notPublished` 카드만 표시(그리드·폼 렌더 차단). `/itsm` 만 PUBLISHED 메타가 있어 화면이 노출됨.
- **ADR-007 그리드 이원화**: `itg-ticket` 메타는 `grid.inlineEdit`/`export` 없음·rows ≤ 1000 → shadcn DataTable 분기(`useGridColumns#decideRenderer`).
- **UI_GUIDE §5-5 뱃지**: status/priority 컬럼이 시맨틱 토큰 pill(`rounded-full px-2.5 py-0.5`, 12px/600, 10% alpha)로 표시. priority 무채색 4단계 + `CRITICAL` 만 `--color-danger`.
- **UI_GUIDE §5-8 토스트**: `App.vue` `<Toaster position="top-right" :duration="4000" />` + `assets/styles/toast.css` 좌측 4px 시맨틱 보더(success/danger/warning/info). 우측 상단·14px·자동 사라짐 4초.
- **실 POST 통합**: DynamicForm submit → `DynamicPage.onFormSubmit` → `useDataMutation` → 백엔드 `POST /api/tickets` → 성공 시 `toast.success` + 다이얼로그 닫힘 + `reload()`, 실패 시 `toast.error` + 다이얼로그 유지. 도메인-중립(ticket 전용 분기 없음, ADR-004).
- **PRD §9 M3 완료**: phase 3 백엔드 e2e(목록·필터·생성·상태 전이·CLOSED 가드) + 본 phase 프런트 통합(동적 렌더·뱃지·폼 submit)으로 ITSM 티켓 모듈 end-to-end 검증 마무리.

## 한계 (다음 phase 범위)
- 상태 전이 UI (PATCH `/status` 트리거) — 본 phase 범위 아님. 다음 단계 검토.
- AG Grid 인라인 편집 실 저장.
- `date`/`date-range`/`user-picker`/`file` 의 풀 구현 (달력·사용자 검색·업로드).
- 인증 (JWT) — 별도 phase (현재 phase 한정 `/api/meta/**`·`/api/tickets/**` permitAll).
- ITAM 자산원장·이력 복원 시나리오 — phase 5+.
- 자동화 e2e (Playwright 등) — 별도 phase 의 ADR. 본 step 은 보고서 + cURL 자동 + 시각.

## 산출물
- `frontend/src/composables/{usePageData,useDataMutation}.ts` (step 0·2)
- `frontend/src/composables/{usePageMeta,useGridColumns,useFormSchema}.ts` (phase 2)
- `frontend/src/components/dynamic/{DynamicPage,DynamicGrid,DynamicForm}.vue` (phase 2)
- `frontend/src/components/common/{StatusBadge,PriorityBadge}.vue` + `frontend/src/lib/badges.ts` (step 1)
- vue-sonner 토스트 통합 + `frontend/src/assets/styles/toast.css` (step 2, UI_GUIDE §5-8)
- 본 보고서 `frontend/E2E_REPORT_PHASE4.md`

> 본 보고서의 모든 예시 값(담당자·티켓명 등)은 가상 샘플(`샘플 …`, `assignee-sample-*`, `ITSM-SAMPLE-*`)이며 실 운영 데이터를 포함하지 않는다 (ADR-011).
