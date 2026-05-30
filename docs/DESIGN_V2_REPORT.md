# 디자인 시스템 v2 종합 보고서 — before/after

> Phase 16 (design-system-v2) Step 10 — `playwright-baseline-and-final-report`
> 작성일: 2026-05-30 · 대상: phase 16 step 0~9 의 디자인 v2 전면 개편 결과
>
> 본 보고서는 UI_GUIDE v2(2026-05-30 개정)의 노선 전환 — *"정보 밀도 + 도메인 시각 변별성 +
> 마이크로 인터랙션 + 다크 모드 우선"* — 이 phase 16 의 10 step 으로 실제 화면에 정착한 결과를
> **v1(이전) → v2(현재)** 비교로 정리하고, 갱신된 Playwright 시각 baseline 인덱스와 잔존 한계를
> 기록한다. 본 step 은 운영 코드를 수정하지 않고 **시각 baseline 전면 갱신 + 보고서**만 다룬다.

---

## 0. 환경

| 항목 | 값 |
|------|-----|
| 작업 디렉토리 | `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM` |
| 브랜치 | `feat-16-design-system-v2` |
| Backend | Spring Boot 3 · Java 21 · `local` 프로파일 (`http://localhost:8080`, `/actuator/health` → UP) |
| Frontend | Vite 6 production build → `pnpm preview` (`http://localhost:5173`) · Node.js v24 |
| DB | PostgreSQL 16 · Docker 컨테이너 `itg-postgres` (`localhost:5432`, DB `itgdb`) |
| e2e | Playwright (chromium, viewport 1440×900, locale ko-KR, `maxDiffPixelRatio: 0.02`) |
| 인증 | admin / admin-sample-1234 (ROLE_ADMIN) |

> 본 baseline 갱신 전 `20_notification.sql`(step 7 산출물)이 기존 DB 컨테이너에 미적용 상태였어
> 백엔드 스키마 검증이 `missing table [notification]` 으로 실패했다. 해당 SQL 을 수동 적용한 뒤
> bootRun 이 정상 기동했다(컨테이너 최초 부팅 이후 추가된 init SQL 은 자동 적용되지 않음 — 운영 메모).

---

## 1. v1 (이전) → v2 (현재) 비교

| 영역 | v1 (before) | v2 (after) | 정착 step |
|------|-------------|------------|-----------|
| **HomePage** | 5 모듈 진입 카드 그리드(평면·정보 밀도 낮음) | 운영 대시보드 위젯 그리드 — KPI 4종 + 차트(우선순위 도넛·상태 분포·자산 분류) + 최근 활동 피드 + 내 워크플로우 큐(30초 폴링) | step 3 |
| **ITSM** | 티켓 그리드 only | 그리드 + **칸반 보드**(/itsm/board, 4 컬럼 드래그 전이) + 워크플로우 패널 + TopBar 목록↔보드 토글 | step 4 |
| **ITAM** | 자산 그리드 only | 분류 트리 + 분류별 동적 폼 + 자산 **라이프사이클 타임라인**(EventTimeline) | step 5 |
| **PMS** | 빈/미구현 페이지 | 프로젝트 그리드 + **Gantt 차트**(ECharts custom series·진행률 채움) | step 5 |
| **색 시스템** | 단일 액센트(회색·흰색 미니멀) | **모듈 컬러 5종**(ITSM indigo·ITAM teal·PMS violet·COMMON amber·SYSTEM slate) + soft 토큰, 헤더 띠·아이콘·KPI 액센트 | step 0 |
| **테마 모드** | light only | **light/dark/system 토글**(localStorage 영구화·prefers-color-scheme 감지·FOUC 회피) | step 1 |
| **데이터 시각화** | 없음 | ECharts 6 + dataviz 7종(KpiCard·Sparkline·Trend·Donut/Bar/Line·MetricRow), 토큰·다크 자동 전환 | step 2 |
| **마이크로 인터랙션** | 거의 없음 | **Skeleton**(shimmer)·**Optimistic UI**(즉시 반영 rollback)·**Page transition**(fade+slide-up)·**Hover detail**(그리드 quick-action)·toast stagger | step 6 |
| **알림** | 없음 | **알림 센터**(TopBar 종+미읽음 뱃지·드롭다운 패널·전체 목록·30초 폴링, 워크플로우/상태변경 자동 생성) | step 7 |
| **빈 상태·아이덴티티** | raw 텍스트·무미건조 | **EmptyState**(lucide 아이콘·모듈 soft 색·행동 유도) + Avatar(이니셜·결정적 색) + PageHeader 모듈 컬러 띠 | step 8 |
| **폼 위젯** | placeholder 입력 다수 | DatePicker·DateRangePicker·UserPicker(자동완성)·FileUpload(드래그앤드롭)·MarkdownEditor·Slider·ColorPicker 풀 위젯 | step 9 |
| **타이포그래피** | 일반 숫자 | KPI·그리드 숫자 **tabular-nums** 자릿수 정렬 | step 0 |
| **Elevation/Motion** | 그림자 오버레이 한정·자의적 트랜지션 | `--shadow-card/hover/overlay` + `--motion-fast/base/slow` 토큰 시스템(prefers-reduced-motion 흡수) | step 0 |
| **시각 회귀** | 5 spec(home·itsm·itam·system·dialog) | + **6 신규 spec**(dashboard·kanban·gantt·dark-mode·notification·chart-gallery), baseline 전면 갱신 | step 10(본 step) |

---

## 2. 시각 baseline 스크린샷 인덱스

Playwright HTML 리포트: `frontend/playwright-report/index.html` (`pnpm e2e:report` 로 열람).
v1 baseline 은 동일 경로의 `*.png.bak` 으로 보존(롤백 가능). chromium/darwin, viewport 1440×900.

### 2-1. 신규 baseline (v2 — step 10)

| spec | baseline PNG | 검증 화면 |
|------|--------------|-----------|
| `dashboard.spec.ts` | `dashboard-admin-chromium-darwin.png` | 대시보드 위젯 그리드(KPI·차트·활동 피드) |
| `kanban.spec.ts` | `kanban-board-chromium-darwin.png` | `/itsm/board` 보드 페이지(§4 F-PH16-BOARD 상태 포함) |
| `gantt.spec.ts` | `pms-gantt-chromium-darwin.png` | `/pms` 프로젝트 그리드 + 간트 |
| `dark-mode.spec.ts` | `dashboard-dark-…png` · `itsm-dark-…png` | 다크 모드 전환 후 대시보드·ITSM 그리드 |
| `notification.spec.ts` | `notification-panel-open-…png` | 알림 패널 open 상태 |
| `chart-gallery.spec.ts` | `chart-gallery-chromium-darwin.png` | `/_dev/charts` 데이터 시각화 갤러리 |

### 2-2. 갱신된 기존 baseline (v1 → v2)

| spec | baseline PNG | 비고 |
|------|--------------|------|
| `home.spec.ts` | `home-admin-…png` · `login-…png` | 대시보드 재설계 반영(헤더 어서션 `/대시보드\|Polestar/` → `/안녕하세요/` 로 갱신) |
| `itsm.spec.ts` | `itsm-admin-…png` | 모듈 컬러·v2 토큰 반영 |
| `itam.spec.ts` | `itam-admin-…png` | 〃 |
| `system.spec.ts` | `system-users-…png` · `system-menus-…png` | 〃 |
| `dialog.spec.ts` | `itsm-create-dialog-…png` | 다이얼로그 v2 오버레이·elevation |

### 2-3. 최종 e2e 결과

본 step 범위(기존 home·itsm·itam·system·dialog + 신규 6 spec) **15 test 전부 PASS** — 재실행 시에도
결정적으로 통과(연속 확인). 갱신 baseline 의 무회귀·안정성을 확인했다.

```
15 passed (chromium)  ─ home·itsm·itam·system·dialog · dashboard·kanban·gantt·dark-mode·notification·chart-gallery
```

> `meta-editor.spec.ts`·`meta-editor-drag.spec.ts` 2 spec 은 본 step 범위 밖이며, **phase 14 step 3 에서
> 이미 기록된 기존 결함(F-PH13-E2E — phase 14 가 span 컨트롤을 라디오→버튼으로 바꾸면서 phase 13
> spec 의 옛 셀렉터 `input[name="f-span-2"]` 가 깨짐)** 으로 실패한다. 본 step 에서 타 phase spec 은
> 수정하지 않았다(`META_EDITOR_DRAG_REPORT.md` §4-1 의 권고 그대로 — 별도 phase 책임). 두 spec 의
> v1 baseline 은 갱신되지 않았고 `.bak` 과 동일하게 보존된다.

---

## 3. UI_GUIDE v2 적용 결과 정착 (§4)

`docs/UI_GUIDE.md` 는 step 0 에서 v2 로 전면 개정됐고(정보 밀도·모듈별 컬러·마이크로 인터랙션·다크
우선), 본 phase 의 step 1~9 가 그 규칙을 실제 컴포넌트·화면에 구현했다. UI_GUIDE 의 핵심 v2 항목이
화면으로 정착된 매핑:

| UI_GUIDE v2 항목 | 정착 산출물 |
|------------------|-------------|
| §3-2 모듈 컬러 시스템(5종 + soft) | `lib/module-color.ts` `moduleVisual()` → PageHeader 띠·아이콘·KPI 액센트(step 0·3·8) |
| §4-2 숫자 tabular-nums | `base.css` body `font-feature-settings` → KPI·그리드 자릿수 정렬(step 0) |
| §5-2 데이터 시각화 컴포넌트 | dataviz 7종 + ECharts 어댑터(`lib/echarts.ts`·`chart-theme.ts`)(step 2) |
| §5-3 보드·타임라인 | DynamicKanban(step 4)·GanttChart·EventTimeline(step 5) |
| §5-4 알림·피드·empty | NotificationItem·ActivityFeedItem·EmptyState(step 7·3·8) |
| §6 Elevation & Shadow | `--shadow-card/hover/overlay` 토큰 적용(step 0) |
| §7 Motion 정식 시스템 | `--motion-fast/base/slow` + prefers-reduced-motion(step 0·6) |
| §8 다크 모드 1급 시민 | useThemeStore·ThemeToggle, 토큰 light/dark 전체 매핑(step 0·1) |
| §9 마이크로 인터랙션 카탈로그 | Skeleton·Optimistic UI·Page transition·Hover detail(step 6) |

> v1 의 폐기·완화 규칙(§10) — "단일 액센트만/그림자 오버레이 한정/그라데이션 전면 금지" — 도 v2
> 토큰 범위 안에서 완화되어 적용됐다(모듈 컬러는 식별 표식 전용, 글로벌 CTA·focus 는 여전히
> `--color-primary`).

---

## 4. 잔존 한계 (사용자 시각 검토에서 드러날 수 있는 영역)

본 step 의 e2e 작업 중 적발한 결함·한계를 ADR-019 의 정직 보고 기조로 기록한다.

### 4-1. F-PH16-BOARD — 칸반 보드 mount 시 이중 fetch 로 dataLoad 에러 (★ 본 step 적발)

- **현상**: `/itsm/board`(BoardPage.vue)는 mount 시 `usePageData` 의 reactive URL 이 먼저 `size=20` 으로
  fetch 를 시작하고, 직후 `onMounted` 의 `setQuery({ size: 500 })` 가 URL 을 바꿔 첫 요청을 **abort** 한다.
  프로덕션 빌드(preview)에서는 abort 된 첫 요청의 에러가 `error.value` 에 남아 "데이터를 불러올 수
  없습니다." 에러 카드가 노출된다(컬럼이 렌더되지 않음). 동일 `usePageData` 를 쓰는 일반 `/itsm`
  그리드는 단일 fetch(200, 3행)로 정상 동작하므로, 원인은 **BoardPage 의 이중 fetch** 로 특정된다.
  (cURL 로 `/api/tickets?page=0&size=500` 은 200·CORS preflight 200 확인 — 백엔드/네트워크 문제 아님.)
- **영향**: dev 모드(step 4 검증 시점)에서는 타이밍 차로 드러나지 않았으나 프로덕션 빌드에서 재현된다.
  실 브라우저 e2e 가 단위 테스트로 드러나지 않던 런타임 결함을 적발한 사례(ADR-019 도입 취지 적중).
- **본 step 처리**: 운영 코드 수정 금지(본 step 금지사항)이므로 **수정하지 않고** 현 상태를 baseline 으로
  고정하고 본 결함을 기록한다(phase 14 step 3 의 F-PH13-E2E 처리 방식과 동일 — 적발·기록, 교정은 별도
  phase). **권고 수정(별도 phase)**: BoardPage 의 초기 query 를 `{ page: 0, size: 500 }` 로 두어 이중
  fetch 를 제거(또는 `usePageData` 에서 abort 에러를 `error` 로 노출하지 않도록 가드). 수정 후 kanban
  baseline 갱신 필요.

### 4-2. 설계상 알려진 한계 (다음 단계 후보)

- **실시간 알림(websocket)** — 현재 30초 폴링 stub(step 7). push/websocket 미도입.
- **모바일 viewport baseline** — 현재 데스크탑(1440×900) 단일. 모바일 시각 회귀는 별도 viewport
  project 추가 시 가능(ADR-019 한계와 동일).
- **일러스트 자산(벡터 그림)** — EmptyState 등은 lucide 아이콘 + 모듈 색 강조만. 전용 일러스트 없음.
- **차트 캔버스 시각 회귀** — ECharts canvas 렌더는 `maxDiffPixelRatio: 0.02` 로 폰트·안티앨리어싱
  차이를 흡수하나, 환경 차에 따른 false positive 가능(컨테이너 고정으로 보강 권고).
- **meta-editor 2 spec 기존 깨짐(F-PH13-E2E)** — §2-3 참조. 별도 phase 에서 셀렉터 갱신 필요.

---

## 5. 다음 단계 후보

1. **F-PH16-BOARD 교정** — BoardPage 이중 fetch 제거 + kanban baseline 갱신(우선).
2. **모바일 viewport** — Playwright project 추가(`devices['iPhone 13']` 등) + 반응형 baseline.
3. **일러스트 자산** — EmptyState·온보딩 화면 전용 벡터 일러스트.
4. **Command Palette (cmd+k)** — 빠른 화면·액션 탐색.
5. **키보드 단축키 풀세트** — 칸반·그리드·다이얼로그 전역 단축키.
6. **meta-editor spec 셀렉터 갱신**(F-PH13-E2E) + 공유 시드 e2e 직렬화(`workers:1`).

---

## 6. 산출물

| 산출물 | 비고 |
|--------|------|
| `frontend/e2e/dashboard.spec.ts` · `kanban.spec.ts` · `gantt.spec.ts` · `dark-mode.spec.ts` · `notification.spec.ts` · `chart-gallery.spec.ts` | **본 step — 신규 6 spec** |
| `frontend/e2e/*-snapshots/*.png` | **본 step — v2 baseline 전면 갱신**(신규 6 + 기존 갱신) |
| `frontend/e2e/*-snapshots/*.png.bak` | **본 step — v1 baseline 백업**(롤백 보존, 삭제 금지) |
| `frontend/e2e/home.spec.ts` | 대시보드 재설계 반영 어서션 갱신(test code) |
| `docs/DESIGN_V2_REPORT.md` | **본 보고서** |

> 검증: `pnpm type-check`·`pnpm lint`(max-warnings 0)·`pnpm build`·`pnpm test`(267) PASS,
> Playwright 본 step 범위 15 test PASS(재실행 안정).
