<script setup lang="ts">
import { useRoute } from 'vue-router';
import DynamicPage from '@/components/dynamic/DynamicPage.vue';

// 라우트 meta.groupId 한 값으로 모듈을 매핑 — 새 모듈 = 라우트 한 줄 + 메타 INSERT (ADR-004).
const route = useRoute();
const groupId = String(route.meta.groupId);
</script>

<template>
  <DynamicPage :group-id="groupId" />
</template>
