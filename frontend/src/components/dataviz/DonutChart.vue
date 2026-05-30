<script setup lang="ts">
/**
 * 도넛(원형) 차트 — vue-echarts wrapper. 옵션은 chart-theme.buildDonutOption 으로만 조립.
 */
import { computed } from 'vue';
import VChart from 'vue-echarts';
import { buildDonutOption, useChartColors, type DonutItem } from '@/lib/chart-theme';

const props = withDefaults(
  defineProps<{
    items: DonutItem[];
    height?: number;
    showLegend?: boolean;
    /** 가운데 라벨(예: 합계 표시). */
    centerLabel?: string;
  }>(),
  { height: 240, showLegend: false, centerLabel: undefined },
);

const colors = useChartColors();

const option = computed(() =>
  buildDonutOption(props.items, colors.value, {
    showLegend: props.showLegend,
    centerLabel: props.centerLabel,
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
