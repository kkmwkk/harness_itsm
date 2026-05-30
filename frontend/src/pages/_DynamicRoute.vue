<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
import DynamicPage from '@/components/dynamic/DynamicPage.vue';
import type { SystemType } from '@/types/meta';

// 라우트 meta.groupId 한 값으로 모듈을 매핑 — 새 모듈 = 라우트 한 줄 + 메타 INSERT (ADR-004).
const route = useRoute();
const router = useRouter();
const groupId = String(route.meta.groupId);

// 라우트 meta.systemType 으로 헤더 모듈 시각 아이덴티티를 결정(메타 로드 전에도 즉시 적용).
const systemType =
  typeof route.meta.systemType === 'string'
    ? (route.meta.systemType as SystemType)
    : undefined;

// 상세 라우팅은 route.meta.detailUrlTemplate 로 결정 — 도메인 하드코딩 없이 meta-driven (ADR-004).
// 템플릿이 없는 모듈은 행 클릭이 no-op.
function onRowClick(row: unknown): void {
  const template = route.meta.detailUrlTemplate;
  if (typeof template !== 'string') return;
  const r = row as { id?: number };
  if (typeof r.id !== 'number') return;
  void router.push(template.replace('{id}', String(r.id)));
}
</script>

<template>
  <DynamicPage
    :group-id="groupId"
    :module="systemType"
    @row-click="onRowClick"
  />
</template>
