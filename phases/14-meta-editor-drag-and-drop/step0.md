# Step 0: draggable-dependency-and-policy

## 읽어야 할 파일
- `/docs/PRD.md` §5-4·`/docs/ARCHITECTURE.md` §16-2·`/docs/ADR.md` ADR-016
- `/phases/13-meta-editor-form-ui/step2~3.md` (FormFieldEditor·GridColumnEditor)

## 작업
드래그앤드롭 라이브러리 도입·정책 결정·공통 helper.

### 1. 라이브러리 선택
- **vue-draggable-plus** (sortablejs 기반, Vue 3 친화, 가벼움) — 권장.
- 대안: vue-draggable-next, dnd-kit-vue.

```bash
cd frontend
pnpm add vue-draggable-plus
```

### 2. 정책
- 드래그 트리거: 행 좌측 grip 아이콘(`Lucide.GripVertical`) 만. 행 전체 드래그 금지(실수 회피).
- 드롭 영역: 같은 리스트 안에서만(필드 순서 변경). 다른 리스트로 이동 X.
- 트랜지션: 200ms ease-out (UI_GUIDE §9).
- 키보드 접근성: `up/down` 화살표 + `space` 로 들어/내리기 — vue-draggable-plus 가 미지원이면 별도 ADR.

### 3. 공통 helper — `frontend/src/lib/drag.ts`
```ts
// drag end 이벤트 → 배열 재정렬 helper. zod index 검증.
export function reorder<T>(list: T[], oldIndex: number, newIndex: number): T[] { ... }
```

### 4. 단위 테스트
- `reorder_정상_시_새_배열_반환_원본_불변`.
- `reorder_범위_외_index_무시`.

## Acceptance Criteria
```bash
cd frontend
grep -q '"vue-draggable-plus"' package.json
test -f src/lib/drag.ts
test -f src/lib/__tests__/drag.spec.ts
pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- 행 전체 드래그 허용 금지 — grip 핸들만.
- 외부 영역(예: 그리드 → 폼) 으로 드롭 허용 금지.
- 라이브러리 두 개 동시 사용 금지.
