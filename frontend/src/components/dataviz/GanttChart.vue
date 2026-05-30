<script setup lang="ts">
/**
 * 간트 차트 — vue-echarts wrapper (phase 16 step 5).
 * 옵션은 lib/gantt.buildGanttOption 으로만 조립(ECharts custom series). 색·다크 전환은 토큰이 처리한다.
 * 무거운 간트 전용 라이브러리 대신 ECharts custom series 사용(금지사항).
 */
import { computed } from 'vue';
import VChart from 'vue-echarts';
import { useChartColors } from '@/lib/chart-theme';
import { buildGanttOption, type GanttBar } from '@/lib/gantt';

const props = withDefaults(
  defineProps<{
    bars: GanttBar[];
    /** 미지정 시 행 수 × rowHeight 로 자동 산정. */
    height?: number;
    rowHeight?: number;
    labelWidth?: number;
  }>(),
  { height: undefined, rowHeight: 36, labelWidth: 180 },
);

const colors = useChartColors();

const computedHeight = computed<number>(
  () => props.height ?? Math.max(120, props.bars.length * props.rowHeight + 72),
);

const option = computed(() =>
  buildGanttOption(props.bars, colors.value, {
    rowHeight: props.rowHeight,
    labelWidth: props.labelWidth,
  }),
);
</script>

<template>
  <VChart
    :option="option"
    :style="{ height: `${computedHeight}px`, width: '100%' }"
    autoresize
  />
</template>
