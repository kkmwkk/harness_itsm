<script setup lang="ts">
import { AgGridVue } from 'ag-grid-vue3';
import type { ColDef } from 'ag-grid-community';

interface Row {
  id: string;
  title: string;
  status: string;
  assignee: string;
  createdAt: string;
}

const rows: Row[] = Array.from({ length: 1500 }, (_, i) => ({
  id: `itg-sample-${String(i + 1).padStart(4, '0')}`,
  title: `샘플 항목 ${i + 1}`,
  status: ['DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED'][i % 4]!,
  assignee: `샘플 사용자 ${i % 7}`,
  createdAt: `2026-05-${String((i % 28) + 1).padStart(2, '0')}`,
}));

const columnDefs: ColDef[] = [
  { field: 'id', headerName: 'ID', width: 200, pinned: 'left' },
  { field: 'title', headerName: '제목', flex: 1 },
  { field: 'status', headerName: '상태', width: 120 },
  { field: 'assignee', headerName: '담당자', width: 140 },
  { field: 'createdAt', headerName: '등록일', width: 120 },
];

const defaultColDef: ColDef = {
  sortable: true,
  resizable: true,
};
</script>

<template>
  <main class="mx-auto max-w-6xl p-6 space-y-4">
    <h1>AG Grid Sampler (1500 rows)</h1>
    <div
      class="ag-theme-itg"
      style="height: 600px; width: 100%"
    >
      <AgGridVue
        :row-data="rows"
        :column-defs="columnDefs"
        :default-col-def="defaultColDef"
        row-selection="single"
        style="height: 100%; width: 100%"
      />
    </div>
  </main>
</template>
