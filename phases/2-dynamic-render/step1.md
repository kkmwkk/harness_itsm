# Step 1: shadcn-data-table

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/docs/UI_GUIDE.md` — §5-4 데이터 그리드 (shadcn DataTable: 헤더 13px/600, 행 높이 40px, sticky top, 호버·선택만 — zebra 금지), §11 접근성
- `/docs/ADR.md` — ADR-007 (그리드 이원화: rows<=1000 → shadcn DataTable, 그 외 AG Grid)
- `/phases/2-dynamic-render/step0.md` — `GridMeta`·`GridColumnMeta` 타입 정의
- `/frontend/src/types/meta-body.ts` — 이전 step 산출물
- `/frontend/src/components/ui/table/` — phase 1 step 2 의 shadcn Table primitive

phase 1 의 shadcn Table primitive 가 이미 있다. 그 위에 TanStack Table 을 얹어 DataTable 컴포넌트를 만든다.

## 작업

이 step 의 목적은 **TanStack Table 을 도입하고, `components/ui/data-table/DataTable.vue` 를 만들어 메타·행 데이터를 받아 렌더링하는 기본 DataTable 을 구축하는 것**이다. AG Grid 와의 자동 분기는 step 3 의 `DynamicGrid` 책임.

### 1. 의존성

```bash
cd frontend
pnpm add @tanstack/vue-table
```

### 2. shadcn 가이드의 DataTable 패턴 채택

shadcn-vue 의 공식 DataTable 예시는 TanStack Table + Tailwind 스타일 조합. 이 패턴을 따른다.

### 3. 새 파일

#### `src/components/ui/data-table/DataTable.vue`

Props:
- `columns: ColumnDef<TData>[]` — TanStack 의 컬럼 정의
- `rows: TData[]`
- `density?: 'default' | 'compact'` — UI_GUIDE §5-4 의 40px/36px 분기
- `selectable?: boolean` — 행 선택 활성화 (단일 선택 우선, 다중은 다음 phase 검토)
- `pageSize?: number` — 페이지네이션 페이지 크기 (기본 20). 0 이면 페이지네이션 비활성.

Emits:
- `row-click`: `(row: TData) => void`
- `selection-change`: `(rows: TData[]) => void`

요구사항 (UI_GUIDE §5-4):
- 헤더: `bg-surface-muted` + 13px / weight 600 + sticky `top-0`. 컬럼 정렬 가능 (TanStack 의 `getSortedRowModel`) → 헤더 클릭 시 정렬, 아이콘 표시 (`lucide-vue-next` 의 `ChevronsUpDown`/`ChevronUp`/`ChevronDown`).
- 행 높이: default 40px, compact 36px (CSS 변수 또는 `data-density` 속성).
- 행 디바이더: `border-b border-border-subtle`.
- 호버: `bg-surface-hover`. 선택: `bg-surface-selected`. **zebra-stripe 금지**.
- 페이지네이션: 우측 하단, "이전 / 1·2·3 / 다음" + 페이지당 선택(20·50·100). shadcn `Button variant="ghost" size="sm"` 또는 직접 구성.
- 셀 셀렉트는 키보드만으로 조작 가능: `tabindex="0"` + `aria-selected`.

스켈레톤:

```vue
<script setup lang="ts" generic="TData">
import { ref, computed, watch } from 'vue';
import {
  useVueTable, getCoreRowModel, getSortedRowModel,
  getPaginationRowModel, type ColumnDef, type SortingState,
} from '@tanstack/vue-table';
import {
  Table, TableHeader, TableHead, TableBody, TableRow, TableCell,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { ChevronsUpDown, ChevronUp, ChevronDown } from 'lucide-vue-next';

interface Props {
  columns:    ColumnDef<TData, unknown>[];
  rows:       TData[];
  density?:   'default' | 'compact';
  selectable?: boolean;
  pageSize?:   number;
}
const props = withDefaults(defineProps<Props>(), {
  density: 'default', selectable: false, pageSize: 20,
});

interface Emits {
  (e: 'row-click',        row: TData): void;
  (e: 'selection-change', rows: TData[]): void;
}
const emit = defineEmits<Emits>();

const sorting = ref<SortingState>([]);
const selectedKey = ref<string | null>(null);

const table = useVueTable({
  get data() { return props.rows; },
  get columns() { return props.columns; },
  state: { get sorting() { return sorting.value; } },
  onSortingChange: (u) => { sorting.value = typeof u === 'function' ? u(sorting.value) : u; },
  getCoreRowModel:       getCoreRowModel(),
  getSortedRowModel:     getSortedRowModel(),
  getPaginationRowModel: props.pageSize === 0 ? undefined : getPaginationRowModel(),
  initialState: props.pageSize === 0 ? undefined : { pagination: { pageSize: props.pageSize } },
});

const rowHeight = computed(() => (props.density === 'compact' ? 'h-9' : 'h-10'));
/* ... */
</script>

<template>
  <div class="rounded-lg border border-border overflow-hidden">
    <Table>
      <TableHeader class="bg-surface-muted sticky top-0">
        <TableRow v-for="hg in table.getHeaderGroups()" :key="hg.id">
          <TableHead
            v-for="header in hg.headers"
            :key="header.id"
            :style="header.column.columnDef.size ? { width: header.column.columnDef.size + 'px' } : undefined"
            class="text-[13px] font-semibold cursor-pointer select-none"
            @click="header.column.getToggleSortingHandler()?.($event)"
          >
            <FlexRender :render="header.column.columnDef.header" :props="header.getContext()" />
            <ChevronUp v-if="header.column.getIsSorted() === 'asc'"  class="inline size-3 ml-1" />
            <ChevronDown v-else-if="header.column.getIsSorted() === 'desc'" class="inline size-3 ml-1" />
            <ChevronsUpDown v-else class="inline size-3 ml-1 opacity-40" />
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow
          v-for="row in table.getRowModel().rows"
          :key="row.id"
          :class="[rowHeight, 'hover:bg-surface-hover',
                   selectable && selectedKey === row.id ? 'bg-surface-selected' : '']"
          tabindex="0"
          :aria-selected="selectedKey === row.id"
          @click="selectable && (selectedKey = row.id); emit('row-click', row.original)"
        >
          <TableCell v-for="cell in row.getVisibleCells()" :key="cell.id" class="text-[13px]">
            <FlexRender :render="cell.column.columnDef.cell" :props="cell.getContext()" />
          </TableCell>
        </TableRow>
      </TableBody>
    </Table>

    <!-- pagination footer (간단 버전) -->
    <div v-if="pageSize > 0" class="flex justify-end items-center gap-2 p-3 border-t border-border-subtle">
      <Button variant="ghost" size="sm" :disabled="!table.getCanPreviousPage()"
              @click="table.previousPage()">이전</Button>
      <span class="text-[13px] text-foreground-muted">
        {{ table.getState().pagination.pageIndex + 1 }} / {{ table.getPageCount() }}
      </span>
      <Button variant="ghost" size="sm" :disabled="!table.getCanNextPage()"
              @click="table.nextPage()">다음</Button>
    </div>
  </div>
</template>
```

> `FlexRender` 는 `@tanstack/vue-table` 의 `<FlexRender>` 컴포넌트. import 추가.

#### `src/components/ui/data-table/index.ts`

```ts
export { default as DataTable } from './DataTable.vue';
```

### 4. 검증 페이지 — `src/views/_dev/DataTableSampler.vue`

라우트 `/_dev/data-table` 추가 (router/index.ts).

```vue
<script setup lang="ts" generic="">
import type { ColumnDef } from '@tanstack/vue-table';
import { DataTable } from '@/components/ui/data-table';

interface Row { id: string; title: string; status: string; assignee: string; }

const rows: Row[] = Array.from({ length: 35 }, (_, i) => ({
  id:       `itg-sample-${String(i + 1).padStart(3, '0')}`,
  title:    `샘플 항목 ${i + 1}`,
  status:   ['DRAFT','PUBLISHED','DEPRECATED','ARCHIVED'][i % 4]!,
  assignee: `샘플 사용자 ${i % 5}`,
}));

const columns: ColumnDef<Row>[] = [
  { accessorKey: 'id',       header: 'ID',     size: 200 },
  { accessorKey: 'title',    header: '제목' },
  { accessorKey: 'status',   header: '상태',   size: 120 },
  { accessorKey: 'assignee', header: '담당자', size: 140 },
];
</script>

<template>
  <main class="mx-auto max-w-5xl p-6 space-y-6">
    <h1>DataTable Sampler</h1>
    <DataTable :rows="rows" :columns="columns" selectable />
  </main>
</template>
```

라우트 추가:

```ts
// router/index.ts 의 _dev 그룹에
{ path: '/_dev/data-table', component: () => import('@/views/_dev/DataTableSampler.vue') },
```

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 의존성
grep -q '"@tanstack/vue-table"' package.json

# 2) 파일 존재
test -f src/components/ui/data-table/DataTable.vue
test -f src/components/ui/data-table/index.ts
test -f src/views/_dev/DataTableSampler.vue
grep -q '/_dev/data-table' src/router/index.ts

# 3) 정적 검증
pnpm type-check
pnpm lint
pnpm build

# 4) 단위 테스트 (이전 step) 회귀
pnpm test

# 5) dev 부팅 + 라우트 200
pnpm dev &
sleep 5
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/_dev/data-table)" = "200"
kill %1
```

## 검증 절차

1. 위 AC 전부 통과.
2. 아키텍처 체크리스트:
   - 헤더가 sticky + `bg-surface-muted` + 13px / 600 + 정렬 아이콘 표시인가? (UI_GUIDE §5-4)
   - 행 높이 default 40px (`h-10`), compact 36px (`h-9`) 인가?
   - zebra-stripe 가 없는가? (UI_GUIDE 금지 패턴)
   - 페이지네이션 우측 하단 + 페이지 당 선택은 step 1 의 범위는 아니더라도 prev/next 동작 확인 가능한가?
   - `tabindex` + `aria-selected` 가 행에 적용되었는가? (UI_GUIDE §11)
   - generic component (`<script setup lang="ts" generic="TData">`) 가 TS 5.x 에서 정상 type 체크되는가?
3. index.json 의 step 1 업데이트:
   - 성공 → `"summary": "@tanstack/vue-table 도입. components/ui/data-table/DataTable.vue (generic TData, ColumnDef, sticky 헤더 13px/600, 행 40/36px 분기, 호버·선택 행, zebra 없음, ChevronUp/Down 정렬 아이콘, 간단 페이지네이션 prev/next, tabindex+aria-selected). DataTableSampler 라우트 추가."`

## 금지사항

- AG Grid 의존성을 이 step 에서 추가하지 마라. 이유: step 2 의 책임.
- DataTable 내부에 backend fetch 코드를 박지 마라. 이유: 컴포넌트는 props 로 rows 만 받는다. 데이터 소스는 부모 또는 DynamicPage (step 5) 의 책임.
- zebra-stripe (`even:bg-...`) 를 추가하지 마라. UI_GUIDE 금지 패턴.
- 컬럼별 다른 폰트·보더 색을 사용하지 마라. UI_GUIDE.
- TanStack 의 `getFilteredRowModel` (필터) 을 이 step 에서 도입하지 마라. 이유: scope. 필터는 다음 phase 의 ADR 후 도입.
- backend 코드 수정 금지.
