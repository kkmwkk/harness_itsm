<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { VueDraggable } from 'vue-draggable-plus';
import { MoreVerticalIcon, PencilIcon, XCircleIcon, ClockIcon } from '@lucide/vue';
import { storeToRefs } from 'pinia';
import { useAuthStore } from '@/stores/useAuthStore';
import PriorityBadge from '@/components/common/PriorityBadge.vue';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import {
  KANBAN_COLUMNS,
  groupByStatus,
  nextStatus,
  initials,
  slaRemaining,
} from '@/lib/kanban';
import type { TicketStatus, TicketSummary } from '@/types/ticket';

/**
 * ITSM 티켓 칸반 보드 (phase 16 step 4 · UI_GUIDE §5-3).
 * 상태 컬럼 4개 × 드래그 이동으로 상태 전이를 트리거한다.
 *
 * - 드래그 이동 시 onMove 호출 → 부모가 PATCH /api/tickets/{id}/status + reload·toast 담당.
 *   onMove 가 reject(throw) 하면 카드를 원위치로 되돌린다(실패 = 카드 원위치).
 * - 전이 매트릭스는 프론트에서 강제하지 않는다 — 백엔드 changeStatus 가 거부하면 onMove 가 throw.
 * - CLOSED 컬럼으로 이동 시 실수 방지 확인 다이얼로그를 띄운다.
 */
const props = defineProps<{
  tickets: TicketSummary[];
  onMove: (
    ticket: TicketSummary,
    fromStatus: TicketStatus,
    toStatus: TicketStatus,
  ) => Promise<void>;
}>();

const router = useRouter();
const auth = useAuthStore();
const { permissions } = storeToRefs(auth);
// 편집 권한(TICKET_CREATE 보유자) — 없으면 '상세 보기'만 노출.
const canEdit = () => permissions.value.includes('TICKET_CREATE');

// 컬럼별 로컬 상태 — 드래그가 이 배열을 직접 변형한다.
// props.tickets 가 갱신되면(성공 후 reload) 다시 그룹핑한다.
const columns = ref<Record<TicketStatus, TicketSummary[]>>(
  groupByStatus(props.tickets),
);
watch(
  () => props.tickets,
  (next) => {
    columns.value = groupByStatus(next);
  },
);

/** 드래그/실패 시 props 기준으로 컬럼을 다시 그려 원위치시킨다. */
function rebuild(): void {
  columns.value = groupByStatus(props.tickets);
}

// CLOSED 이동 확인 다이얼로그용 대기 이동.
interface PendingMove {
  ticket: TicketSummary;
  fromStatus: TicketStatus;
  toStatus: TicketStatus;
}
const pending = ref<PendingMove | null>(null);

async function applyMove(
  ticket: TicketSummary,
  fromStatus: TicketStatus,
  toStatus: TicketStatus,
): Promise<void> {
  try {
    await props.onMove(ticket, fromStatus, toStatus);
    // 성공 시 부모가 reload → props.tickets 변경 → watch 가 재그룹핑.
  } catch {
    rebuild(); // 실패: 카드 원위치
  }
}

/**
 * vue-draggable-plus 의 destination @add — 카드가 다른 컬럼으로 들어온 직후 호출.
 * 이미 v-model 로 columns[toStatus] 에 이동돼 있으므로 newIndex 로 티켓을 찾고,
 * ticket.status(아직 옛 상태) 를 fromStatus 로 사용한다.
 */
function onColumnAdd(toStatus: TicketStatus, evt: { newIndex?: number | null }): void {
  const idx = evt?.newIndex ?? -1;
  const moved = idx >= 0 ? columns.value[toStatus][idx] : undefined;
  if (!moved) return;
  const fromStatus = moved.status;
  if (fromStatus === toStatus) return;
  requestMove(moved, fromStatus, toStatus);
}

/** 이동 요청 — CLOSED 로의 이동은 확인 다이얼로그, 그 외는 즉시 적용. */
function requestMove(
  ticket: TicketSummary,
  fromStatus: TicketStatus,
  toStatus: TicketStatus,
): void {
  if (toStatus === 'CLOSED' && fromStatus !== 'CLOSED') {
    pending.value = { ticket, fromStatus, toStatus };
    return;
  }
  void applyMove(ticket, fromStatus, toStatus);
}

function confirmPending(): void {
  const p = pending.value;
  pending.value = null;
  if (p) void applyMove(p.ticket, p.fromStatus, p.toStatus);
}

function cancelPending(): void {
  pending.value = null;
  rebuild(); // 다이얼로그 취소 시 드래그된 카드 원위치
}

// ── 카드 액션 ───────────────────────────────────────────────
const openMenuId = ref<number | null>(null);
function toggleMenu(id: number): void {
  openMenuId.value = openMenuId.value === id ? null : id;
}
function closeMenu(): void {
  openMenuId.value = null;
}
function goDetail(ticket: TicketSummary): void {
  closeMenu();
  void router.push(`/itsm/${ticket.id}`);
}
function requestClose(ticket: TicketSummary): void {
  closeMenu();
  requestMove(ticket, ticket.status, 'CLOSED');
}

// 키보드: Enter → 상세, Alt+→ 다음 상태로 이동.
function onCardKeydown(ticket: TicketSummary, e: KeyboardEvent): void {
  if (e.key === 'Enter') {
    e.preventDefault();
    goDetail(ticket);
    return;
  }
  if (e.altKey && e.key === 'ArrowRight') {
    e.preventDefault();
    const to = nextStatus(ticket.status);
    if (to) requestMove(ticket, ticket.status, to);
  }
}

function slaFor(ticket: TicketSummary) {
  return slaRemaining(ticket.slaDueAt, Date.now());
}
</script>

<template>
  <div class="flex gap-4 overflow-x-auto pb-2">
    <section
      v-for="col in KANBAN_COLUMNS"
      :key="col.status"
      class="flex min-w-[260px] flex-1 flex-col rounded-lg border border-border bg-surface-muted"
    >
      <!-- 컬럼 헤더: 라벨 + 개수 뱃지 -->
      <header
        class="flex items-center justify-between border-b border-border-subtle px-3 py-2"
      >
        <span class="text-[13px] font-semibold text-foreground">{{ col.label }}</span>
        <span
          class="inline-flex min-w-6 items-center justify-center rounded-full bg-surface px-2 py-0.5 text-[12px] font-semibold text-foreground-muted"
        >
          {{ columns[col.status].length }}
        </span>
      </header>

      <!-- 카드 리스트 (드래그 가능) -->
      <VueDraggable
        v-model="columns[col.status]"
        tag="div"
        :group="{ name: 'itsm-kanban' }"
        :animation="200"
        filter=".kanban-no-drag"
        :prevent-on-filter="false"
        ghost-class="kanban-card-ghost"
        chosen-class="kanban-card-chosen"
        class="flex min-h-24 flex-1 flex-col gap-2 p-2"
        @add="(e) => onColumnAdd(col.status, e)"
      >
        <article
          v-for="t in columns[col.status]"
          :key="t.id"
          tabindex="0"
          class="group relative cursor-grab rounded-md border border-border bg-surface p-3 shadow-card transition-shadow hover:shadow-hover focus-visible:outline-2 focus-visible:outline-ring active:cursor-grabbing"
          @keydown="onCardKeydown(t, $event)"
        >
          <!-- 우상단 dropdown (hover/focus 시 fade-in) -->
          <div class="kanban-no-drag absolute right-1.5 top-1.5">
            <button
              type="button"
              class="inline-flex h-7 w-7 items-center justify-center rounded-md text-foreground-subtle opacity-0 transition-opacity hover:bg-surface-hover hover:text-foreground focus-visible:opacity-100 group-hover:opacity-100"
              :class="{ 'opacity-100': openMenuId === t.id }"
              aria-label="티켓 작업 메뉴"
              @click.stop="toggleMenu(t.id)"
            >
              <MoreVerticalIcon class="size-4" />
            </button>
            <div
              v-if="openMenuId === t.id"
              class="absolute right-0 z-20 mt-1 w-32 rounded-md border border-border bg-surface py-1 shadow-overlay"
            >
              <button
                type="button"
                class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-[13px] text-foreground hover:bg-surface-hover"
                @click.stop="goDetail(t)"
              >
                <PencilIcon class="size-3.5" />
                {{ canEdit() ? '편집' : '상세 보기' }}
              </button>
              <button
                v-if="canEdit() && t.status !== 'CLOSED'"
                type="button"
                class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-[13px] text-danger hover:bg-surface-hover"
                @click.stop="requestClose(t)"
              >
                <XCircleIcon class="size-3.5" />
                종료(닫기)
              </button>
            </div>
          </div>

          <!-- 카드 본문 -->
          <p class="font-mono text-[12px] text-foreground-muted">
            {{ t.ticketNo }}
          </p>
          <p class="mt-0.5 pr-6 text-[14px] font-semibold leading-snug text-foreground">
            {{ t.title }}
          </p>

          <div class="mt-2 flex items-center justify-between gap-2">
            <PriorityBadge :value="t.priority" />
            <!-- assignee 아바타 (이니셜) -->
            <span
              :title="t.assigneeId ?? '미지정'"
              class="inline-flex size-6 items-center justify-center rounded-full bg-itsm-soft text-[11px] font-semibold text-itsm"
            >
              {{ initials(t.assigneeId) }}
            </span>
          </div>

          <!-- SLA 잔여시간 (slaDueAt 제공 시) -->
          <p
            v-if="slaFor(t)"
            class="mt-2 inline-flex items-center gap-1 text-[12px]"
            :class="slaFor(t)!.overdue ? 'text-danger' : 'text-foreground-muted'"
          >
            <ClockIcon class="size-3.5" />
            {{ slaFor(t)!.label }}
          </p>
        </article>

        <!-- 빈 컬럼 상태 -->
        <p
          v-if="columns[col.status].length === 0"
          class="px-2 py-6 text-center text-[12px] text-foreground-subtle"
        >
          이 상태에 항목이 없습니다.
        </p>
      </VueDraggable>
    </section>

    <!-- 메뉴 외부 클릭 닫기용 백드롭 -->
    <div
      v-if="openMenuId !== null"
      class="fixed inset-0 z-10"
      @click="closeMenu"
    />

    <!-- CLOSED 이동 확인 -->
    <Dialog
      :open="pending !== null"
      @update:open="(v: boolean) => { if (!v) cancelPending(); }"
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>티켓 종료</DialogTitle>
          <DialogDescription>
            <template v-if="pending">
              <span class="font-mono">{{ pending.ticket.ticketNo }}</span>
              티켓을 종료(CLOSED) 상태로 이동합니다. 종료 후에는 되돌릴 수 없습니다. 계속하시겠습니까?
            </template>
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            variant="outline"
            @click="cancelPending"
          >
            취소
          </Button>
          <Button
            variant="destructive"
            @click="confirmPending"
          >
            종료
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<style scoped>
/* 드래그 중 원본 카드 — 반투명 (UI_GUIDE §9). */
.kanban-card-chosen {
  opacity: 0.5;
}
/* 드롭 위치 placeholder — primary 파선 (UI_GUIDE §9). */
.kanban-card-ghost {
  border: 1px dashed var(--color-primary);
  opacity: 0.6;
}
</style>
