<script setup lang="ts">
/**
 * 우선순위별 티켓 분포 — DonutChart(step 2) wrapper. 가운데 합계 라벨.
 * 데이터 0건이면 풍부한 빈 상태(아이콘 + 안내 + 등록 유도)를 노출한다.
 */
import { computed } from 'vue';
import { TicketIcon, PlusIcon } from '@lucide/vue';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import DonutChart from '@/components/dataviz/DonutChart.vue';
import { priorityLabel, sumCounts, toDonutItems } from '@/composables/useDashboard';
import type { CountByKey } from '@/types/dashboard';

const props = defineProps<{
  counts: CountByKey[];
}>();

const total = computed(() => sumCounts(props.counts));
const items = computed(() => toDonutItems(props.counts, priorityLabel));
</script>

<template>
  <Card class="h-full shadow-card">
    <CardHeader>
      <CardTitle class="text-[20px]">
        우선순위별 티켓
      </CardTitle>
      <CardDescription>열린 티켓의 우선순위 분포</CardDescription>
    </CardHeader>
    <CardContent>
      <DonutChart
        v-if="total > 0"
        :items="items"
        :center-label="`${total}건`"
        show-legend
        :height="260"
      />
      <div
        v-else
        class="flex flex-col items-center justify-center gap-3 py-10 text-center"
      >
        <span class="inline-flex size-12 items-center justify-center rounded-full bg-itsm-soft text-itsm">
          <TicketIcon
            class="size-6"
            :stroke-width="1.5"
          />
        </span>
        <p class="text-sm text-foreground-muted">
          아직 열린 티켓이 없습니다.
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
