<script setup lang="ts">
/**
 * 상태별 티켓 분포 — BarChart(step 2) wrapper. 카테고리 = 상태 한글 라벨.
 * 데이터 0건이면 풍부한 빈 상태를 노출한다.
 */
import { computed } from 'vue';
import { ListChecksIcon, PlusIcon } from '@lucide/vue';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import BarChart from '@/components/dataviz/BarChart.vue';
import { statusLabel, sumCounts } from '@/composables/useDashboard';
import type { CountByKey } from '@/types/dashboard';
import type { SeriesInput } from '@/lib/chart-theme';

const props = defineProps<{
  counts: CountByKey[];
}>();

const total = computed(() => sumCounts(props.counts));
const categories = computed<string[]>(() => props.counts.map((c) => statusLabel(c.key)));
const series = computed<SeriesInput[]>(() => [
  { name: '티켓', data: props.counts.map((c) => c.count) },
]);
</script>

<template>
  <Card class="h-full shadow-card">
    <CardHeader>
      <CardTitle class="text-[20px]">
        상태별 티켓
      </CardTitle>
      <CardDescription>처리 단계별 티켓 수</CardDescription>
    </CardHeader>
    <CardContent>
      <BarChart
        v-if="total > 0"
        :categories="categories"
        :series="series"
        :height="260"
      />
      <div
        v-else
        class="flex flex-col items-center justify-center gap-3 py-10 text-center"
      >
        <span class="inline-flex size-12 items-center justify-center rounded-full bg-itsm-soft text-itsm">
          <ListChecksIcon
            class="size-6"
            :stroke-width="1.5"
          />
        </span>
        <p class="text-sm text-foreground-muted">
          집계할 티켓이 없습니다.
        </p>
        <Button
          as-child
          variant="outline"
          size="sm"
        >
          <RouterLink to="/itsm/new/INCIDENT">
            <PlusIcon
              class="size-4"
              :stroke-width="1.5"
            />
            첫 티켓 등록하기
          </RouterLink>
        </Button>
      </div>
    </CardContent>
  </Card>
</template>
