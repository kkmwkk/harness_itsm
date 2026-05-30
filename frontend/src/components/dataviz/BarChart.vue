<script setup lang="ts">
/**
 * 막대 차트 — vue-echarts wrapper. 옵션은 chart-theme.buildBarOption 으로만 조립.
 */
import { computed } from 'vue';
import VChart from 'vue-echarts';
import { buildBarOption, useChartColors, type SeriesInput } from '@/lib/chart-theme';

const props = withDefaults(
  defineProps<{
    categories: string[];
    series: SeriesInput[];
    height?: number;
    stack?: boolean;
    showLegend?: boolean;
  }>(),
  { height: 240, stack: false, showLegend: false },
);

const colors = useChartColors();

const option = computed(() =>
  buildBarOption(props.categories, props.series, colors.value, {
    stack: props.stack,
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
