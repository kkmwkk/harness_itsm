<script setup lang="ts">
/**
 * 미니 라인(스파크라인) — 축·그리드·툴팁 없이 추세선만. 기본 60×24px.
 * 색은 module prop → 모듈 토큰 색, 또는 명시 color. 다크 모드 자동 반응.
 */
import { computed } from 'vue';
import VChart from 'vue-echarts';
import type { SystemType } from '@/types/meta';
import { buildSparklineOption, moduleChartColor, useChartColors } from '@/lib/chart-theme';

const props = withDefaults(
  defineProps<{
    data: number[];
    module?: SystemType;
    /** module 보다 우선하는 명시 색(토큰 var 권장). */
    color?: string;
    width?: number;
    height?: number;
  }>(),
  { module: undefined, color: undefined, width: 60, height: 24 },
);

const colors = useChartColors();

const lineColor = computed(() => {
  if (props.color) return props.color;
  if (props.module) return moduleChartColor(props.module, colors.value);
  return colors.value.primary;
});

const option = computed(() => buildSparklineOption(props.data, lineColor.value, colors.value));
</script>

<template>
  <VChart
    class="shrink-0"
    :option="option"
    :style="{ width: `${width}px`, height: `${height}px` }"
    autoresize
  />
</template>
