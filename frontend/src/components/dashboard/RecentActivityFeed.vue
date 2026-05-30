<script setup lang="ts">
/**
 * 최근 활동 피드 — 티켓·자산 이벤트를 시간 내림차순으로 나열. 아이콘 + 제목 + 상세 + 상대 시각.
 * 상대 시각은 useDashboard.relativeTime 순수 함수로 계산(activities 갱신 시 재평가).
 * 데이터 0건이면 풍부한 빈 상태를 노출한다.
 */
import { computed } from 'vue';
import { ActivityIcon, TicketIcon, BoxesIcon } from '@lucide/vue';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { relativeTime } from '@/composables/useDashboard';
import type { RecentActivity } from '@/types/dashboard';

const props = defineProps<{
  activities: RecentActivity[];
}>();

interface Row extends RecentActivity {
  time: string;
}

const rows = computed<Row[]>(() => {
  const now = Date.now();
  return props.activities.map((a) => ({ ...a, time: relativeTime(a.at, now) }));
});
</script>

<template>
  <Card class="flex h-full flex-col shadow-card">
    <CardHeader>
      <CardTitle class="text-[20px]">
        최근 활동
      </CardTitle>
      <CardDescription>티켓·자산의 최근 변경 내역</CardDescription>
    </CardHeader>
    <CardContent class="flex-1">
      <ul
        v-if="rows.length"
        class="flex flex-col"
      >
        <li
          v-for="(row, i) in rows"
          :key="`${row.type}-${i}`"
          class="flex items-start gap-3 border-b border-border-subtle py-3 last:border-0"
        >
          <span
            :class="[
              'mt-0.5 inline-flex size-8 shrink-0 items-center justify-center rounded-full',
              row.type === 'TICKET' ? 'bg-itsm-soft text-itsm' : 'bg-itam-soft text-itam',
            ]"
            aria-hidden="true"
          >
            <TicketIcon
              v-if="row.type === 'TICKET'"
              class="size-4"
              :stroke-width="1.5"
            />
            <BoxesIcon
              v-else
              class="size-4"
              :stroke-width="1.5"
            />
          </span>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-foreground">
              {{ row.title }}
            </p>
            <p class="truncate text-[13px] text-foreground-muted">
              {{ row.detail }}
            </p>
          </div>
          <span class="shrink-0 text-xs text-foreground-subtle tabular-nums">{{ row.time }}</span>
        </li>
      </ul>
      <div
        v-else
        class="flex flex-col items-center justify-center gap-3 py-12 text-center"
      >
        <span class="inline-flex size-12 items-center justify-center rounded-full bg-surface-muted text-foreground-subtle">
          <ActivityIcon
            class="size-6"
            :stroke-width="1.5"
          />
        </span>
        <p class="text-sm text-foreground-muted">
          최근 활동이 없습니다.
        </p>
        <p class="text-xs text-foreground-subtle">
          티켓이나 자산을 등록하면 여기에 표시됩니다.
        </p>
      </div>
    </CardContent>
  </Card>
</template>
