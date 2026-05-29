# QA·UX 베이스라인 보고서 (v2.1)

> Phase 11 (ux-baseline-and-playwright) Step 2 — `ci-integration-and-report`
> 작성일: 2026-05-29 · 대상: v2.1 UX 베이스라인 + Playwright 시각 회귀

본 보고서는 PRD §6(UX 베이스라인)·§7(비기능)·ARCHITECTURE §17 의 약속을 실제 산출물로
정착시킨 결과를 정리한다. 핵심 약속 두 가지 — **(1) raw 백엔드 토큰 노출 금지(ADR-020),
(2) 실 브라우저 시각 회귀 자동 검증(ADR-019)** — 의 적용 범위·결함 처리 결과·운영 규칙·한계를
기록한다.

---

## 1. 적용 범위

| 항목 | 산출물 | 근거 |
|------|--------|------|
| UX 메시지 카탈로그 | `frontend/src/lib/ui-messages.ts` (UI.empty/error/loading/success + ERROR_CODE_MAP 13개 + mapErrorCode fallback) | ADR-020 (phase 11 step 0) |
| 카탈로그 적용 지점 | `usePageMeta` · `usePageData` · `useDataMutation` · `lib/api.ts` · `DynamicPage` · `LoginPage` · `MetaPage` | ADR-020 |
| Playwright 시각 회귀 | `frontend/playwright.config.ts` · `frontend/e2e/*.spec.ts` (5 spec / 8 test) · `e2e/fixtures/auth.ts` · baseline 스크린샷 7개 | ADR-019 (phase 11 step 1) |
| CI 게이트 | `.github/workflows/playwright.yml` — backend bootRun + frontend preview + Playwright 시각 diff + 실패 시 artifact 업로드 | 본 step |

> CI 구성은 phase 8 의 `sonarqube.yml`(backend build+test+SonarQube)과 **별도 파일·별도 job** 으로 분리한다.
> `sonarqube.yml` = 정적분석·커버리지 게이트, `playwright.yml` = 시각 회귀 게이트.

---

## 2. 1차 빌드 결함 카탈로그 처리 결과 (PRD 부록 A 갱신)

| ID | 결함 | 처리 |
|---|---|---|
| F-001 | Tailwind v4 `@source` 누락 → utility 무효 | ✅ hotfix (tokens.css + shadcn-mapping.css) |
| F-002 | DialogOverlay `bg-black/10` 너무 투명 | ✅ hotfix (`bg-black/50`) |
| F-003 | shadcn 시멘틱 변수 `@theme` 외부 → `bg-popover` 무효 | ✅ hotfix (shadcn-mapping.css `@import tailwindcss`) |
| F-004 | `usePageMeta` notPublished 매칭 좁음 → raw 백엔드 메시지 노출 | ✅ hotfix + 카탈로그 매핑 (phase 11 step 0) |
| F-005 | HomePage "다음 phase 에서…" placeholder 잔존 | ✅ hotfix (메타 ready 상태 표시) |
| F-006 | DynamicPage notPublished/error 카드 raw 텍스트 | ✅ hotfix + 친화 카탈로그 (phase 11 step 0) |
| F-007 | system/MetaPage 의도 불분명 | ✅ hotfix (안내 보강) |
| F-008 | `@PreAuthorize` 등 권한 가드 부재 | ✅ M7 (phase 9·10) |
| F-009 | 자동 e2e 가 실 브라우저 시각 결함 못 잡음 | ✅ Playwright 도입 (phase 11 step 1·2) |

> F-009 의 실효성은 step 1 에서 입증됐다 — 실 브라우저 e2e 가 `LoginPage` 의 잠복 버그(`immediate:false`
> `useFetch` 를 `execute()` 없이 `await` → hang)를 적발했고 `.execute()` 추가로 수정됐다. 기존 cURL·단위
> 테스트(Mockito·Vitest)로는 재현되지 않던 결함이다.

---

## 3. Playwright spec 일람

| spec 파일 | 검증 내용 |
|-----------|-----------|
| `home.spec` | 비인증 시 `/login` 리다이렉트 · admin 홈 화면 |
| `itsm.spec` | admin 티켓 그리드 · user 권한 메뉴 차이 |
| `itam.spec` | admin 자산 그리드 |
| `system.spec` | 사용자 목록 · 메뉴 트리 |
| `dialog.spec` | 등록 다이얼로그 (F-002 투명 박살 회귀 차단) |

baseline 스크린샷 7개를 `e2e/*-snapshots/` 에 commit 했으며, 재실행 안정성(연속 PASS)을 step 1 에서 확인했다.

---

## 4. baseline 운영 규칙

- baseline PNG 는 `main` 브랜치에 commit. PR 마다 diff 검사.
- 의도된 UI 변경 시에만 `pnpm e2e:update` 로 갱신하고 **PR 본문에 사유 명시** (운영 절차는 `docs/QA_GUIDE.md`).
- `maxDiffPixelRatio: 0.02` (2%) — 폰트 렌더링·OS 환경 차이를 흡수하는 임계치. 초과 diff 는 PR 자동 차단.
- CI 에서 `--update-snapshots` 자동 실행 금지 — baseline 갱신은 사람이 의도적으로만 수행한다.

---

## 5. 한계

- Playwright 시각 회귀는 **도메인 결함(잘못된 endpoint 호출·계산 오류 등)을 시각만으로는 못 잡는다.**
  액션 체인을 검증하는 시나리오 e2e 는 향후 별도 ADR 후보.
- CI 환경 폰트·렌더링 차이로 인한 false positive 가능 — `maxDiffPixelRatio` 임계치로 1차 흡수하되,
  반복 발생 시 컨테이너 고정·임계치 조정으로 보강한다.
- 현재 viewport 는 데스크탑(1440×900) 단일. 모바일 시각 회귀는 별도 viewport project 추가 시 가능.
- `playwright.yml` 은 backend bootRun + frontend preview 의 실 기동에 의존하므로, 백엔드 부팅 실패 시
  e2e job 전체가 실패한다(설계상 의도 — 통합 상태 검증).
