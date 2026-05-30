<script setup lang="ts">
/**
 * 변화량 지표 — `↑ 12.3%` 화살표 + 시맨틱 색 + tabular-nums.
 * 색은 토큰 유틸(text-success/text-danger)만 사용한다(직접 hex 금지).
 */
import { computed } from 'vue';
import { trendInfo } from '@/lib/chart-theme';

const props = withDefaults(
  defineProps<{
    /** 변화량(보통 %). */
    value: number;
    /** 낮을수록 좋은 지표면 true (상승을 위험으로 표시). */
    invert?: boolean;
    /** 값 뒤에 붙는 단위. */
    suffix?: string;
    /** 소수 자리수. */
    digits?: number;
  }>(),
  { invert: false, suffix: '%', digits: 1 },
);

const info = computed(() => trendInfo(props.value, props.invert));

const toneClass = computed(() => {
  switch (info.value.tone) {
    case 'success':
      return 'text-success';
    case 'danger':
      return 'text-danger';
    default:
      return 'text-foreground-muted';
  }
});

const label = computed(() => `${info.value.magnitude.toFixed(props.digits)}${props.suffix}`);
</script>

<template>
  <span
    :class="[toneClass, 'inline-flex items-center gap-0.5 text-[13px] font-semibold tabular-nums']"
  >
    <span aria-hidden="true">{{ info.arrow }}</span>
    <span>{{ label }}</span>
  </span>
</template>
