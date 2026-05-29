# No-code 메타 편집기 e2e 보고서 — 드래그앤드롭(M10 / ADR-016 2단계)

> Phase 14 (meta-editor-drag-and-drop) Step 3 — `e2e-drag-meta-edit`
> 작성일: 2026-05-30 · 대상: `itg-test-meta-editor` 그룹(phase 13 step 5 시드)

본 보고서는 **비개발 사용자가 기존(PUBLISHED) 메타의 DRAFT 사본을 만들어, 폼 필드 순서를 grip
핸들로 바꾸고 그리드 컬럼 width 를 인라인 편집한 뒤 저장·발행하고, 발행된 메타 본문(viewer)에 새
순서·width 가 반영되는 것까지** e2e 로 통과시킨 결과를 기록한다. 이로써 ADR-016 2단계(드래그앤드롭)·
PRD §5-4 의 "필드 순서·레이아웃·컬럼 width 를 GUI 로 변경" 약속을 검증한다.

---

## 1. 환경

| 항목 | 값 |
|------|-----|
| 작업 디렉토리 | `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM` |
| 브랜치 | `feat-14-meta-editor-drag-and-drop` |
| Backend | Spring Boot 3 · Java 21 · `local` 프로파일 (`http://localhost:8080`, `/actuator/health` → UP) |
| Frontend | Vite 6 dev 서버 (`http://localhost:5173`) · Node.js v24 |
| DB | PostgreSQL 16 · Docker 컨테이너 `itg-postgres` (`localhost:5432`, DB `itgdb`) |
| e2e | Playwright (chromium, viewport 1440×900, locale ko-KR, `fullyParallel:false` 직렬) |
| 인증 | admin / admin-sample-1234 (ROLE_ADMIN — META_EDIT 보유) |
| spec | `frontend/e2e/meta-editor-drag.spec.ts` |
| baseline | `frontend/e2e/meta-editor-drag.spec.ts-snapshots/meta-editor-drag-chromium-darwin.png` |
| 편집 컴포넌트 | `FormFieldEditor.vue`(step 1) · `GridColumnEditor.vue`(step 2) · `drag.ts`(step 0) |

---

## 2. 시나리오 — 전부 PASS ✅

`pnpm exec playwright test e2e/meta-editor-drag.spec.ts` → **1 passed** (재실행 시에도 안정적으로 통과).

| # | 단계 | 검증 |
|---|------|------|
| 1 | admin 로그인 → `/system/meta-editor` → `itg-test-meta-editor` 그룹 선택 → v1-1(PUBLISHED) **복사** | `POST /api/meta/{id}/copy` → "새 DRAFT 버전이 생성되었습니다." → v1-2(DRAFT) 생성 |
| 2 | DRAFT(v1-2) **편집** 진입 | `/system/meta-editor/itg-test-meta-editor-v1-2` — 복사된 본문(필드 title·priority·content) 로드 |
| 3 | FormFieldEditor — 1번째 필드(`title`)를 grip 핸들로 끝까지 내림 | `[title, priority, content]` → `[priority, content, title]` (각 단계 input value 로 확인) |
| 4 | GridColumnEditor — 1번째 컬럼(`title`)을 **px 너비 모드로 전환** 후 width **200 → 150** 인라인 편집 | `#c-width-0` 값 200 → 150, flex 제거(px 모드) |
| 5 | **저장** | `POST /api/meta/dry-run` 200(valid) → `PUT /api/meta/{id}/body` 200 → "저장되었습니다." |
| 6 | **발행** → 확인 다이얼로그 → 발행 | `PATCH /api/meta/{id}/publish` 200 → "배포되었습니다." → v1-1 자동 DEPRECATED |
| 7 | `/system/meta?groupId=itg-test-meta-editor` viewer 에서 본문 확인 | `form.fields` 순서 `["priority","content","title"]`, `grid.columns[0].width === 150`, `flex === undefined` |
| 8 | 시각 스크린샷 baseline | `meta-editor-drag.png` 생성·diff PASS |

> viewer 검증(7)은 `MetaPage.vue` 가 `GET /api/meta/active/{groupId}` 로 받은 PUBLISHED 최신본의
> `metaJson` 을 그대로 렌더(`<pre>`)하므로, 그 JSON 을 파싱해 **필드 순서·컬럼 width 가 실제 발행
> 본문에 반영**됐는지 직접 단언한다.

---

## 3. 핵심 검증 사실

- **드래그(순서 변경) + width 인라인 편집의 결과가 발행 본문에 반영**. grip 핸들로 바꾼 필드 순서와
  px 모드로 바꾼 컬럼 width(150) 가 `dry-run → PUT body → publish` 를 거쳐 `page_meta_active` 의
  PUBLISHED 본문으로 노출된다(ADR-006 버전 라우팅 위에서 동작).
- **순서 변경은 드래그와 동일한 `reorder()` 경로**. grip 핸들의 내장 키보드 affordance(Alt+↓)는
  `FormFieldEditor.moveField → drag.ts.reorder` 를 호출한다 — VueDraggable 의 드래그 종료가 호출하는
  것과 같은 helper다(step 0·1). 즉 순서 변경 로직 자체를 e2e 가 그대로 검증한다.
- **px↔flex 폭 모드 전환**. 시드 컬럼 `title` 은 flex 모드였고, "px 너비" 전환(`setWidthMode`) 시
  flex 가 제거되고 width 인풋이 활성화되어 인라인 편집이 가능함을 확인(step 2 동작).

---

## 4. 한계 — SortableJS 네이티브 DnD 와 Playwright 합성 입력

본 step 에서 적발한 **e2e 자동화 한계**를 정직하게 기록한다(ADR-019 의 정직 보고 기조).

- **현상**: 컴포넌트의 순서 변경은 SortableJS(vue-draggable-plus 1.15.2 번들)의 **네이티브 HTML5
  DnD 모드**로 동작한다. 진단 결과 드래그 시작 시 dragEl 에 `draggable="true"` 가 부여되고 chosen
  클래스도 적용되지만(`tap` 인식됨), **Playwright 의 합성 마우스/포인터 입력은 브라우저의 네이티브
  `dragstart/dragover/drop` 시퀀스를 발생시키지 못해**(공식 문서화된 한계) 정렬이 커밋되지 않았다.
  네이티브 drag 이벤트를 직접 `dispatchEvent` 로 합성하는 방법도 SortableJS 내부 상태 가드 때문에
  안정적으로 재현되지 않았다.
- **대응**: e2e 는 **같은 grip 핸들에 내장된 키보드 affordance(`Alt+↓`)**로 순서를 변경한다. 이는
  운영 코드의 실제 접근성 기능이며, 위에서 적은 대로 드래그와 **동일한 reorder() 경로**를 탄다.
  운영 코드는 일절 수정하지 않았다(forceFallback 강제 등 변경 없음).
- **시각 회귀의 보완**: 드래그 자체의 **시각 표현**(grip 커서·chosen 반투명·ghost 파선)은 단위
  테스트(step 1·2) + 본 baseline 스크린샷으로 보강한다. 포인터 드래그의 **실 브라우저 정렬 커밋**까지
  e2e 로 자동 구동하려면 forceFallback 도입(운영 변경, 별도 ADR 후보) 또는 시나리오 e2e 전용 드라이버가
  필요하다 — 본 step 범위 밖.

### 그 외 한계 (다음 phase)

- **그리드 컬럼 순서 드래그**: 본 시나리오는 컬럼 **width** 인라인 편집만 다룬다. 컬럼 순서 드래그도
  필드와 동일한 reorder 경로이므로 동일한 한계가 적용된다.
- **실 화면 WYSIWYG — phase 15(M11, Stretch)**: 실 미리보기에서 직접 클릭·편집은 별도 PoC 후 결정.

---

## 4-1. 본 step 에서 적발한 기존 결함·관찰 (별도 처리 권고)

본 step 의 e2e 작업 중 **본 step 범위 밖이지만 별도 조치가 필요한 사항**을 정직하게 기록한다
(운영 코드·타 phase spec 은 본 step 에서 수정하지 않았다 — 금지사항 준수).

- **F-PH13-E2E — phase 13 `meta-editor.spec.ts` 가 step 1 의 span UI 변경으로 깨짐**: phase 14 step 1
  이 FormFieldEditor 의 span 컨트롤을 라디오(`input[name="f-span-2"]`)에서 두 개의 버튼(`반 폭`/`전체 폭`)
  으로 바꿨는데, phase 13 e2e 가 옛 셀렉터를 그대로 써서 **단독 실행에서도 30s 타임아웃으로 실패**한다.
  step 1 의 AC 가 단위 테스트·build 만 돌리고 기존 e2e 스위트를 돌리지 않아 회귀가 새어 나갔다(ADR-019 의
  도입 취지가 적중한 사례). → **권고: phase 13 spec 의 span 조작을 `전체 폭` 버튼 클릭으로 갱신**
  (본 step 범위 밖 — 타 phase 산출물 수정 금지).
- **공유 시드 + 파일 병렬 실행 race**: `meta-editor-drag.spec`(본 step)과 `meta-editor.spec`(phase 13)이
  같은 시드 그룹(`itg-test-meta-editor`)을 쓴다. `playwright.config.ts` 는 `fullyParallel:false` 이지만
  **`workers` 미설정 → 파일 단위는 병렬 워커**로 돌아 두 spec 이 동시에 그룹을 조작하면 race 가 난다
  (full `pnpm e2e` 에서 관측). 본 step 의 AC 는 spec 단독 실행이라 **결정적으로 PASS**한다(연속 2회 확인).
  → **권고: 두 spec 을 직렬화(`workers:1`)하거나, 공유 시드를 쓰는 e2e 를 같은 파일로 묶기**(본 step 범위 밖).

---

## 5. 재실행 안정성 · 시드 보존

- spec 의 `beforeAll` 은 이전 실행이 만든 사본(v1-2+)만 제거하고 **v1-1 시드는 삭제하지 않으며**,
  알려진 PUBLISHED 본문(필드 3개·컬럼 2개)으로 normalize 해 매 실행 결정적인 시작 상태를 만든다.
- 테스트가 끝나면 그룹은 새 PUBLISHED 버전(v1-2)과 자동 DEPRECATED 된 v1-1 을 남긴다 — 그룹은 항상
  PUBLISHED 최신본을 가지므로 viewer 노출이 보존된다. 시드(`itg-test-meta-editor-*`)는 정리하지 않는다.

---

## 6. 산출물

| 산출물 | 비고 |
|--------|------|
| `frontend/e2e/meta-editor-drag.spec.ts` | **본 step — 드래그/순서·width 편집 e2e spec** |
| `frontend/e2e/meta-editor-drag.spec.ts-snapshots/meta-editor-drag-chromium-darwin.png` | **본 step — 시각 baseline** |
| `docs/META_EDITOR_DRAG_REPORT.md` | **본 보고서** |
| `frontend/src/components/editor/FormFieldEditor.vue` · `GridColumnEditor.vue` | steps 1·2 산출물(드래그·width — 본 step 미수정) |
