<script setup lang="ts" generic="TData">
import { computed, toRef } from 'vue';
import { AgGridVue } from 'ag-grid-vue3';
import { DataTable } from '@/components/ui/data-table';
import Skeleton from '@/components/feedback/Skeleton.vue';
import { useGridColumns } from '@/composables/useGridColumns';
import type { GridMeta } from '@/types/meta-body';

interface Props {
  meta: GridMeta;
  rows: TData[];
  /** 최초 로딩 중 여부 — rows 가 비어있을 때 스켈레톤 그리드를 표시한다(UI_GUIDE §9). */
  loading?: boolean;
}
const props = defineProps<Props>();

// 스켈레톤 컬럼 수 — 메타 컬럼 수 기준(최소 3). 첫 컬럼은 넓게 그려 실제 그리드와 결을 맞춘다.
const skeletonCols = computed(() => Math.max(props.meta.columns.length, 3));
const showSkeleton = computed(() => props.loading === true && props.rows.length === 0);
const emit = defineEmits<{ 'row-click': [row: TData] }>();

const metaRef = toRef(props, 'meta');
const rowsRef = toRef(props, 'rows');
const { renderer, dataTableColumns, agGridColDefs } = useGridColumns<TData>(
  metaRef,
  rowsRef,
);

function onAgRowClick(ev: { data?: TData }): void {
  if (ev.data) emit('row-click', ev.data);
}
</script>

<template>
  <!-- 최초 로딩 스켈레톤 — 헤더 1줄 + 본문 8줄 (스피너 대체 금지, UI_GUIDE §9). -->
  <div
    v-if="showSkeleton"
    class="overflow-hidden rounded-lg border border-border"
    aria-busy="true"
  >
    <div class="flex gap-4 border-b border-border-subtle bg-surface-muted px-4 py-2.5">
      <Skeleton
        v-for="i in skeletonCols"
        :key="`h-${i}`"
        :width="i === 1 ? '36%' : '72px'"
        height="0.8rem"
      />
    </div>
    <div
      v-for="r in 8"
      :key="`r-${r}`"
      class="flex gap-4 border-b border-border-subtle px-4 py-3 last:border-b-0"
    >
      <Skeleton
        v-for="i in skeletonCols"
        :key="`c-${r}-${i}`"
        :width="i === 1 ? '36%' : '72px'"
        height="0.85rem"
      />
    </div>
  </div>

  <DataTable
    v-else-if="renderer === 'data-table'"
    :rows="rows"
    :columns="dataTableColumns"
    selectable
    @row-click="(r: TData) => emit('row-click', r)"
  />
  <div
    v-else
    class="ag-theme-itg"
    style="height: 600px; width: 100%"
  >
    <AgGridVue
      :row-data="rows"
      :column-defs="agGridColDefs"
      row-selection="single"
      style="height: 100%; width: 100%"
      @row-clicked="onAgRowClick"
    />
  </div>
</template>
