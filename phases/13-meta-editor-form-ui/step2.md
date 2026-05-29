# Step 2: form-field-editor

## 읽어야 할 파일
- `/docs/PRD.md` §5-4·`/docs/ARCHITECTURE.md` §16-1
- `/phases/13-meta-editor-form-ui/step0~1.md`
- `/frontend/src/types/meta-body.ts`·`/frontend/src/components/dynamic/DynamicForm.vue`

## 작업
**폼 필드 편집 UI** — 메타의 `form.fields` 배열을 GUI 로 편집.

### 1. 라우트
- `/system/meta-editor/:metaId(.+)` — 특정 메타 편집 진입.

### 2. `pages/system/MetaEditorDetailPage.vue`
- 상단: 메타 정보 (id·title·status·version) + "DRAFT 만 편집 가능" 안내.
- 본문: 탭/분할 — `[폼 편집] [그리드 편집] [액션 편집] [미리보기]` (step 2~4 가 각 탭 담당).

### 3. `components/editor/FormFieldEditor.vue`
- 필드 목록 (현재 form.fields).
- 각 필드 행:
  - 라벨 (Input)
  - name (Input, snake-case 또는 camelCase 검증)
  - type (Select, FieldType 12종)
  - required (Checkbox)
  - span (Radio: 1/2)
  - placeholder·helpText·maxLength·min·max (조건부 Input)
  - options (type=select/radio 일 때): 옵션 행 추가/삭제 (value·label)
  - 삭제 버튼 (휴지통 아이콘)
- 하단: "+ 필드 추가" 버튼 → 신규 필드 (기본 type=text).
- 본 step: **순서 변경(드래그)은 phase 14**. 본 step 은 추가/삭제/필드 속성 편집만.

### 4. 변경 흐름
- 모든 편집은 client 측 state(`bodyDraft: PageMetaBody`) 에서.
- "저장" 버튼 → dry-run 자동 → PUT `/api/meta/{id}/body` → 성공 토스트 + 라우터 stay.
- "취소" 또는 페이지 이탈 시 변경사항 손실 확인 (vue-router beforeRouteLeave guard).

### 5. 검증
- 필드 name 중복 금지 (client 검증).
- 필수 속성(name·label·type) 누락 시 저장 비활성.
- options 가 비어있고 type=select/radio 면 경고.

### 6. 단위 테스트
- `FormFieldEditor.spec` — 필드 추가/삭제/속성 변경.
- 중복 name 검증.

## Acceptance Criteria
```bash
cd frontend
test -f src/pages/system/MetaEditorDetailPage.vue
test -f src/components/editor/FormFieldEditor.vue
grep -q "meta-editor/:metaId" src/router/index.ts

pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- JSON 본문을 textarea 로 직접 노출 금지 — GUI 만.
- 저장 전 dry-run 호출 누락 금지.
- DRAFT 가 아닌 메타에 저장 버튼 활성 금지.
- options 없이 select 필드 저장 허용 금지 (또는 강력 경고).
- 백엔드 수정 금지.
- 운영 코드 console.log 금지.
