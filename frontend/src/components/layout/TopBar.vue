<script setup lang="ts">
import { MenuIcon, CircleUserIcon } from '@lucide/vue';
import { useLayoutStore } from '@/stores/useLayoutStore';

const layout = useLayoutStore();
</script>

<template>
  <header
    class="sticky top-0 z-40 flex h-14 items-center gap-3 border-b border-border bg-surface px-4"
  >
    <button
      type="button"
      class="inline-flex h-9 w-9 items-center justify-center rounded-md text-foreground hover:bg-surface-hover md:hidden"
      aria-label="메뉴 열기"
      @click="layout.toggleMobile()"
    >
      <MenuIcon
        class="h-5 w-5"
        :stroke-width="1.5"
      />
    </button>
    <RouterLink
      to="/"
      class="text-[15px] font-semibold tracking-tight text-foreground hover:text-primary"
    >
      Polestar10 ITG
    </RouterLink>
    <div class="ml-auto flex items-center gap-2">
      <button
        type="button"
        class="inline-flex h-9 items-center gap-2 rounded-md px-3 text-sm text-foreground hover:bg-surface-hover"
        aria-label="사용자 메뉴"
      >
        <CircleUserIcon
          class="h-5 w-5"
          :stroke-width="1.5"
        />
        <span class="hidden sm:inline">샘플 사용자</span>
      </button>
    </div>
  </header>
</template>
