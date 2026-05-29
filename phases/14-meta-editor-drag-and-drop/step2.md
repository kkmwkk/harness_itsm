# Step 2: grid-column-drag-sort-and-resize

## 읽어야 할 파일
- `/phases/13-meta-editor-form-ui/step3.md`
- `/phases/14-meta-editor-drag-and-drop/step0~1.md`

## 작업
GridColumnEditor 의 컬럼 순서 드래그 + width resize.

### 1. GridColumnEditor 갱신
- 컬럼 리스트에 `<VueDraggable>` 적용 (FormFieldEditor 동일 패턴).
- 각 컬럼 행 grip + width input + flex toggle.
- pinned 토글 (left/right/none) — 라디오 또는 dropdown.

### 2. width resize 인라인
- width 인풋을 단순 number + "px 단위 또는 flex 사용" toggle.
- flex 모드: width 인풋 비활성.

### 3. 시각 미리보기 (선택)
- 컬럼 리스트 우측에 그리드 헤더 미리보기 stripe (width 비례).

### 4. 테스트
- `GridColumnEditor.spec` — 드래그·width·pinned 토글.

## Acceptance Criteria
```bash
cd frontend
grep -q "VueDraggable\|vue-draggable-plus" src/components/editor/GridColumnEditor.vue
pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- 그리드 미리보기에 실 데이터 fetch 금지 — 시각 stripe 만.
- pinned 가 left·right 둘 다인 컬럼 허용 금지.
- 백엔드 수정 금지.
