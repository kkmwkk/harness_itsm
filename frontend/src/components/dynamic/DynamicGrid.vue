<script setup lang="ts" generic="TData">
import { toRef } from 'vue';
import { AgGridVue } from 'ag-grid-vue3';
import { DataTable } from '@/components/ui/data-table';
import { useGridColumns } from '@/composables/useGridColumns';
import type { GridMeta } from '@/types/meta-body';

interface Props {
  meta: GridMeta;
  rows: TData[];
}
const props = defineProps<Props>();
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
  <DataTable
    v-if="renderer === 'data-table'"
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
