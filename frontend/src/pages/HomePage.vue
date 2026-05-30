<script setup lang="ts">
/**
 * 운영 대시보드 홈 — 위젯 그리드(환영 헤더 · KPI · 차트 · 활동 피드 · 자산 분포 · 워크플로우 큐).
 * 데이터는 useDashboard(`GET /api/dashboard/summary`, 30초 폴링)로 현재 사용자 기준 집계를 받는다.
 * 티켓 위젯은 TICKET 권한 보유자에게만 노출(보안 경계는 백엔드 @PreAuthorize 가 책임).
 */
import { computed } from 'vue';
import { storeToRefs } from 'pinia';
import { TriangleAlertIcon } from '@lucide/vue';
import { useAuthStore } from '@/stores/useAuthStore';
import { useDashboard } from '@/composables/useDashboard';
import { UI } from '@/lib/ui-messages';
import { Button } from '@/components/ui/button';
import DashboardWelcome from '@/components/dashboard/DashboardWelcome.vue';
import KpiGrid from '@/components/dashboard/KpiGrid.vue';
import TicketByPriorityCard from '@/components/dashboard/TicketByPriorityCard.vue';
import TicketByStatusCard from '@/components/dashboard/TicketByStatusCard.vue';
import RecentActivityFeed from '@/components/dashboard/RecentActivityFeed.vue';
import AssetByCategoryCard from '@/components/dashboard/AssetByCategoryCard.vue';
import MyWorkflowQueueCard from '@/components/dashboard/MyWorkflowQueueCard.vue';

const { permissions } = storeToRefs(useAuthStore());
const canTickets = computed<boolean>(() =>
  permissions.value.some((p) => p.startsWith('TICKET')),
);

const { summary, isFetching, error, reload } = useDashboard({ poll: true });

const showInitialLoading = computed(() => !summary.value && isFetching.value);
</script>

<template>
  <section class="flex flex-col gap-6">
    <DashboardWelcome />

    <!-- 최초 로딩 — 스켈레톤 자리 표시 -->
    <div
      v-if="showInitialLoading"
      class="flex flex-col gap-6"
      aria-busy="true"
    >
      <p class="sr-only">
        {{ UI.loading.data }}
      </p>
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div
          v-for="i in 4"
          :key="i"
          class="h-28 animate-pulse rounded-lg border border-border bg-surface-muted"
        />
      </div>
      <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div class="h-72 animate-pulse rounded-xl border border-border bg-surface-muted lg:col-span-2" />
        <div class="h-72 animate-pulse rounded-xl border border-border bg-surface-muted" />
      </div>
    </div>

    <!-- 에러 -->
    <div
      v-else-if="error"
      class="flex flex-col items-center justify-center gap-3 rounded-xl border border-border bg-surface py-16 text-center shadow-card"
    >
      <span class="inline-flex size-12 items-center justify-center rounded-full bg-danger/10 text-danger">
        <TriangleAlertIcon
          class="size-6"
          :stroke-width="1.5"
        />
      </span>
      <p class="text-sm text-foreground-muted">
        {{ error }}
      </p>
      <Button
        variant="outline"
        size="sm"
        @click="reload"
      >
        다시 시도
      </Button>
    </div>

    <!-- 위젯 그리드 -->
    <template v-else-if="summary">
      <KpiGrid
        :summary="summary"
        :can-tickets="canTickets"
      />

      <!-- 행 1: (canTickets) 2/3 차트 + 1/3 활동 피드 / (그 외) 활동 피드 전폭 -->
      <div
        v-if="canTickets"
        class="grid grid-cols-1 gap-4 lg:grid-cols-3"
      >
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:col-span-2">
          <TicketByPriorityCard :counts="summary.ticketsByPriority" />
          <TicketByStatusCard :counts="summary.ticketsByStatus" />
        </div>
        <RecentActivityFeed :activities="summary.recentActivities" />
      </div>
      <RecentActivityFeed
        v-else
        :activities="summary.recentActivities"
      />

      <!-- 행 2: 자산 분류 도넛 + 내 워크플로우 작업 -->
      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <AssetByCategoryCard
          :categories="summary.assetsByCategory"
          :trend="summary.lifecycleTrend"
        />
        <MyWorkflowQueueCard :steps="summary.myWorkflowSteps" />
      </div>
    </template>
  </section>
</template>
