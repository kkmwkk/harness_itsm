<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import {
  LifeBuoyIcon,
  BoxesIcon,
  FolderKanbanIcon,
  LayersIcon,
  SettingsIcon,
} from '@lucide/vue';
import { moduleVisual } from '@/lib/module-color';
import type { SystemType } from '@/types/meta';

/**
 * 페이지 헤더 — 모듈 시각 아이덴티티(UI_GUIDE §3 v2).
 * module 이 주어지면(또는 route.meta.systemType) 좌측 4px 컬러 띠 + 모듈 아이콘 + 컬러 텍스트.
 * 모듈 컬러는 헤더 한정 — 글로벌 버튼·링크로 전염하지 않는다(§2).
 */
const props = defineProps<{
  title?: string;
  module?: SystemType;
}>();

const route = useRoute();

const displayTitle = computed<string>(() => {
  if (props.title) return props.title;
  const metaTitle = route.meta.title;
  return typeof metaTitle === 'string' ? metaTitle : '';
});

// 명시 prop 우선, 없으면 route.meta.systemType 로 폴백 — 모듈 라우트는 자동 적용.
const effectiveModule = computed<SystemType | null>(() => {
  if (props.module) return props.module;
  const m = route.meta.systemType;
  return typeof m === 'string' ? (m as SystemType) : null;
});

const visual = computed(() =>
  effectiveModule.value ? moduleVisual(effectiveModule.value) : null,
);

const MODULE_ICON: Record<SystemType, typeof LifeBuoyIcon> = {
  ITSM: LifeBuoyIcon,
  ITAM: BoxesIcon,
  PMS: FolderKanbanIcon,
  COMMON: LayersIcon,
  SYSTEM: SettingsIcon,
};
const moduleIcon = computed(() =>
  effectiveModule.value ? MODULE_ICON[effectiveModule.value] : null,
);
</script>

<template>
  <header class="flex h-16 items-center justify-between">
    <div class="flex items-center gap-3">
      <!-- 모듈 컬러 띠(4px) — primary 색(토큰 var) -->
      <span
        v-if="visual"
        class="h-7 w-1 rounded-full"
        :style="{ backgroundColor: visual.primaryVar }"
        aria-hidden="true"
      />
      <!-- 모듈 아이콘 (soft 배경 + 모듈 색) -->
      <span
        v-if="visual && moduleIcon"
        :class="[
          'flex size-8 items-center justify-center rounded-md',
          visual.bgSoftClass,
          visual.textClass,
        ]"
        aria-hidden="true"
      >
        <component
          :is="moduleIcon"
          class="size-5"
          :stroke-width="1.5"
        />
      </span>
      <h1 class="text-[28px] font-bold leading-tight tracking-tight text-foreground">
        {{ displayTitle }}
      </h1>
    </div>
    <div class="flex items-center gap-2">
      <slot name="actions" />
    </div>
  </header>
</template>
