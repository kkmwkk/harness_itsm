<script setup lang="ts">
import StatusBadge from '@/components/common/StatusBadge.vue';
import PriorityBadge from '@/components/common/PriorityBadge.vue';

const statuses = [
  'DRAFT',
  'PUBLISHED',
  'DEPRECATED',
  'ARCHIVED',
  'OPEN',
  'IN_PROGRESS',
  'RESOLVED',
  'CLOSED',
  'UNKNOWN',
];
const priorities = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
</script>

<template>
  <main class="mx-auto max-w-3xl p-6 space-y-8">
    <section class="space-y-3">
      <h1>StatusBadge</h1>
      <div class="flex flex-wrap gap-2">
        <StatusBadge
          v-for="s in statuses"
          :key="s"
          :value="s"
        />
      </div>
    </section>

    <section class="space-y-3">
      <h2>PriorityBadge</h2>
      <div class="flex flex-wrap gap-2">
        <PriorityBadge
          v-for="p in priorities"
          :key="p"
          :value="p"
        />
      </div>
    </section>
  </main>
</template>
