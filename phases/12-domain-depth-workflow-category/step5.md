# Step 5: asset-category-frontend

## 읽어야 할 파일
- `/CLAUDE.md`·`/docs/ARCHITECTURE.md` §14-3
- `/phases/12-domain-depth-workflow-category/step1~2.md`
- `/frontend/src/pages/itam/DetailPage.vue` (기존 자산 상세)

## 작업
자산 분류 트리 UI + 분류별 메타 분기 + 라이프사이클 이벤트 표시.

### 1. 타입 — `src/types/asset-category.ts`
```ts
export interface AssetCategoryNode {
  code: string; label: string; parentCode: string | null; path: string;
  formMetaGroupId: string | null; sortOrder: number; active: boolean;
  children: AssetCategoryNode[];
}

export interface LifecycleEvent {
  id: number; assetId: number;
  eventType: 'ACQUIRED'|'TRANSFERRED'|'REPAIRED'|'DISPOSED'|'RENEWED';
  eventDate: string; byUserId: number | null;
  payload: Record<string, unknown> | null; createdAt: string;
}
```

### 2. composable — `src/composables/useAssetCategory.ts`
- `useAssetCategoryTree()` → `/api/asset-categories/tree` fetch
- `findByCode(tree, code)` helper

### 3. `/itam` 진입 UI 확장
**분류 트리 사이드 + 분류 선택 시 그 분류의 form_meta_group_id 로 DynamicPage 메타 분기:**

```
┌────────────────────────────────────────────────────────────────┐
│ ITAM 자산원장                                                  │
├──────────────┬─────────────────────────────────────────────────┤
│ 분류 트리    │  선택된 분류의 그리드 + 등록 폼                  │
│  HW          │  - HW_LAPTOP 선택 시 itg-asset-hw-laptop 메타로  │
│   ├ 노트북   │    DynamicPage 동작                              │
│   ├ 서버     │  - 자산 클릭 → /itam/:id 상세 (등록 메타로 복원) │
│   └ 모니터   │                                                 │
│  SW          │                                                 │
│   └ 라이선스 │                                                 │
└──────────────┴─────────────────────────────────────────────────┘
```

구현:
- `pages/itam/IndexPage.vue` 가 트리 + DynamicPage 2 컬럼.
- 트리 클릭 → `selectedCategoryCode` reactive → 그 분류의 `formMetaGroupId` 를 `<DynamicPage :groupId="..." />` 의 prop 으로 전달.
- 분류 미선택 시: 안내 메시지 ("분류를 선택하세요").

router 의 itam 라우트가 기존에 `_DynamicRoute.vue` 였다면, `IndexPage.vue` 로 교체.

### 4. 자산 상세 페이지 확장
- 기존 DetailPage 에 라이프사이클 이벤트 타임라인 카드 추가.
- `GET /api/assets/{id}/lifecycle-events` fetch.
- 이벤트 기록 다이얼로그 (TRANSFER·REPAIR·DISPOSE — payload JSONB 입력).

### 5. 등록 흐름
- 트리에서 분류 선택 → 등록 버튼 → 그 분류의 메타로 DynamicForm.
- 폼 submit 시 categoryCode 자동 첨부 (분류 트리 상태에서).

### 6. 단위 테스트
- `useAssetCategory.spec` — fetch·find.
- `AssetCategoryTree.spec` — 재귀 렌더링·selectedCode highlight.

## Acceptance Criteria
```bash
cd frontend
test -f src/types/asset-category.ts
test -f src/composables/useAssetCategory.ts
test -f src/components/itam/AssetCategoryTree.vue
test -f src/pages/itam/IndexPage.vue
grep -q "useAssetCategoryTree\|asset-categories/tree" src/pages/itam/IndexPage.vue

pnpm type-check
pnpm lint
pnpm build
pnpm test

pnpm dev &
sleep 6
for p in /itam /itam/1; do
  test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173$p)" = "200"
done
kill %1
```

## 금지사항
- 분류 트리에 비-활성 분류 노출 금지.
- 분류 미선택 시 그리드 자동 fetch 금지 (불필요한 API 호출).
- 이벤트 payload JSONB 에 raw textarea 만 두지 마라 — 이벤트 타입별 권장 필드 가이드 노출.
- 백엔드 수정 금지.
- 운영 코드 console.log 금지.
