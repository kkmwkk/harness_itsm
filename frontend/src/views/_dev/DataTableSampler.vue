<script setup lang="ts">
import type { ColumnDef } from '@tanstack/vue-table';
import { DataTable } from '@/components/ui/data-table';

interface Row {
  id: string;
  title: string;
  status: string;
  assignee: string;
}

const rows: Row[] = Array.from({ length: 35 }, (_, i) => ({
  id: `itg-sample-${String(i + 1).padStart(3, '0')}`,
  title: `샘플 항목 ${i + 1}`,
  status: ['DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED'][i % 4]!,
  assignee: `샘플 사용자 ${i % 5}`,
}));

const columns: ColumnDef<Row>[] = [
  { accessorKey: 'id', header: 'ID', size: 200 },
  { accessorKey: 'title', header: '제목' },
  { accessorKey: 'status', header: '상태', size: 120 },
  { accessorKey: 'assignee', header: '담당자', size: 140 },
];
</script>

<template>
  <main class="mx-auto max-w-5xl p-6 space-y-6">
    <h1>DataTable Sampler</h1>
    <DataTable
      :rows="rows"
      :columns="columns"
      selectable
    />
  </main>
</template>
