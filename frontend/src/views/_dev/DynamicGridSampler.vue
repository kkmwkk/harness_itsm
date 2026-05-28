<script setup lang="ts">
import { ref, computed } from 'vue';
import DynamicGrid from '@/components/dynamic/DynamicGrid.vue';
import type { GridMeta } from '@/types/meta-body';

interface Row {
  id: string;
  title: string;
  status: string;
}
const mode = ref<'small' | 'large' | 'inline' | 'export'>('small');

const baseColumns: GridMeta['columns'] = [
  { field: 'id', label: 'ID', type: 'text', width: 200 },
  { field: 'title', label: '제목', type: 'text', flex: 1 },
  { field: 'status', label: '상태', type: 'status', width: 120 },
];

const meta = computed<GridMeta>(() => ({
  columns: baseColumns,
  inlineEdit: mode.value === 'inline',
  export: mode.value === 'export',
}));

const rows = computed<Row[]>(() => {
  const n = mode.value === 'large' ? 1500 : 35;
  return Array.from({ length: n }, (_, i) => ({
    id: `itg-sample-${String(i + 1).padStart(4, '0')}`,
    title: `샘플 항목 ${i + 1}`,
    status: ['DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED'][i % 4]!,
  }));
});
</script>

<template>
  <main class="mx-auto max-w-6xl p-6 space-y-4">
    <h1>Dynamic Grid Sampler</h1>
    <div class="space-x-3 text-[13px]">
      <label><input
        v-model="mode"
        type="radio"
        value="small"
      > 35 rows (DataTable)</label>
      <label><input
        v-model="mode"
        type="radio"
        value="large"
      > 1500 rows (AG Grid)</label>
      <label><input
        v-model="mode"
        type="radio"
        value="inline"
      > inlineEdit (AG Grid)</label>
      <label><input
        v-model="mode"
        type="radio"
        value="export"
      > export (AG Grid)</label>
    </div>
    <DynamicGrid
      :meta="meta"
      :rows="rows"
    />
  </main>
</template>
