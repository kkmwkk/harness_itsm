<script setup lang="ts">
/**
 * 지표 행 — KpiCard 의 인라인(한 줄) 변형. 라벨 + 큰 숫자 + 변화량.
 * 리스트·사이드 패널에서 여러 지표를 조밀하게 나열할 때 사용.
 */
import { computed } from 'vue';
import type { SystemType } from '@/types/meta';
import { moduleVisual } from '@/lib/module-color';
import TrendIndicator from './TrendIndicator.vue';

const props = withDefaults(
  defineProps<{
    label: string;
    value: string | number;
    unit?: string;
    trend?: number;
    trendInvert?: boolean;
    module?: SystemType;
  }>(),
  { unit: undefined, trend: undefined, trendInvert: false, module: undefined },
);

const visual = computed(() => (props.module ? moduleVisual(props.module) : null));
</script>

<template>
  <div class="flex items-center justify-between gap-3 py-2">
    <span class="flex items-center gap-1.5 text-[13px] text-foreground-muted">
      <span
        v-if="visual"
        :class="[visual.bgSoftClass, visual.borderClass, 'inline-block size-2 shrink-0 rounded-full border']"
        aria-hidden="true"
      />
      {{ label }}
    </span>
    <span class="flex items-baseline gap-2">
      <span class="flex items-baseline gap-0.5">
        <span class="text-lg font-bold tabular-nums text-foreground">{{ value }}</span>
        <span
          v-if="unit"
          class="text-xs font-medium text-foreground-muted"
        >{{ unit }}</span>
      </span>
      <TrendIndicator
        v-if="trend !== undefined"
        :value="trend"
        :invert="trendInvert"
      />
    </span>
  </div>
</template>
