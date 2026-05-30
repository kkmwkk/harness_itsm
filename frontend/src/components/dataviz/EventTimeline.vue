<script lang="ts">
/** 세로 타임라인 항목 한 개. 자산 라이프사이클·워크플로우 단계 이력 등 시계열 이벤트 표현용. */
export type TimelineTone = 'success' | 'danger' | 'warning' | 'info' | 'neutral';

export interface TimelineItem {
  id: string | number;
  /** 마커 옆 분류 칩 텍스트(예: '취득', '1차 검토'). */
  badge?: string;
  /** 시맨틱 톤 — 마커·칩 색. 기본 'info'. */
  tone?: TimelineTone;
  /** 이미 포맷된 시각 문자열. */
  time?: string;
  /** 주요 텍스트(제목). */
  label: string;
  /** 부가 설명. */
  description?: string;
  /** 처리자/행위자 표시 텍스트. */
  user?: string;
}
</script>

<script setup lang="ts">
/**
 * 세로 이벤트 타임라인 (phase 16 step 5).
 * 마커 점 + 연결선 + 칩/시각/라벨/설명/사용자. 색은 시맨틱 토큰만 사용(UI_GUIDE §3-6).
 * 자산 상세의 라이프사이클·WorkflowPanel 단계 이력 시각화에 공용으로 쓴다.
 */
withDefaults(
  defineProps<{
    items: TimelineItem[];
    emptyText?: string;
  }>(),
  { emptyText: '아직 기록된 이벤트가 없습니다.' },
);

const TONE_DOT: Record<TimelineTone, string> = {
  success: 'bg-success',
  danger: 'bg-danger',
  warning: 'bg-warning',
  info: 'bg-info',
  neutral: 'bg-neutral',
};

const TONE_CHIP: Record<TimelineTone, string> = {
  success: 'bg-success/10 text-success',
  danger: 'bg-danger/10 text-danger',
  warning: 'bg-warning/10 text-warning',
  info: 'bg-info/10 text-info',
  neutral: 'bg-neutral/10 text-neutral',
};

function dotClass(tone: TimelineTone | undefined): string {
  return TONE_DOT[tone ?? 'info'];
}
function chipClass(tone: TimelineTone | undefined): string {
  return TONE_CHIP[tone ?? 'info'];
}
</script>

<template>
  <p
    v-if="items.length === 0"
    class="text-sm text-foreground-muted"
  >
    {{ emptyText }}
  </p>
  <ol
    v-else
    class="relative"
  >
    <li
      v-for="(item, idx) in items"
      :key="item.id"
      class="relative flex gap-3 pb-5 last:pb-0"
    >
      <!-- 연결선 — 마지막 항목 제외 -->
      <span
        v-if="idx < items.length - 1"
        class="absolute left-[7px] top-4 bottom-0 w-px bg-border-subtle"
        aria-hidden="true"
      />
      <!-- 마커 점 -->
      <span
        class="relative z-10 mt-1 size-3.5 shrink-0 rounded-full ring-2 ring-surface"
        :class="dotClass(item.tone)"
        aria-hidden="true"
      />
      <div class="min-w-0 flex-1 text-[13px]">
        <div class="flex flex-wrap items-center gap-2">
          <span
            v-if="item.badge"
            class="inline-flex items-center rounded-pill px-2.5 py-0.5 text-[12px] font-semibold"
            :class="chipClass(item.tone)"
          >
            {{ item.badge }}
          </span>
          <span
            v-if="item.time"
            class="font-mono text-[12px] text-foreground-muted"
          >
            {{ item.time }}
          </span>
          <span
            v-if="item.user"
            class="text-[12px] text-foreground-muted"
          >· {{ item.user }}</span>
        </div>
        <p class="mt-0.5 font-semibold text-foreground">
          {{ item.label }}
        </p>
        <p
          v-if="item.description"
          class="mt-0.5 break-words text-foreground-muted"
        >
          {{ item.description }}
        </p>
      </div>
    </li>
  </ol>
</template>
