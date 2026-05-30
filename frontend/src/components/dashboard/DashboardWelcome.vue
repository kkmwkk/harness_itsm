<script setup lang="ts">
/**
 * 대시보드 환영 헤더 — 사용자명 + 오늘 날짜 + 빠른 액션(티켓 등록·자산 등록).
 * 사용자명은 useAuthStore 의 로그인 사용자(name 우선, 없으면 username)에서 읽는다.
 * 빠른 액션은 권한(RequirePermission)으로 가드한다 — UI 만 숨기고 보안 경계는 백엔드 책임.
 */
import { computed } from 'vue';
import { storeToRefs } from 'pinia';
import { PlusIcon, BoxesIcon, CalendarIcon } from '@lucide/vue';
import { useAuthStore } from '@/stores/useAuthStore';
import RequirePermission from '@/components/common/RequirePermission.vue';
import { Button } from '@/components/ui/button';

const { user } = storeToRefs(useAuthStore());

const displayName = computed<string>(() => user.value?.name || user.value?.username || '사용자');

/** 오늘 날짜 — ko-KR 풀 포맷(예: 2026년 5월 30일 토요일). 런타임 표기 전용. */
const today = computed<string>(() =>
  new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  }).format(new Date()),
);
</script>

<template>
  <section
    class="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 shadow-card sm:flex-row sm:items-center sm:justify-between"
  >
    <div class="min-w-0">
      <p class="flex items-center gap-1.5 text-[13px] text-foreground-muted">
        <CalendarIcon
          class="size-3.5 text-foreground-subtle"
          :stroke-width="1.5"
          aria-hidden="true"
        />
        {{ today }}
      </p>
      <h1 class="mt-1 text-[28px] font-bold leading-tight tracking-tight text-foreground">
        {{ displayName }}님, 안녕하세요
      </h1>
      <p class="mt-1 text-sm text-foreground-muted">
        오늘의 운영 현황을 한눈에 확인하세요.
      </p>
    </div>
    <div class="flex shrink-0 flex-wrap items-center gap-2">
      <RequirePermission code="TICKET_CREATE">
        <Button as-child>
          <RouterLink to="/itsm/new/INCIDENT">
            <PlusIcon
              class="size-4"
              :stroke-width="1.5"
            />
            티켓 등록
          </RouterLink>
        </Button>
      </RequirePermission>
      <RequirePermission code="ASSET_READ">
        <Button
          as-child
          variant="outline"
        >
          <RouterLink to="/itam">
            <BoxesIcon
              class="size-4"
              :stroke-width="1.5"
            />
            자산 등록
          </RouterLink>
        </Button>
      </RequirePermission>
    </div>
  </section>
</template>
