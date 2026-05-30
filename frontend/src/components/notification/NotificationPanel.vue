<script setup lang="ts">
/**
 * 알림 드롭다운 패널 — 미읽음/최근 목록 + "모두 읽음" + "전체 보기" 링크.
 * 데이터·읽음 처리는 부모(NotificationBell)가 useNotifications 로 관리하고, 본 컴포넌트는 표시만 한다.
 */
import { CheckCheckIcon, BellIcon } from '@lucide/vue';
import NotificationItem from '@/components/notification/NotificationItem.vue';
import { UI } from '@/lib/ui-messages';
import type { NotificationItem as NotificationItemType } from '@/types/notification';

defineProps<{
  items: NotificationItemType[];
  unreadCount: number;
  loading: boolean;
  error: string | null;
}>();

const emit = defineEmits<{
  open: [item: NotificationItemType];
  readAll: [];
  viewAll: [];
}>();
</script>

<template>
  <div
    class="w-80 overflow-hidden rounded-md border border-border bg-surface shadow-overlay sm:w-96"
    role="dialog"
    aria-label="알림"
  >
    <header class="flex items-center justify-between border-b border-border px-3 py-2.5">
      <h2 class="text-sm font-semibold text-foreground">
        알림<span
          v-if="unreadCount > 0"
          class="ml-1.5 text-itsm"
        >{{ unreadCount }}</span>
      </h2>
      <button
        type="button"
        class="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs text-foreground-muted transition-colors hover:bg-surface-hover hover:text-foreground disabled:opacity-40"
        :disabled="unreadCount === 0"
        @click="emit('readAll')"
      >
        <CheckCheckIcon
          class="size-3.5"
          :stroke-width="1.5"
        />
        모두 읽음
      </button>
    </header>

    <div class="max-h-96 overflow-y-auto">
      <p
        v-if="loading && items.length === 0"
        class="px-3 py-8 text-center text-sm text-foreground-muted"
      >
        {{ UI.loading.data }}
      </p>
      <p
        v-else-if="error"
        class="px-3 py-8 text-center text-sm text-danger"
      >
        {{ error }}
      </p>
      <ul
        v-else-if="items.length"
        class="divide-y divide-border-subtle"
      >
        <li
          v-for="n in items"
          :key="n.id"
        >
          <NotificationItem
            :item="n"
            @open="emit('open', $event)"
          />
        </li>
      </ul>
      <div
        v-else
        class="flex flex-col items-center justify-center gap-2 px-3 py-10 text-center"
      >
        <span class="inline-flex size-10 items-center justify-center rounded-full bg-surface-muted text-foreground-subtle">
          <BellIcon
            class="size-5"
            :stroke-width="1.5"
          />
        </span>
        <p class="text-sm text-foreground-muted">
          새 알림이 없습니다.
        </p>
      </div>
    </div>

    <footer class="border-t border-border px-3 py-2">
      <button
        type="button"
        class="w-full rounded-md py-1.5 text-center text-xs font-medium text-itsm transition-colors hover:bg-itsm-soft"
        @click="emit('viewAll')"
      >
        전체 보기
      </button>
    </footer>
  </div>
</template>
