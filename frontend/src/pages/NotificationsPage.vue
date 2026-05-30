<script setup lang="ts">
/**
 * 전체 알림 목록 페이지 (/notifications) — 페이지네이션 + 모두 읽음 + 항목 클릭 이동.
 * NotificationItem 컴포넌트를 재사용한다. 폴링은 사용하지 않고 수동 새로고침/페이지 이동만.
 */
import { ref, computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { CheckCheckIcon, BellIcon, ChevronLeftIcon, ChevronRightIcon } from '@lucide/vue';
import { useApiFetch } from '@/lib/api';
import { UI, mapErrorCode } from '@/lib/ui-messages';
import NotificationItem from '@/components/notification/NotificationItem.vue';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import type { ApiEnvelope } from '@/types/meta';
import type { PageResponse } from '@/types/page';
import type { NotificationItem as NotificationItemType } from '@/types/notification';

const router = useRouter();
const PAGE_SIZE = 20;
const page = ref(0);

const listUrl = computed(
  () => `/api/notifications?unreadOnly=false&page=${page.value}&size=${PAGE_SIZE}`,
);
const {
  data,
  isFetching,
  error: fetchError,
  execute: reloadList,
} = useApiFetch(listUrl, { refetch: true }).json<ApiEnvelope<PageResponse<NotificationItemType>>>();

const items = computed<NotificationItemType[]>(() => data.value?.data?.content ?? []);
const totalPages = computed<number>(() => data.value?.data?.totalPages ?? 0);
const totalElements = computed<number>(() => data.value?.data?.totalElements ?? 0);
const error = computed<string | null>(() => {
  if (!fetchError.value) return null;
  const code = data.value?.errorCode;
  return code ? mapErrorCode(code) : UI.error.dataLoad;
});

watch(page, () => {
  void reloadList();
});

async function onOpenItem(item: NotificationItemType) {
  if (!item.read) {
    const { execute } = useApiFetch(`/api/notifications/${item.id}/read`).patch().json();
    await execute();
    await reloadList();
  }
  if (item.relatedUrl) {
    void router.push(item.relatedUrl);
  }
}

async function onReadAll() {
  const { execute } = useApiFetch('/api/notifications/read-all').post().json();
  await execute();
  await reloadList();
}

function prev() {
  if (page.value > 0) page.value -= 1;
}
function next() {
  if (page.value < totalPages.value - 1) page.value += 1;
}
</script>

<template>
  <div class="mx-auto max-w-3xl">
    <Card class="shadow-card">
      <CardHeader class="flex flex-row items-center justify-between">
        <CardTitle class="text-[20px]">
          알림<span
            v-if="totalElements > 0"
            class="ml-2 text-sm font-normal text-foreground-muted tabular-nums"
          >총 {{ totalElements }}건</span>
        </CardTitle>
        <Button
          variant="ghost"
          size="sm"
          @click="onReadAll"
        >
          <CheckCheckIcon
            class="size-4"
            :stroke-width="1.5"
          />
          모두 읽음
        </Button>
      </CardHeader>
      <CardContent>
        <p
          v-if="isFetching && items.length === 0"
          class="py-12 text-center text-sm text-foreground-muted"
        >
          {{ UI.loading.data }}
        </p>
        <p
          v-else-if="error"
          class="py-12 text-center text-sm text-danger"
        >
          {{ error }}
        </p>
        <ul
          v-else-if="items.length"
          class="divide-y divide-border-subtle rounded-md border border-border"
        >
          <li
            v-for="n in items"
            :key="n.id"
          >
            <NotificationItem
              :item="n"
              @open="onOpenItem"
            />
          </li>
        </ul>
        <div
          v-else
          class="flex flex-col items-center justify-center gap-3 py-16 text-center"
        >
          <span class="inline-flex size-12 items-center justify-center rounded-full bg-surface-muted text-foreground-subtle">
            <BellIcon
              class="size-6"
              :stroke-width="1.5"
            />
          </span>
          <p class="text-sm text-foreground-muted">
            알림이 없습니다.
          </p>
          <p class="text-xs text-foreground-subtle">
            워크플로우 단계 배정·티켓 상태 변경 시 여기에 표시됩니다.
          </p>
        </div>

        <div
          v-if="totalPages > 1"
          class="mt-4 flex items-center justify-center gap-3"
        >
          <Button
            variant="outline"
            size="sm"
            :disabled="page === 0"
            @click="prev"
          >
            <ChevronLeftIcon
              class="size-4"
              :stroke-width="1.5"
            />
            이전
          </Button>
          <span class="text-sm text-foreground-muted tabular-nums">
            {{ page + 1 }} / {{ totalPages }}
          </span>
          <Button
            variant="outline"
            size="sm"
            :disabled="page >= totalPages - 1"
            @click="next"
          >
            다음
            <ChevronRightIcon
              class="size-4"
              :stroke-width="1.5"
            />
          </Button>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
