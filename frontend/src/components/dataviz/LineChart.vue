<script setup lang="ts">
/**
 * 선형(추세) 차트 — vue-echarts wrapper. 옵션은 chart-theme.buildLineOption 으로만 조립.
 */
import { computed } from 'vue';
import VChart from 'vue-echarts';
import { buildLineOption, useChartColors, type SeriesInput } from '@/lib/chart-theme';

const props = withDefaults(
  defineProps<{
    categories: string[];
    series: SeriesInput[];
    height?: number;
    smooth?: boolean;
    area?: boolean;
    showLegend?: boolean;
  }>(),
  { height: 240, smooth: true, area: false, showLegend: false },
);

const colors = useChartColors();

const option = computed(() =>
  buildLineOption(props.categories, props.series, colors.value, {
    smooth: props.smooth,
    area: props.area,
    showLegend: props.showLegend,
  }),
);
</script>

<template>
  <VChart
    :option="option"
    :style="{ height: `${height}px`, width: '100%' }"
    autoresize
  />
</template>
