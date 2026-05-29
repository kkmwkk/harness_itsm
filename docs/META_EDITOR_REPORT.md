# No-code 메타 편집기 e2e 보고서 — 폼 UI(M9 / ADR-016 1단계)

> Phase 13 (meta-editor-form-ui) Step 5 — `e2e-no-code-form-editor`
> 작성일: 2026-05-30 · 대상: `itg-test-meta-editor-v1-1`

본 보고서는 **비개발 사용자가 JSON 을 한 줄도 보지 않고 GUI(폼 UI)만으로 새 메타 그룹을 만들고,
폼·그리드·액션을 편집해 저장·발행한 뒤 화면(`/system/meta` viewer)에 노출되는 것까지** e2e 로
통과시킨 결과를 기록한다. 이로써 PRD §8 성공 지표("비개발자가 메타 1건 발행 평균 10분 이내")와
ADR-016(폼 UI → 드래그 → WYSIWYG 의 1단계) 의 핵심 약속을 검증한다.

---

## 1. 환경

| 항목 | 값 |
|------|-----|
| 작업 디렉토리 | `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM` |
| 브랜치 | `feat-13-meta-editor-form-ui` |
| Backend | Spring Boot 3 · Java 21 · `local` 프로파일 (`http://localhost:8080`, `/actuator/health` → UP) |
| Frontend | Vite 6 dev 서버 (`http://localhost:5173`) · Node.js v24 |
| DB | PostgreSQL 16 · Docker 컨테이너 `itg-postgres` (`localhost:5432`, DB `itgdb`) |
| e2e | Playwright (chromium, viewport 1440×900, locale ko-KR) |
| 인증 | admin / admin-sample-1234 (ROLE_ADMIN — META_EDIT 보유) |
| spec | `frontend/e2e/meta-editor.spec.ts` |
| baseline | `frontend/e2e/meta-editor.spec.ts-snapshots/meta-editor-flow-chromium-darwin.png` |

---

## 2. 시나리오 11 단계 — 전부 PASS ✅

`pnpm exec playwright test e2e/meta-editor.spec.ts` → **1 passed** (재실행 시에도 안정적으로 통과).

| # | 단계 | 검증 |
|---|------|------|
| 1 | admin 로그인 → `/system/meta-editor` 진입 | "신규 그룹 만들기" 버튼 노출 (RequirePermission META_EDIT 통과) |
| 2 | "신규 그룹 만들기" 클릭 → 다이얼로그 | 신규 메타 그룹 다이얼로그 표시 |
| 3 | 입력 — groupId=`itg-test-meta-editor`, title=`테스트 메타`, systemType=`COMMON`, packageType=`PACKAGE`, major=1, minor=1 | id 는 `{groupId}-v{major}-{minor}` 패턴으로 자동 합성 |
| 4 | 생성 → 편집 페이지 이동 | `POST /api/meta` 201 → 성공 토스트 → 버전 이력의 DRAFT "편집" → `/system/meta-editor/itg-test-meta-editor-v1-1` |
| 5 | FormFieldEditor — 필드 3개 | `title`(text·필수) · `priority`(radio·옵션 4종 LOW/MEDIUM/HIGH/CRITICAL) · `content`(textarea·전체 폭 span 2) |
| 6 | GridColumnEditor — 컬럼 2개 | `title`(text·flex 1) · `priority`(priority·width 110) |
| 7 | ActionEditor — 액션 1개 | `create`(dialog-form, 라벨 "등록") |
| 8 | 저장 클릭 | `POST /api/meta/dry-run` 200(valid) → `PUT /api/meta/{id}/body` 200 → "저장되었습니다." 토스트 |
| 9 | 발행 클릭 → 확인 다이얼로그 → 발행 | `PATCH /api/meta/{id}/publish` 200 → "배포되었습니다." 토스트 |
| 10 | `/system/meta?groupId=itg-test-meta-editor` 진입 | `GET /api/meta/active/itg-test-meta-editor` 200 → PUBLISHED 메타 카드(`테스트 메타 (itg-test-meta-editor-v1-1)`) 노출 |
| 11 | 시각 스크린샷 baseline | `meta-editor-flow.png` 생성·diff PASS |

### cURL 교차 검증

```bash
# 생성된 메타가 DB 에 PUBLISHED
docker exec -i itg-postgres psql -U itg -d itgdb -c \
  "SELECT meta_status FROM page_meta WHERE id='itg-test-meta-editor-v1-1';"
#  itg-test-meta-editor-v1-1 | PUBLISHED

# active 응답 (버전 라우팅 — PUBLISHED 최신 1건)
curl -fsS -H "Authorization: Bearer $ADMIN" \
  http://localhost:8080/api/meta/active/itg-test-meta-editor
#  → "id":"itg-test-meta-editor-v1-1"  (ACTIVE OK)
```

---

## 3. 핵심 검증 사실

- **비개발 사용자가 JSON 한 줄 보지 않고 메타 생성·발행** (ADR-016 1단계, PRD §8). 그룹 생성 다이얼로그 →
  폼/그리드/액션 GUI 편집기 → 저장 → 발행까지 전 과정이 폼 컨트롤만으로 완결된다. JSON 직접 편집·노출 없음.
- **DRAFT → dry-run → PUT body → publish 흐름 자동** (ADR-006). 저장 시 `dry-run` 형식 검증을 먼저 통과해야
  본문이 교체되고, 발행 게이트(`canPublish`)는 dry-run 검증된 스냅샷에서만 활성화된다.
- **발행 즉시 화면 노출**. publish 직후 `/system/meta?groupId=itg-test-meta-editor` 의 메타 viewer 가
  `GET /api/meta/active/{groupId}` 버전 라우팅으로 PUBLISHED 최신본을 즉시 렌더링한다.

---

## 4. 본 step 에서 적발·교정한 결함 (steps 0~4 회귀)

실 브라우저 e2e 가 **단위 테스트(Vitest)·cURL 로는 드러나지 않던 결함**을 적발했다 (ADR-019 의 도입 취지 — F-009).

- **결함**: 편집기의 변경 요청(생성·dry-run·저장(PUT)·발행·복사·보관)이 VueUse `useFetch` 를
  `immediate: false` 로 두고 **`execute()` 호출 없이 `await` 만** 해 요청이 영원히 hang 했다
  (VueUse 14.3.0 의 `await ...post().json()` 은 `until(isFinished).toBe(true)` 를 기다리는데,
  `immediate:false` 에서는 `execute()` 가 호출되지 않아 `isFinished` 가 영원히 `false`).
  네트워크 요청 자체가 나가지 않아 "생성" 클릭 후 다이얼로그가 닫히지 않고 토스트도 뜨지 않았다.
- **교정 위치(steps 0~4 코드)**: `MetaEditorPage.vue`(submitCreate·copyVersion·publishVersion·archiveVersion)
  · `MetaEditorDetailPage.vue`(runDryRun·save·doPublish·copyNewVersion·archive) — 각 변경 요청을
  `LoginPage.vue` 의 검증된 정석 패턴(`const { execute, ... } = useApiFetch(...).post(...).json(); await execute();`)
  으로 통일했다. (운영 코드 로직·계약 변경 없음 — 누락된 `execute()` 보강만.)
- **회귀 안전망**: vue-tsc 0건, Vitest 140 tests PASS, Playwright 5+1 spec PASS 로 무회귀 확인.

> 이 결함은 폼 GUI 편집기의 **모든 mutation** 을 막던 blocker 였으며, 편집기 e2e(본 step) 이전에는
> 어떤 자동 검증도 통과시킨 적이 없는 경로였다. 시각·시나리오 e2e 가 도메인 흐름 결함을 잡은 사례다.

---

## 5. 한계 (다음 phase)

- **드래그앤드롭 순서 변경 — phase 14(M10)**: 폼 필드·그리드 컬럼의 순서/레이아웃(span·width) 을
  드래그로 바꾸는 기능은 본 phase 범위 밖이다. 현재는 추가 순서대로 배치된다.
- **실 화면 WYSIWYG — phase 15(M11, Stretch)**: 실 미리보기에서 직접 클릭·편집하는 단계는 별도 PoC 후 결정.
- **mutation `execute()` 누락 — 전역 점검 권고**: 동일 패턴(`useDataMutation` 등 phase 13 외 코드)에도
  잠복 가능성이 있다. 시나리오 e2e 확대 시 함께 점검하는 것이 바람직하다(본 step 범위 밖).

---

## 6. 산출물

| 산출물 | 비고 |
|--------|------|
| `frontend/e2e/meta-editor.spec.ts` | **본 step — 11단계 e2e spec** |
| `frontend/e2e/meta-editor.spec.ts-snapshots/meta-editor-flow-chromium-darwin.png` | **본 step — 시각 baseline** |
| `frontend/src/pages/system/MetaEditorPage.vue` · `MetaEditorDetailPage.vue` | steps 0~4 결함 교정(`execute()` 보강) |
| `docs/META_EDITOR_REPORT.md` | **본 보고서** |

> 시드 메타(`itg-test-meta-editor-v1-1`, PUBLISHED)는 DB 에 **보존**한다 (재실행 안정성을 위해 spec 의
> `beforeAll` 이 시작 전 동일 그룹을 정리하고, 테스트가 다시 생성·발행해 항상 PUBLISHED 로 끝낸다).
