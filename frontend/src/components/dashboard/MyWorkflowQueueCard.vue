<script setup lang="ts">
/**
 * 내 워크플로우 작업 큐 — 현재 사용자(역할/지정)에게 할당된 미완료 단계 리스트.
 * 단계명·SLA 마감·초과 뱃지 + 바로 처리 버튼(티켓 상세로 이동). 0건이면 풍부한 빈 상태.
 */
import { computed } from 'vue';
import { ListChecksIcon, ClockIcon, TriangleAlertIcon, ArrowRightIcon } from '@lucide/vue';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import type { MyWorkflowStep } from '@/types/dashboard';

const props = defineProps<{
  steps: MyWorkflowStep[];
}>();

/** SLA 마감 시각 — MM/DD HH:mm 표기(없으면 'SLA 없음'). 런타임 표기 전용. */
function formatDue(iso: string | null): string {
  if (!iso) return 'SLA 없음';
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return 'SLA 없음';
  return new Intl.DateTimeFormat('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(t));
}

const rows = computed(() =>
  props.steps.map((s) => ({ ...s, due: formatDue(s.slaDueAt) })),
);
</script>

<template>
  <Card class="flex h-full flex-col shadow-card">
    <CardHeader>
      <CardTitle class="text-[20px]">
        내 워크플로우 작업
      </CardTitle>
      <CardDescription>내 역할에 할당된 처리 대기 단계</CardDescription>
    </CardHeader>
    <CardContent class="flex-1">
      <ul
        v-if="rows.length"
        class="flex flex-col"
      >
        <li
          v-for="row in rows"
          :key="`${row.instanceId}-${row.stepIndex}`"
          class="flex items-center gap-3 border-b border-border-subtle py-3 last:border-0"
        >
          <span
            :class="[
              'inline-flex size-8 shrink-0 items-center justify-center rounded-full',
              row.overdue ? 'bg-danger/10 text-danger' : 'bg-common-soft text-common',
            ]"
            aria-hidden="true"
          >
            <TriangleAlertIcon
              v-if="row.overdue"
              class="size-4"
              :stroke-width="1.5"
            />
            <ListChecksIcon
              v-else
              class="size-4"
              :stroke-width="1.5"
            />
          </span>
          <div class="min-w-0 flex-1">
            <p class="flex items-center gap-2">
              <span class="truncate text-sm font-medium text-foreground">{{ row.stepName }}</span>
              <span
                v-if="row.overdue"
                class="shrink-0 rounded-full bg-danger/10 px-2 py-0.5 text-[12px] font-semibold text-danger"
              >SLA 초과</span>
            </p>
            <p class="flex items-center gap-1 text-[13px] text-foreground-muted">
              <ClockIcon
                class="size-3.5"
                :stroke-width="1.5"
                aria-hidden="true"
              />
              <span class="tabular-nums">{{ row.due }}</span>
              <span
                v-if="row.ticketId"
                class="text-foreground-subtle"
              >· 티켓 #{{ row.ticketId }}</span>
            </p>
          </div>
          <Button
            v-if="row.ticketId"
            as-child
            variant="ghost"
            size="sm"
          >
            <RouterLink :to="`/itsm/${row.ticketId}`">
              처리
              <ArrowRightIcon
                class="size-4"
                :stroke-width="1.5"
              />
            </RouterLink>
          </Button>
        </li>
      </ul>
      <div
        v-else
        class="flex flex-col items-center justify-center gap-3 py-12 text-center"
      >
        <span class="inline-flex size-12 items-center justify-center rounded-full bg-common-soft text-common">
          <ListChecksIcon
            class="size-6"
            :stroke-width="1.5"
          />
        </span>
        <p class="text-sm text-foreground-muted">
          처리 대기 중인 작업이 없습니다.
        </p>
        <p class="text-xs text-foreground-subtle">
          새 작업이 할당되면 여기에 표시됩니다.
        </p>
      </div>
    </CardContent>
  </Card>
</template>
