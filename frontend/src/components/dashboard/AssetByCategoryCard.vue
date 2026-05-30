<script setup lang="ts">
/**
 * 자산 분류별 분포 — DonutChart(분류별 자산 수) + 라이프사이클 이벤트 주간 추이 Sparkline.
 * 데이터 0건이면 풍부한 빈 상태를 노출한다.
 */
import { computed } from 'vue';
import { BoxesIcon } from '@lucide/vue';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import DonutChart from '@/components/dataviz/DonutChart.vue';
import Sparkline from '@/components/dataviz/Sparkline.vue';
import { sumCounts, toDonutItems } from '@/composables/useDashboard';
import type { CountByKey } from '@/types/dashboard';

const props = defineProps<{
  categories: CountByKey[];
  /** 라이프사이클 이벤트 주간 추이(마지막 = 이번 주). */
  trend: number[];
}>();

const total = computed(() => sumCounts(props.categories));
const items = computed(() => toDonutItems(props.categories));
const hasTrend = computed(() => props.trend.some((v) => v > 0));
</script>

<template>
  <Card class="h-full shadow-card">
    <CardHeader>
      <CardTitle class="text-[20px]">
        자산 분류
      </CardTitle>
      <CardDescription>분류별 자산 수와 이력 이벤트 추이</CardDescription>
    </CardHeader>
    <CardContent>
      <template v-if="total > 0">
        <DonutChart
          :items="items"
          :center-label="`${total}대`"
          show-legend
          :height="240"
        />
        <div
          v-if="hasTrend"
          class="mt-4 flex items-center justify-between gap-3 border-t border-border-subtle pt-4"
        >
          <span class="text-[13px] text-foreground-muted">라이프사이클 이벤트 추이 (최근 8주)</span>
          <Sparkline
            :data="trend"
            module="ITAM"
            :width="120"
            :height="32"
          />
        </div>
      </template>
      <div
        v-else
        class="flex flex-col items-center justify-center gap-3 py-10 text-center"
      >
        <span class="inline-flex size-12 items-center justify-center rounded-full bg-itam-soft text-itam">
          <BoxesIcon
            class="size-6"
            :stroke-width="1.5"
          />
        </span>
        <p class="text-sm text-foreground-muted">
          등록된 자산이 없습니다.
        </p>
        <Button
          as-child
          variant="outline"
          size="sm"
        >
          <RouterLink to="/itam">
            자산 등록하러 가기
          </RouterLink>
        </Button>
      </div>
    </CardContent>
  </Card>
</template>
