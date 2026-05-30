<script setup lang="ts">
/**
 * KPI 카드 그리드 — KpiCard(step 2) 강제 사용. 모듈 좌측 보더 4px(티켓=ITSM·자산=ITAM·작업=COMMON).
 * 티켓 관련 KPI 는 canTickets(권한) 가 false 면 숨긴다. ROLE_ADMIN 은 adminStats 가 있을 때
 * 사용자/메뉴/메타 그룹 수 추가 KPI(SYSTEM 색)를 함께 노출한다.
 */
import { computed } from 'vue';
import KpiCard from '@/components/dataviz/KpiCard.vue';
import type { CountByKey, DashboardSummary } from '@/types/dashboard';

const props = defineProps<{
  summary: DashboardSummary;
  /** 티켓 권한 보유 여부 — false 면 티켓 KPI 숨김. */
  canTickets: boolean;
}>();

/** 값이 하나라도 0보다 크면 spark 로 사용, 전부 0이면 undefined(스파크라인 생략). */
function spark(values: number[]): number[] | undefined {
  return values.some((v) => v > 0) ? values : undefined;
}

const ticketStatusSpark = computed<number[] | undefined>(() =>
  spark(props.summary.ticketsByStatus.map((c: CountByKey) => c.count)),
);

const lifecycleSpark = computed<number[] | undefined>(() => spark(props.summary.lifecycleTrend));
</script>

<template>
  <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
    <template v-if="canTickets">
      <KpiCard
        label="열린 티켓"
        :value="summary.openTickets"
        unit="건"
        module="ITSM"
        :spark="ticketStatusSpark"
      />
      <KpiCard
        label="SLA 임박·초과"
        :value="summary.slaBreachCount"
        unit="건"
      />
      <KpiCard
        label="내 작업"
        :value="summary.myOpenTickets"
        unit="건"
        module="COMMON"
      />
    </template>
    <KpiCard
      label="전체 자산"
      :value="summary.totalAssets"
      unit="대"
      module="ITAM"
      :spark="lifecycleSpark"
    />

    <template v-if="summary.adminStats">
      <KpiCard
        label="사용자"
        :value="summary.adminStats.userCount"
        unit="명"
        module="SYSTEM"
      />
      <KpiCard
        label="메뉴"
        :value="summary.adminStats.menuCount"
        unit="개"
        module="SYSTEM"
      />
      <KpiCard
        label="메타 그룹"
        :value="summary.adminStats.metaGroupCount"
        unit="개"
        module="SYSTEM"
      />
    </template>
  </div>
</template>
