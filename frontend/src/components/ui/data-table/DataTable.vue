<script setup lang="ts" generic="TData">
import { ref, computed } from 'vue';
import {
  useVueTable,
  getCoreRowModel,
  getSortedRowModel,
  getPaginationRowModel,
  FlexRender,
  type ColumnDef,
  type SortingState,
  type Row,
} from '@tanstack/vue-table';
import {
  Table,
  TableHeader,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableEmpty,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import EmptyState from '@/components/feedback/EmptyState.vue';
import { UI } from '@/lib/ui-messages';
import { ChevronsUpDown, ChevronUp, ChevronDown } from 'lucide-vue-next';
import { InboxIcon } from '@lucide/vue';

interface Props {
  columns: ColumnDef<TData, unknown>[];
  rows: TData[];
  /** 행 높이 분기 — default 40px / compact 36px (UI_GUIDE §5-4) */
  density?: 'default' | 'compact';
  /** 행 선택 활성화 (단일 선택) */
  selectable?: boolean;
  /** 페이지네이션 페이지 크기 (기본 20). 0 이면 페이지네이션 비활성. */
  pageSize?: number;
}
const props = withDefaults(defineProps<Props>(), {
  density: 'default',
  selectable: false,
  pageSize: 20,
});

interface Emits {
  (e: 'row-click', row: TData): void;
  (e: 'selection-change', rows: TData[]): void;
}
const emit = defineEmits<Emits>();

const sorting = ref<SortingState>([]);
const selectedKey = ref<string | null>(null);

const table = useVueTable({
  get data() {
    return props.rows;
  },
  get columns() {
    return props.columns;
  },
  state: {
    get sorting() {
      return sorting.value;
    },
  },
  onSortingChange: (u) => {
    sorting.value = typeof u === 'function' ? u(sorting.value) : u;
  },
  getCoreRowModel: getCoreRowModel(),
  getSortedRowModel: getSortedRowModel(),
  getPaginationRowModel: props.pageSize === 0 ? undefined : getPaginationRowModel(),
  initialState:
    props.pageSize === 0 ? undefined : { pagination: { pageSize: props.pageSize } },
});

const rowHeight = computed(() => (props.density === 'compact' ? 'h-9' : 'h-10'));

// 빈 상태(0건)를 한 행으로 펼치기 위한 컬럼 수 (최소 1).
const columnCount = computed(() => props.columns.length || 1);
const isEmpty = computed(() => props.rows.length === 0);

const pageSizeOptions = [20, 50, 100];

function handleRowClick(row: Row<TData>): void {
  if (props.selectable) {
    selectedKey.value = selectedKey.value === row.id ? null : row.id;
    const selected = selectedKey.value === null ? [] : [row.original];
    emit('selection-change', selected);
  }
  emit('row-click', row.original);
}

function onPageSizeChange(event: Event): void {
  const value = Number((event.target as HTMLSelectElement).value);
  table.setPageSize(value);
}
</script>

<template>
  <div class="rounded-lg border border-border overflow-hidden">
    <Table>
      <TableHeader class="bg-surface-muted sticky top-0 z-10">
        <TableRow v-for="hg in table.getHeaderGroups()" :key="hg.id">
          <TableHead
            v-for="header in hg.headers"
            :key="header.id"
            :style="
              header.column.columnDef.size
                ? { width: header.column.columnDef.size + 'px' }
                : undefined
            "
            :aria-sort="
              header.column.getIsSorted() === 'asc'
                ? 'ascending'
                : header.column.getIsSorted() === 'desc'
                  ? 'descending'
                  : undefined
            "
            class="text-[13px] font-semibold tracking-[0.2px] select-none"
            :class="header.column.getCanSort() ? 'cursor-pointer' : ''"
            @click="header.column.getToggleSortingHandler()?.($event)"
          >
            <FlexRender
              :render="header.column.columnDef.header"
              :props="header.getContext()"
            />
            <template v-if="header.column.getCanSort()">
              <ChevronUp
                v-if="header.column.getIsSorted() === 'asc'"
                class="inline size-3 ml-1"
              />
              <ChevronDown
                v-else-if="header.column.getIsSorted() === 'desc'"
                class="inline size-3 ml-1"
              />
              <ChevronsUpDown v-else class="inline size-3 ml-1 opacity-40" />
            </template>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableEmpty
          v-if="isEmpty"
          :colspan="columnCount"
        >
          <EmptyState
            :icon="InboxIcon"
            :title="UI.empty.grid"
          />
        </TableEmpty>
        <TableRow
          v-for="row in table.getRowModel().rows"
          :key="row.id"
          :class="[
            rowHeight,
            'border-b border-border-subtle hover:bg-surface-hover',
            selectable && selectedKey === row.id ? 'bg-surface-selected' : '',
          ]"
          tabindex="0"
          :aria-selected="selectable ? selectedKey === row.id : undefined"
          @click="handleRowClick(row)"
          @keydown.enter.prevent="handleRowClick(row)"
          @keydown.space.prevent="handleRowClick(row)"
        >
          <TableCell
            v-for="cell in row.getVisibleCells()"
            :key="cell.id"
            class="text-[13px]"
          >
            <FlexRender
              :render="cell.column.columnDef.cell"
              :props="cell.getContext()"
            />
          </TableCell>
        </TableRow>
      </TableBody>
    </Table>

    <div
      v-if="pageSize > 0"
      class="flex justify-end items-center gap-3 p-3 border-t border-border-subtle"
    >
      <select
        class="h-8 rounded-md border border-border bg-surface px-2 text-[13px] text-foreground"
        :value="table.getState().pagination.pageSize"
        @change="onPageSizeChange"
      >
        <option v-for="size in pageSizeOptions" :key="size" :value="size">
          {{ size }} / 페이지
        </option>
      </select>
      <Button
        variant="ghost"
        size="sm"
        :disabled="!table.getCanPreviousPage()"
        @click="table.previousPage()"
      >
        이전
      </Button>
      <span class="text-[13px] text-foreground-muted tabular-nums">
        {{ table.getState().pagination.pageIndex + 1 }} / {{ table.getPageCount() }}
      </span>
      <Button
        variant="ghost"
        size="sm"
        :disabled="!table.getCanNextPage()"
        @click="table.nextPage()"
      >
        다음
      </Button>
    </div>
  </div>
</template>
