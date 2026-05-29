# Step 3: grid-column-editor

## 읽어야 할 파일
- `/docs/PRD.md` §5-4·`/docs/ARCHITECTURE.md` §16
- `/phases/13-meta-editor-form-ui/step0~2.md`
- `/frontend/src/types/meta-body.ts`·`/frontend/src/composables/useGridColumns.ts`

## 작업
**그리드 컬럼 편집 UI** — 메타의 `grid.columns` 배열을 GUI 로 편집.

### 1. `components/editor/GridColumnEditor.vue`
- 컬럼 목록 (현재 grid.columns).
- 각 컬럼 행:
  - field (Input)
  - label (Input)
  - type (Select, FieldType 12종)
  - width (Number Input, px)
  - flex (Number Input, optional)
  - pinned (Select: none/left/right)
  - hideAt (Select: none/sm/md)
  - 삭제 버튼
- 하단: "+ 컬럼 추가".
- inlineEdit / export 토글 (그리드 전역 옵션).

### 2. 통합
- MetaEditorDetailPage 의 두 번째 탭에 통합.
- 저장 시 form 편집과 같은 흐름 (dry-run → PUT body).

### 3. 검증
- field 중복 금지.
- form.fields 의 name 과 매칭되지 않는 grid.column.field 가 있으면 WARNING 표시 (dry-run 의 WARNING 활용).

### 4. 단위 테스트
- `GridColumnEditor.spec` — 컬럼 추가/삭제/속성 변경·검증.

## Acceptance Criteria
```bash
cd frontend
test -f src/components/editor/GridColumnEditor.vue
pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- field 와 form.fields.name 매칭은 client 측 강제 차단하지 마라 — WARNING 만 (서버 측 dry-run 도 동일).
- 컬럼 순서 드래그 본 step 에서 도입 금지 — phase 14.
- type 이 status/priority 일 때 셀 렌더링이 뱃지로 자동 매핑됨을 가이드 텍스트로 안내.
- 백엔드 수정 금지.
