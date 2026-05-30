<script setup lang="ts">
/**
 * KPI 카드 — 큰 숫자(36px/700 tabular-nums) + 라벨 + 우측 스파크라인 + 하단 변화량.
 * 카드는 Hairline + 12px radius + shadow-card(UI_GUIDE §6 elevation). module prop 으로
 * 스파크라인 색과 좌측 4px 액센트 보더(모듈 식별, UI_GUIDE §3-2)를 자동 적용한다.
 */
import { computed } from 'vue';
import type { SystemType } from '@/types/meta';
import { moduleVisual } from '@/lib/module-color';
import Skeleton from '@/components/feedback/Skeleton.vue';
import Sparkline from './Sparkline.vue';
import TrendIndicator from './TrendIndicator.vue';

const props = withDefaults(
  defineProps<{
    label: string;
    /** 표시값 — 포맷은 호출부 책임(문자열 권장). 로딩 중에는 생략 가능. */
    value?: string | number;
    /** 보조 단위/접미 (예: '건', 'h'). */
    unit?: string;
    spark?: number[];
    /** 변화량(%) — 지정 시 하단 TrendIndicator 표시. */
    trend?: number;
    /** 낮을수록 좋은 지표면 true. */
    trendInvert?: boolean;
    module?: SystemType;
    /** 로딩 중 — 숫자·스파크라인 자리에 Skeleton 표시(UI_GUIDE §9). */
    loading?: boolean;
  }>(),
  {
    value: '',
    unit: undefined,
    spark: undefined,
    trend: undefined,
    trendInvert: false,
    module: undefined,
    loading: false,
  },
);

const visual = computed(() => (props.module ? moduleVisual(props.module) : null));
</script>

<template>
  <div
    :class="[
      'rounded-lg border border-border bg-surface p-5 shadow-card',
      visual ? `border-l-4 ${visual.borderClass}` : '',
    ]"
  >
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0">
        <p class="flex items-center gap-1.5 truncate text-[13px] font-medium text-foreground-muted">
          <span
            v-if="visual"
            :class="[visual.bgSoftClass, visual.borderClass, 'inline-block size-2 shrink-0 rounded-full border']"
            aria-hidden="true"
          />
          {{ label }}
        </p>
        <Skeleton
          v-if="loading"
          class="mt-2"
          width="58%"
          height="2.25rem"
        />
        <p
          v-else
          class="mt-1 flex items-baseline gap-1"
        >
          <span class="text-[36px] font-bold leading-none tabular-nums text-foreground">
            {{ value }}
          </span>
          <span
            v-if="unit"
            class="text-sm font-medium text-foreground-muted"
          >{{ unit }}</span>
        </p>
      </div>
      <Skeleton
        v-if="loading"
        class="mt-1 shrink-0"
        width="64px"
        height="32px"
      />
      <Sparkline
        v-else-if="spark && spark.length"
        :data="spark"
        :module="module"
        class="mt-1"
      />
    </div>
    <div
      v-if="!loading && trend !== undefined"
      class="mt-3 flex items-center gap-2"
    >
      <TrendIndicator
        :value="trend"
        :invert="trendInvert"
      />
      <span class="text-xs text-foreground-subtle">지난 기간 대비</span>
    </div>
  </div>
</template>
