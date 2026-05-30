<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { toast } from 'vue-sonner';
import { ListIcon } from '@lucide/vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import DynamicKanban from '@/components/dynamic/DynamicKanban.vue';
import { usePageData } from '@/composables/usePageData';
import { useApiFetch } from '@/lib/api';
import { UI } from '@/lib/ui-messages';
import { statusLabel } from '@/composables/useDashboard';
import type { ApiEnvelope } from '@/types/meta';
import type { TicketStatus, TicketSummary } from '@/types/ticket';

/**
 * ITSM 티켓 칸반 보드 페이지 (phase 16 step 4).
 * 티켓 전체를 한 번에 불러와 상태별 컬럼으로 표시하고, 드래그로 상태 전이를 트리거한다.
 * 상태 전이는 백엔드 PATCH /api/tickets/{id}/status 가 단독 판정한다(거부 시 토스트 + 카드 원위치).
 */
const router = useRouter();

// 보드는 페이지네이션 없이 전체 표시 — 큰 size 로 1페이지 조회.
const api = computed(() => '/api/tickets');
const { rows, isFetching, error, setQuery, reload } =
  usePageData<TicketSummary>(api);

onMounted(() => {
  setQuery({ page: 0, size: 500 });
});

/**
 * 드래그 이동 핸들러 — DynamicKanban 이 호출. 실패 시 throw 하여 카드를 원위치시킨다.
 * 전이 매트릭스는 백엔드가 판정하며, 거부(4xx)는 toast 로 안내한다.
 */
async function onMove(
  ticket: TicketSummary,
  _fromStatus: TicketStatus,
  toStatus: TicketStatus,
): Promise<void> {
  const { execute, error: patchError } = useApiFetch(
    `/api/tickets/${ticket.id}/status`,
    { immediate: false, refetch: false },
  )
    .patch({ next: toStatus })
    .json<ApiEnvelope<unknown>>();
  // immediate:false 이므로 명시적으로 실행해야 요청이 나간다 (execute 없이 await 하면 hang).
  await execute();
  if (patchError.value) {
    toast.error(`${statusLabel(toStatus)}(으)로 이동할 수 없습니다.`);
    throw new Error('status change rejected');
  }
  toast.success(`${ticket.ticketNo} → ${statusLabel(toStatus)}`);
  await reload();
}
</script>

<template>
  <section class="space-y-4">
    <PageHeader title="ITSM 티켓 보드">
      <template #actions>
        <Button
          variant="outline"
          @click="router.push('/itsm')"
        >
          <ListIcon class="mr-1 size-4" /> 목록 보기
        </Button>
      </template>
    </PageHeader>

    <p
      v-if="isFetching && rows.length === 0"
      class="text-foreground-muted"
    >
      {{ UI.loading.data }}
    </p>
    <Card
      v-else-if="error"
      class="border-danger"
    >
      <CardContent class="py-6 text-danger">
        {{ error }}
      </CardContent>
    </Card>

    <DynamicKanban
      v-else
      :tickets="rows"
      :on-move="onMove"
    />
  </section>
</template>
