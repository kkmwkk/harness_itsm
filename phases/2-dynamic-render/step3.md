# Step 3: dynamic-grid

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/docs/ARCHITECTURE.md` — §6 그리드 렌더러 선택 규칙
- `/docs/ADR.md` — ADR-007 (그리드 이원화 자동 선택 기준)
- `/phases/2-dynamic-render/step0.md`·`step1.md`·`step2.md` — meta-body 타입, DataTable, AG Grid 어댑터
- `/frontend/src/types/meta-body.ts`·`/frontend/src/lib/meta-body.ts`·`/frontend/src/components/ui/data-table/DataTable.vue`·`/frontend/src/lib/ag-grid-modules.ts`·`/frontend/src/assets/styles/ag-theme-itg.css`

## 작업

이 step 의 목적은 **`GridMeta` + `rows` 를 받아 적절한 렌더러를 자동 선택하는 `DynamicGrid` 컴포넌트를 만드는 것**이다. 그리드 선택 로직과 컬럼 변환은 `useGridColumns` composable 로 분리.

### 1. composable — `src/composables/useGridColumns.ts`

`GridMeta` → 두 라이브러리의 컬럼 정의를 모두 만들어주는 헬퍼. 어떤 렌더러를 쓸지 판정도 한다.

```ts
import { computed, type Ref } from 'vue';
import type { ColumnDef } from '@tanstack/vue-table';
import type { ColDef } from 'ag-grid-community';
import type { GridMeta, GridColumnMeta } from '@/types/meta-body';

export type GridRenderer = 'data-table' | 'ag-grid';

/** rows 임계치 — ADR-007. 매직넘버는 한 곳에. */
export const AG_GRID_ROW_THRESHOLD = 1000;

export function decideRenderer(meta: GridMeta, rowCount: number): GridRenderer {
  if (meta.inlineEdit) return 'ag-grid';
  if (meta.export)     return 'ag-grid';
  if (rowCount > AG_GRID_ROW_THRESHOLD) return 'ag-grid';
  return 'data-table';
}

export function toDataTableColumns<TData>(
  meta: GridMeta,
): ColumnDef<TData, unknown>[] {
  return meta.columns.map((c) => ({
    accessorKey: c.field,
    header:      c.label,
    size:        c.width,
    // pinned/flex/hideAt 등은 단순 매핑 — 상세 처리는 후속 phase
  }));
}

export function toAgGridColDefs(meta: GridMeta): ColDef[] {
  return meta.columns.map((c) => {
    const col: ColDef = {
      field:      c.field,
      headerName: c.label,
    };
    if (c.width !== undefined) col.width  = c.width;
    if (c.flex  !== undefined) col.flex   = c.flex;
    if (c.pinned !== undefined) col.pinned = c.pinned;
    return col;
  });
}

/** 단위 테스트 친화: 단일 진입 composable */
export function useGridColumns<TData>(
  meta: Ref<GridMeta>,
  rows: Ref<TData[]>,
) {
  const renderer = computed<GridRenderer>(() =>
    decideRenderer(meta.value, rows.value.length));
  const dataTableColumns = computed(() => toDataTableColumns<TData>(meta.value));
  const agGridColDefs    = computed(() => toAgGridColDefs(meta.value));
  return { renderer, dataTableColumns, agGridColDefs };
}
```

### 2. 새 컴포넌트 — `src/components/dynamic/DynamicGrid.vue`

`useGridColumns` 의 판정에 따라 두 렌더러 중 하나를 선택해서 렌더링한다.

Props:
- `meta: GridMeta`
- `rows: TData[]` (generic)

Emits:
- `row-click`: `(row: TData) => void`

스켈레톤:

```vue
<script setup lang="ts" generic="TData">
import { toRef } from 'vue';
import { AgGridVue } from 'ag-grid-vue3';
import { DataTable } from '@/components/ui/data-table';
import { useGridColumns } from '@/composables/useGridColumns';
import type { GridMeta } from '@/types/meta-body';

interface Props { meta: GridMeta; rows: TData[]; }
const props = defineProps<Props>();
const emit  = defineEmits<{ 'row-click': [row: TData] }>();

const metaRef = toRef(props, 'meta');
const rowsRef = toRef(props, 'rows');
const { renderer, dataTableColumns, agGridColDefs } = useGridColumns<TData>(metaRef, rowsRef);

function onAgRowClick(ev: { data?: TData }) {
  if (ev.data) emit('row-click', ev.data);
}
</script>

<template>
  <DataTable
    v-if="renderer === 'data-table'"
    :rows="rows"
    :columns="dataTableColumns"
    selectable
    @row-click="(r) => emit('row-click', r)"
  />
  <div v-else class="ag-theme-itg" style="height: 600px; width: 100%;">
    <AgGridVue
      :rowData="rows"
      :columnDefs="agGridColDefs"
      :rowSelection="'single'"
      @row-clicked="onAgRowClick"
      style="height: 100%; width: 100%;"
    />
  </div>
</template>
```

> 인라인 편집·export 의 실 동작 구현은 본 phase 범위가 아니다. 메타 플래그가 set 되면 AG Grid 로 분기하는 것까지만. 실 편집·export 액션은 다음 phase 의 ADR.

### 3. 단위 테스트 — `src/composables/__tests__/useGridColumns.spec.ts`

판정 로직 단위 테스트 (Vitest):

1. `decideRenderer_rows_1000_이하_data-table`.
2. `decideRenderer_rows_1001_이상_ag-grid`.
3. `decideRenderer_inlineEdit_true_면_rows_무관_ag-grid`.
4. `decideRenderer_export_true_면_rows_무관_ag-grid`.
5. `toDataTableColumns_meta_컬럼_to_accessorKey_header_size_매핑`.
6. `toAgGridColDefs_meta_컬럼_to_field_headerName_width_flex_pinned_매핑`.

### 4. 통합 검증 페이지 — `src/views/_dev/DynamicGridSampler.vue`

라우트 `/_dev/dynamic-grid` 추가. **자동 분기 검증**:

- 라디오 버튼: `35 rows (DataTable)` / `1500 rows (AG Grid)` / `inlineEdit (AG Grid)` / `export (AG Grid)`.
- 선택에 따라 GridMeta 와 rows 가 바뀌고, 적절한 렌더러가 자동 선택됨.

```vue
<script setup lang="ts">
import { ref, computed } from 'vue';
import { DynamicGrid } from '@/components/dynamic/DynamicGrid.vue';
import type { GridMeta } from '@/types/meta-body';

interface Row { id: string; title: string; status: string; }
const mode = ref<'small'|'large'|'inline'|'export'>('small');

const baseColumns: GridMeta['columns'] = [
  { field: 'id',     label: 'ID',   type: 'text',   width: 200 },
  { field: 'title',  label: '제목', type: 'text',   flex: 1 },
  { field: 'status', label: '상태', type: 'status', width: 120 },
];

const meta = computed<GridMeta>(() => ({
  columns:   baseColumns,
  inlineEdit: mode.value === 'inline',
  export:     mode.value === 'export',
}));

const rows = computed<Row[]>(() => {
  const n = mode.value === 'large' ? 1500 : 35;
  return Array.from({ length: n }, (_, i) => ({
    id:     `itg-sample-${String(i + 1).padStart(4, '0')}`,
    title:  `샘플 항목 ${i + 1}`,
    status: ['DRAFT','PUBLISHED','DEPRECATED','ARCHIVED'][i % 4]!,
  }));
});
</script>

<template>
  <main class="mx-auto max-w-6xl p-6 space-y-4">
    <h1>Dynamic Grid Sampler</h1>
    <div class="space-x-3 text-[13px]">
      <label><input type="radio" v-model="mode" value="small"  /> 35 rows (DataTable)</label>
      <label><input type="radio" v-model="mode" value="large"  /> 1500 rows (AG Grid)</label>
      <label><input type="radio" v-model="mode" value="inline" /> inlineEdit (AG Grid)</label>
      <label><input type="radio" v-model="mode" value="export" /> export (AG Grid)</label>
    </div>
    <DynamicGrid :meta="meta" :rows="rows" />
  </main>
</template>
```

라우트 추가:

```ts
{ path: '/_dev/dynamic-grid', component: () => import('@/views/_dev/DynamicGridSampler.vue') },
```

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 파일 존재
test -f src/composables/useGridColumns.ts
test -f src/components/dynamic/DynamicGrid.vue
test -f src/composables/__tests__/useGridColumns.spec.ts
test -f src/views/_dev/DynamicGridSampler.vue
grep -q '/_dev/dynamic-grid' src/router/index.ts

# 2) 정적 검증 + 단위 테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 3) dev 부팅 + 라우트 200
pnpm dev &
sleep 6
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/_dev/dynamic-grid)" = "200"
kill %1
```

## 검증 절차

1. 위 AC 통과.
2. 아키텍처 체크리스트:
   - `decideRenderer` 의 분기 4종(small/large/inlineEdit/export) 이 ADR-007 과 일치하는가?
   - `AG_GRID_ROW_THRESHOLD = 1000` 한 곳에 정의되어 다른 파일에서 참조 가능한가? (매직넘버 회피)
   - DynamicGrid 가 자체 fetch 코드를 갖지 않고 props 의존인가?
   - 컬럼 변환이 generic 타입 유지(`ColumnDef<TData, unknown>`)되는가?
3. step 3 업데이트:
   - 성공 → `"summary": "useGridColumns composable(decideRenderer + toDataTableColumns + toAgGridColDefs, AG_GRID_ROW_THRESHOLD=1000 단일 정의) + components/dynamic/DynamicGrid.vue (rows>1000 or inlineEdit or export → AG Grid, 그 외 shadcn DataTable). 단위 테스트 6 케이스. DynamicGridSampler 라우트에서 4모드 자동 분기 시각 검증."`

## 금지사항

- `decideRenderer` 의 임계치 1000 을 컴포넌트 내부에 다시 박지 마라. `AG_GRID_ROW_THRESHOLD` 단일 상수만 참조.
- DynamicGrid 에 fetch 코드·페이지네이션 외부 상태를 박지 마라. props/emits 만.
- 인라인 편집·export 실 동작을 이 step 에서 구현하지 마라. 분기까지만.
- AG Grid 의 `RangeSelectionModule` 등 Enterprise 모듈을 추가하지 마라.
- DataTable 또는 AG Grid 의 알파인 등 기본 테마를 이 step 에서 다시 import 하지 마라.
- backend 코드 수정 금지.
