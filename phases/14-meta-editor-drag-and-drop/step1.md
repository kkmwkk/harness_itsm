# Step 1: form-field-drag-sort-and-span

## 읽어야 할 파일
- `/phases/13-meta-editor-form-ui/step2.md` (FormFieldEditor)
- `/phases/14-meta-editor-drag-and-drop/step0.md`
- `/frontend/src/components/editor/FormFieldEditor.vue`

## 작업
FormFieldEditor 의 필드 순서 드래그 + span 인라인 토글.

### 1. FormFieldEditor 갱신
- `<VueDraggable>` 으로 필드 리스트 wrap.
- 각 필드 행 좌측에 `<GripVerticalIcon>` 핸들.
- 드래그 종료 → `reorder(fields, oldIdx, newIdx)` → state 갱신.
- span 토글: "전체 폭" / "반 폭" 두 버튼. 1↔2.

### 2. 시각·UX
- 드래그 중 행: opacity 0.5 · border-color primary.
- 드롭 위치: 파선 indicator.
- 키보드: 행 포커스 + Alt+↑/↓ 로 순서 변경 (선택 — 시간 여유 있으면).

### 3. 저장 흐름
- 순서/span 변경도 client state 업데이트 → 저장 버튼 활성 → dry-run → PUT body.

### 4. 테스트
- `FormFieldEditor.spec` (vue-test-utils + happy-dom):
  - 드래그 시뮬레이션 → 배열 순서 변경 검증 (라이브러리 mock 필요).
  - span 토글 시 1↔2 전환.

## Acceptance Criteria
```bash
cd frontend
grep -q "VueDraggable\|vue-draggable-plus" src/components/editor/FormFieldEditor.vue
grep -q "GripVerticalIcon\|GripVertical" src/components/editor/FormFieldEditor.vue
pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- 드래그 트리거를 행 전체로 확장 금지.
- 드래그 중 다른 필드 폼 입력 동시 가능하게 만들지 마라 (포커스 충돌).
- span 외 다른 layout(grid 3 column 등) 도입 금지 — 본 phase 범위 밖.
- 백엔드 수정 금지.
