# Step 9: form-widget-enhancement

## 읽어야 할 파일
- `/docs/PRD.md` §4-2-a / §5-4 (FieldType 12종)
- `/frontend/src/components/dynamic/DynamicForm.vue`
- `/frontend/src/types/meta-body.ts` (FieldType)

## 작업
**FieldType 풀 구현 + 풍부한 폼 위젯** — date / date-range / user-picker / file / status / priority 등 placeholder 였던 필드들을 본격 위젯으로 교체.

### 1. DatePicker — `frontend/src/components/common/DatePicker.vue`
shadcn-vue 의 calendar + popover 도입:
```bash
pnpm dlx shadcn-vue@latest add calendar popover
```

- 입력 box 클릭 → 캘린더 popover.
- ko-KR locale.
- 다크 모드 자동.
- DynamicForm 의 type='date' 필드에서 사용.

### 2. DateRangePicker — `frontend/src/components/common/DateRangePicker.vue`
- 두 칸 (from·to).
- 캘린더에서 범위 선택.
- type='date-range' 필드에서 사용.

### 3. UserPicker — `frontend/src/components/common/UserPicker.vue`
- 입력 시 `/api/users?kw=...` 자동완성 (debounced).
- 선택된 사용자: Avatar + name + 제거 버튼.
- type='user-picker' 필드 사용.

### 4. FileUpload — `frontend/src/components/common/FileUpload.vue`
- 드래그앤드롭 영역 + "파일 선택" 버튼.
- 다중 파일 지원.
- 미리보기 (이미지 thumbnail·기타 아이콘).
- 백엔드 endpoint stub: `POST /api/attachments` (multipart) — 본 step 에서 단순 stub 구현 (저장 X, 메타 응답만).

### 5. MarkdownEditor — `frontend/src/components/common/MarkdownEditor.vue`
- 좌측 textarea + 우측 미리보기 (marked + DOMPurify).
- type='textarea' 의 옵션 `markdown: true` 일 때 사용.

### 6. SliderInput·ColorPicker (선택)
- type='number' 의 옵션 `widget: 'slider'` 일 때 SliderInput.
- 자산 분류 메타 등의 색 필드용 ColorPicker (옵션).

### 7. DynamicForm 매핑 갱신
`useFormSchema` 의 type → 컴포넌트 매핑을 확장. 기존 fallback Input 으로 가던 6 종이 본격 위젯으로.

### 8. 단위 테스트
- 각 위젯 props·emit 검증.
- DynamicForm 의 type 분기 매트릭스.

## Acceptance Criteria
```bash
cd frontend
test -f src/components/common/DatePicker.vue
test -f src/components/common/DateRangePicker.vue
test -f src/components/common/UserPicker.vue
test -f src/components/common/FileUpload.vue
test -f src/components/common/MarkdownEditor.vue
grep -q "DatePicker\|UserPicker\|FileUpload" src/components/dynamic/DynamicForm.vue
pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- date / date-range 필드에 native `<input type="date">` 그대로 두지 마라 — DatePicker 강제.
- UserPicker 가 모든 keystroke 마다 API 호출 X — debounce 200ms.
- FileUpload 의 실 저장은 stub (저장소 도입은 별도 ADR).
- MarkdownEditor 의 미리보기에 raw HTML 렌더 X — DOMPurify.
- 운영 코드 console.log 금지.
