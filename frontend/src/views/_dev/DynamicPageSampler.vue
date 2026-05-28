<script setup lang="ts">
import { ref } from 'vue';
import DynamicPage from '@/components/dynamic/DynamicPage.vue';

const target = ref<'grid' | 'aggrid' | 'no-meta'>('grid');

const gridRows = Array.from({ length: 18 }, (_, i) => ({
  id: `itg-row-${String(i + 1).padStart(3, '0')}`,
  title: `샘플 항목 ${i + 1}`,
  status: ['DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED'][i % 4]!,
  assignee: `샘플 사용자 ${i % 5}`,
  createdAt: `2026-05-${String((i % 28) + 1).padStart(2, '0')}`,
}));

const agRows = Array.from({ length: 25 }, (_, i) => ({
  assetNo: `AST-${String(i + 1).padStart(5, '0')}`,
  name: `샘플 자산 ${i + 1}`,
  category: ['서버', '노트북', '모니터', '네트워크'][i % 4]!,
  owner: `샘플 사용자 ${i % 6}`,
  acquiredAt: `2026-${String((i % 12) + 1).padStart(2, '0')}-15`,
}));
</script>

<template>
  <main class="mx-auto max-w-6xl p-6 space-y-4">
    <h1>Dynamic Page Sampler (Phase 2 E2E)</h1>
    <div class="space-x-3 text-[13px]">
      <label><input
        v-model="target"
        type="radio"
        value="grid"
      > itg-mock-grid (DataTable)</label>
      <label><input
        v-model="target"
        type="radio"
        value="aggrid"
      > itg-mock-aggrid (AG Grid, inlineEdit)</label>
      <label><input
        v-model="target"
        type="radio"
        value="no-meta"
      > 미존재 그룹 (notPublished)</label>
    </div>

    <DynamicPage
      v-if="target === 'grid'"
      group-id="itg-mock-grid"
      :rows="gridRows"
    />
    <DynamicPage
      v-if="target === 'aggrid'"
      group-id="itg-mock-aggrid"
      :rows="agRows"
    />
    <DynamicPage
      v-if="target === 'no-meta'"
      group-id="itg-mock-missing"
    />
  </main>
</template>
