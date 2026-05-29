# Step 0: wysiwyg-poc-meta-edit

## 읽어야 할 파일
- `/docs/PRD.md` §5-4 단계 3·`/docs/ADR.md` ADR-016
- `/phases/13-meta-editor-form-ui/`·`/phases/14-meta-editor-drag-and-drop/` 산출물

## 작업
**WYSIWYG PoC** — 실 화면 미리보기에서 클릭·편집하는 작은 시도. 본격 도구가 아닌 평가용.

### 1. 컴포넌트 — `frontend/src/components/editor/WysiwygPreview.vue`
- 좌측: 메타 편집 패널 (기존 FormFieldEditor·GridColumnEditor 축약 버전).
- 우측: 실 DynamicPage 미리보기 + **편집 가능 모드 토글**.
- 편집 모드 ON:
  - 폼 필드 라벨 클릭 → 인플레이스 라벨 편집 (contenteditable 또는 작은 input overlay).
  - 그리드 컬럼 헤더 클릭 → 인플레이스 label·width 편집.
  - 필드 우측에 작은 삭제 아이콘.
  - 변경 즉시 좌측 패널·body draft 동기화.

### 2. 한계
- 본격 WYSIWYG 가 아닌 인플레이스 라벨 편집 + 삭제 수준. 신규 필드 추가·복잡 레이아웃은 좌측 패널 의존.
- Builder.io / GrapeJS 수준이 아님. **PoC 평가용**.

### 3. 라우트
- `/system/meta-editor/:metaId/wysiwyg` — 동일 메타의 WYSIWYG 모드.

### 4. 테스트
- 인플레이스 라벨 편집 → body draft 갱신 확인.
- 삭제 → 필드 제거 확인.

### 5. PoC 평가 점수표
- 학습 비용 / 구현 복잡도 / 실 사용자 만족도 / 디버깅 난이도 — step 2 에서 보고.

## Acceptance Criteria
```bash
cd frontend
test -f src/components/editor/WysiwygPreview.vue
grep -q "meta-editor/.*wysiwyg" src/router/index.ts
pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- 본 step 을 본격 page builder 로 키우지 마라 — PoC 수준 유지.
- 외부 SDK(Builder.io·GrapeJS) 도입 금지 — 자체 인플레이스 편집 시도만.
- 백엔드 수정 금지.
- 운영 코드 console.log 금지.
