<script setup lang="ts">
/**
 * 알림 1건 행 — 아이콘 + 제목 + 본문(plain text) + 상대 시각 + 미읽음 점.
 * 본문은 항상 텍스트로만 렌더(v-html 금지 — raw HTML 노출 방지).
 * 클릭 시 'open' 이벤트를 emit 하고, 읽음 처리·이동은 부모가 담당한다.
 */
import { computed } from 'vue';
import { GitBranchIcon, TicketIcon } from '@lucide/vue';
import { relativeTime } from '@/composables/useDashboard';
import type { NotificationItem } from '@/types/notification';

const props = defineProps<{ item: NotificationItem }>();
const emit = defineEmits<{ open: [item: NotificationItem] }>();

const time = computed(() => relativeTime(props.item.createdAt, Date.now()));
const isWorkflow = computed(() => props.item.type === 'WORKFLOW_STEP_ASSIGNED');
</script>

<template>
  <button
    type="button"
    class="flex w-full items-start gap-3 px-3 py-2.5 text-left transition-colors hover:bg-surface-hover focus-visible:bg-surface-hover focus-visible:outline-none"
    :class="{ 'bg-itsm-soft/40': !item.read }"
    @click="emit('open', item)"
  >
    <span
      class="mt-0.5 inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-itsm-soft text-itsm"
      aria-hidden="true"
    >
      <GitBranchIcon
        v-if="isWorkflow"
        class="size-4"
        :stroke-width="1.5"
      />
      <TicketIcon
        v-else
        class="size-4"
        :stroke-width="1.5"
      />
    </span>
    <div class="min-w-0 flex-1">
      <div class="flex items-center gap-2">
        <p
          class="truncate text-sm text-foreground"
          :class="item.read ? 'font-medium' : 'font-semibold'"
        >
          {{ item.title }}
        </p>
        <span
          v-if="!item.read"
          class="size-2 shrink-0 rounded-full bg-primary"
          aria-label="안 읽음"
        />
      </div>
      <p
        v-if="item.body"
        class="mt-0.5 line-clamp-2 text-[13px] text-foreground-muted"
      >
        {{ item.body }}
      </p>
      <p class="mt-1 text-xs text-foreground-subtle tabular-nums">
        {{ time }}
      </p>
    </div>
  </button>
</template>
