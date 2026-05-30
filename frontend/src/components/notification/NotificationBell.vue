<script setup lang="ts">
/**
 * TopBar 알림 종 — 미읽음 뱃지 + 클릭 시 NotificationPanel 드롭다운.
 * useNotifications 로 30초 폴링하며, 항목 클릭 시 읽음 처리 후 relatedUrl 로 이동한다.
 * 실시간 push 없이 폴링 stub (PRD §9 의도적 비포함).
 */
import { ref, useTemplateRef } from 'vue';
import { useRouter } from 'vue-router';
import { onClickOutside } from '@vueuse/core';
import { BellIcon } from '@lucide/vue';
import NotificationPanel from '@/components/notification/NotificationPanel.vue';
import { useNotifications } from '@/composables/useNotifications';
import type { NotificationItem } from '@/types/notification';

const router = useRouter();
const { items, unreadCount, isFetching, error, markRead, markAllRead } = useNotifications({
  poll: true,
});

const open = ref(false);
const root = useTemplateRef<HTMLElement>('root');
onClickOutside(root, () => {
  open.value = false;
});

function toggle() {
  open.value = !open.value;
}

async function onOpenItem(item: NotificationItem) {
  open.value = false;
  if (!item.read) {
    await markRead(item.id);
  }
  if (item.relatedUrl) {
    void router.push(item.relatedUrl);
  }
}

async function onReadAll() {
  await markAllRead();
}

function onViewAll() {
  open.value = false;
  void router.push('/notifications');
}
</script>

<template>
  <div
    ref="root"
    class="relative"
  >
    <button
      type="button"
      class="relative inline-flex h-9 w-9 items-center justify-center rounded-md text-foreground hover:bg-surface-hover"
      :aria-label="unreadCount > 0 ? `알림 ${unreadCount}건 안 읽음` : '알림'"
      aria-haspopup="dialog"
      :aria-expanded="open"
      @click="toggle"
    >
      <BellIcon
        class="h-5 w-5"
        :stroke-width="1.5"
      />
      <span
        v-if="unreadCount > 0"
        class="absolute -right-0.5 -top-0.5 inline-flex min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold leading-4 text-white tabular-nums"
      >
        {{ unreadCount > 99 ? '99+' : unreadCount }}
      </span>
    </button>

    <div
      v-if="open"
      class="absolute right-0 z-50 mt-1"
    >
      <NotificationPanel
        :items="items"
        :unread-count="unreadCount"
        :loading="isFetching"
        :error="error"
        @open="onOpenItem"
        @read-all="onReadAll"
        @view-all="onViewAll"
      />
    </div>
  </div>
</template>
