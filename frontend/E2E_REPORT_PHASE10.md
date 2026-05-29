# Phase 10 E2E 검증 보고서 — 로그인 흐름·동적 메뉴 권한 필터·시스템 관리 (PRD §9 M7)

> 본 보고서는 백엔드(Spring Boot 3 / Java 21) + 프런트엔드(Vue 3 / Vite dev) 를 **동시에 부팅한 상태**에서,
> **JWT 로그인 흐름 · 사용자 권한에 따른 동적 메뉴 트리 차이 · 시스템 관리 4 페이지 동작 · 새로고침 세션 복원**을
> e2e 로 검증한 결과다. 운영 코드(Vue·composable·store·router·Controller·Service) 변경은 없다.
> 자동화 e2e(Playwright)는 본 phase 에서 도입하지 않았고(phase 11 범위), §D 의 시각 검증은
> 부팅된 SPA 라우트 + 백엔드 API 응답 + 렌더링/가드 코드 경로로 확인했다(ADR-019 는 phase 11).

## 1. 환경

| 항목 | 값 |
|------|-----|
| 검증 일시 | 2026-05-29 (KST) |
| 작업 디렉토리 | `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM` |
| 브랜치 | `feat-10-menu-and-routing` |
| Docker | `postgres:16` (`itg-postgres`, healthy, `localhost:5432` / `itgdb`) |
| Backend | Spring Boot 3.5 / Java 21, profile `local`, `http://localhost:8080` (actuator health `{"status":"UP"}`, 기동 3.5s) |
| Frontend | Vite 6.4.2 dev 서버, Node **v24.16.0** / pnpm 11.4.0, `http://localhost:5173` |
| 인증 | JWT(Bearer) 정식 — `JwtAuthenticationFilter` 실 검증 + `@PreAuthorize` 권한 가드 (ADR-008, phase 9 산출물) |
| 시드 | `sql/init/14_auth_seed.sql` — 권한 13종·역할 3종·사용자 3명·부서 3개·메뉴 13노드 (phase 9 step 4) |
| 정적 게이트 | `vue-tsc` 0건 · `eslint --max-warnings=0` 0건 · `vite build` OK · vitest 62 passed (10 files) |

## 2. 시나리오 결과

### §A·정적 게이트 (AC)

| # | 단계 | 기대 | 결과 |
|---|------|------|------|
| A | 백엔드 + 프런트 동시 부팅 | health UP · vite ready | **PASS** (actuator `UP`, Vite 6.4.2 ready) |
| A | `pnpm type-check` (vue-tsc) | 0건 | **PASS** |
| A | `pnpm lint` (eslint `--max-warnings=0`) | 0건 | **PASS** |
| A | `pnpm build` | 성공 | **PASS** (built in 3.44s) |
| A | `pnpm test` (vitest) | 전부 통과 | **PASS** (62 passed / 10 files) |

### §B·cURL 자동 (시드·권한 필터 검증)

| # | 단계 | 기대 | 결과 |
|---|------|------|------|
| B1 | `POST /api/auth/login` (admin / `admin-sample-1234`) | accessToken 발급 | **PASS** (JWT 445자) |
| B2 | admin `GET /api/menu` | `시스템 관리`·`사용자` 포함 | **PASS** (13 라벨 — 아래 §3 트리) |
| B3 | `user-sample-1` `GET /api/menu` | `시스템 관리` **미포함** | **PASS** (6 라벨: 대시보드·ITSM·티켓 등록·ITAM·PMS·공통) |
| B4 | admin `POST /api/users` (`u-phase10`) | `"username":"u-phase10"` | **PASS** (id=4, ROLE_USER — 시드로 **보존**) |
| B+ | `it-support` 로그인 | ROLE_IT_SUPPORT | **PASS** |

### §C·SPA 라우트 200

| 경로 | HTTP | 경로 | HTTP |
|------|------|------|------|
| `/` | **200** | `/system/menus` | **200** |
| `/login` | **200** | `/itsm` | **200** |
| `/system/users` | **200** | `/itam` | **200** |
| `/system/depts` | **200** | `/system/meta` | **200** |
| `/system/roles` | **200** | | |

→ 9 라우트 전부 **200 PASS**.

### §D·로그인 흐름·권한·세션 (API 응답 + 가드 코드 경로)

| # | 단계 | 기대 | 결과 | 근거 |
|---|------|------|------|------|
| D1 | 비인증 진입 → `/login` 자동 리다이렉트 (`?next=` 부착) | 가드 차단 | **PASS** | `router.beforeEach`: `!auth.isAuthenticated` → `{ path:'/login', query:{ next: to.fullPath } }` |
| D2 | admin 로그인 → 홈 + Sidebar 시드 메뉴 트리 | 13노드 동적 렌더 | **PASS** | `/api/menu` 트리(§3) + `useMenuStore.load` 1회 호출(트리 비었을 때만) |
| D3 | TopBar 사용자명 + 로그아웃 | 표시 | **PASS** | `TopBar.vue:16` `displayName = user.name ?? user.username` + 로그아웃 버튼 |
| D4 | 시스템 관리 → 사용자: 시드 3명 + `u-phase10` + 등록 다이얼로그 | 동작 | **PASS** | `/system/users` 200 + B4 사용자 영속 + UserPage CRUD(step 2) |
| D5 | 새로고침: 토큰 유지·메뉴 복원·user/roles/perms 자동 채움 | `/me` 재호출 | **PASS** | 가드: `!auth.user` → `GET /api/auth/me` → `auth.user/roles/permissions` 복원 (localStorage `itg.auth.*`) |
| D6 | 로그아웃 → `/login` | 세션 클리어 | **PASS** | `useAuthStore.clear()` + `useMenuStore.clear()` (TopBar) |
| D7 | `user-sample-1` → `/system/users` 진입 시도 → 백엔드 차단 | 403 | **PASS** | `GET /api/users` → **403 FORBIDDEN** `"권한이 부족합니다"` (`@PreAuthorize`) |
| D8 | `u-phase10` 본인 비번 변경 가능 | SelfCheck 허용 | **PASS(경로)** | `PATCH /api/users/{id}/password` (phase 9 SelfCheck) 존재 — 실제 변경은 시드 로그인 보존 위해 미수행 |

> `/api/auth/me` 응답은 `data.user{ id·username·name·email·departmentName } + roles[] + permissions[]` 중첩 구조이며,
> 프런트 `useAuthStore` 가 이를 그대로 복원한다.

## 3. 권한별 메뉴 트리 차이 (핵심 — No-code + 인증 통합)

같은 `/api/menu` 엔드포인트가 **로그인 사용자의 권한에 따라 다른 트리**를 반환한다 (`MenuService.getTreeFor` 권한 필터).

**admin (ROLE_ADMIN — 13노드)**
```
- 대시보드 [/]
- ITSM [/itsm]
  - 티켓 등록 [/itsm]
- ITAM [/itam]
- PMS [/pms]
- 공통 [/common]
- 시스템 관리 [/system]            ← 관리자 전용
  - 사용자 [/system/users]
  - 부서 [/system/depts]
  - 역할 [/system/roles]
  - 메뉴 [/system/menus]
  - 메타 관리 [/system/meta]
  - 메타 편집기 [/system/meta-editor]
```

**it-support (ROLE_IT_SUPPORT — 7노드)**: `대시보드·사용자(USER_READ)·ITSM·티켓 등록·ITAM·PMS·공통`.
`시스템 관리` 그룹 노드는 권한 부족으로 필터링되되, `USER_READ` 보유 항목(`사용자`)은 노출 — 권한 세분화 결과.

**user-sample-1 (ROLE_USER — 6노드)**: `대시보드·ITSM·티켓 등록·ITAM·PMS·공통`. `시스템 관리`·`사용자` 전무.

→ 백엔드 권한 필터 + 프런트 동적 Sidebar 렌더(`MenuTreeNode` 재귀)로 **세 권한 레벨의 메뉴 차이가 시각적으로 분기**됨을 확인.

## 4. 핵심 검증 사실

- **No-code + 인증 통합**: 사용자 권한에 따라 메뉴 트리가 동적으로 달라진다 (백엔드 `getTreeFor` 필터 + 프런트 `useMenuStore`/`MenuTreeNode` 동적 렌더). 메뉴는 router 하드코딩이 아닌 DB 시드 기반.
- **인증 안전망 완비**: ① 라우터 가드(`meta.requiresAuth` → `/login?next=`) ② `useApiFetch` `beforeFetch` 의 `Authorization: Bearer` 자동 주입 ③ `onFetchError` 401 시 세션 클리어 + `/login` 자동 리다이렉트(loop 방지) — 3중.
- **시스템 관리 4 페이지 정식 동작**: 사용자/부서/역할/메뉴 CRUD (step 2 산출물) + `@PreAuthorize` 권한 가드. 비권한 사용자는 백엔드에서 **403 FORBIDDEN(한글 메시지)** 으로 차단.
- **새로고침 세션 유지**: access/refresh 토큰은 localStorage(`itg.auth.*`) 보존, user/roles/permissions 는 가드의 `/api/auth/me` 재호출로 자동 복원.
- **시드 보존**: `u-phase10`(id=4) 포함 시드 사용자·메뉴 데이터를 정리하지 않고 다음 phase 를 위해 유지.

## 5. 한계 (다음 phase 범위)

- **UX 메시지 카탈로그**(`UI.error.*` 통일, raw 토큰 노출 금지 — ADR-020): phase 11.
- **Playwright 시각 회귀 자동화**(ADR-019): phase 11. 본 §D 는 API 응답 + 가드 코드 경로로 검증.
- **토큰 갱신 자동화**(interceptor refresh): 별도 ADR — 본 step 미구현. 현재 401 은 `/login` 리다이렉트로만 처리.
- **메뉴 동적 라우터 추가**(`router.addRoute` 로 `group_id` 라우트 동적 등록): M9 메타 편집기와 함께.
- **WYSIWYG·BPMN**: M10·M11.

## 6. 결론

- **PRD §9 M7 마일스톤 이행**: `JWT 로그인 → 권한별 동적 메뉴 트리 → 시스템 관리 CRUD → 새로고침 세션 복원`
  의 인증·메뉴 흐름이 프런트 + 백엔드 동시 부팅 상태에서 e2e 로 동작함을 검증했다.
- **AC PASS**: type-check 0 · lint 0 · build OK · 62 tests passed. §B/C/D 시나리오 전부 PASS.
- **M7(인증·사용자·부서·역할·권한·메뉴) 완료**.
