<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  MenuIcon,
  CircleUserIcon,
  LogOutIcon,
  LayoutGridIcon,
  ListIcon,
} from '@lucide/vue';
import { storeToRefs } from 'pinia';
import { useLayoutStore } from '@/stores/useLayoutStore';
import { useAuthStore } from '@/stores/useAuthStore';
import { useMenuStore } from '@/stores/useMenuStore';
import ThemeToggle from '@/components/layout/ThemeToggle.vue';
import NotificationBell from '@/components/notification/NotificationBell.vue';

const layout = useLayoutStore();
const auth = useAuthStore();
const menu = useMenuStore();
const router = useRouter();
const route = useRoute();
const { user } = storeToRefs(auth);

const displayName = computed(() => user.value?.name ?? user.value?.username ?? '');

// ITSM 화면(목록/보드)에서만 노출하는 목록↔보드 토글.
const isItsm = computed(() => route.path === '/itsm' || route.path === '/itsm/board');
const isBoard = computed(() => route.path === '/itsm/board');

function onLogout() {
  auth.clearSession();
  menu.clear();
  void router.push('/login');
}
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
    <RouterLink
      v-if="isItsm"
      :to="isBoard ? '/itsm' : '/itsm/board'"
      class="inline-flex h-9 items-center gap-1.5 rounded-md px-3 text-sm text-foreground hover:bg-surface-hover"
    >
      <component
        :is="isBoard ? ListIcon : LayoutGridIcon"
        class="h-4 w-4"
        :stroke-width="1.5"
      />
      <span class="hidden sm:inline">{{ isBoard ? '목록 보기' : '보드 보기' }}</span>
    </RouterLink>
    <div class="ml-auto flex items-center gap-1">
      <NotificationBell />
      <ThemeToggle />
      <span
        class="inline-flex h-9 items-center gap-2 rounded-md px-3 text-sm text-foreground"
      >
        <CircleUserIcon
          class="h-5 w-5"
          :stroke-width="1.5"
        />
        <span class="hidden sm:inline">{{ displayName }}</span>
      </span>
      <button
        type="button"
        class="inline-flex h-9 items-center gap-2 rounded-md px-3 text-sm text-foreground hover:bg-surface-hover"
        @click="onLogout"
      >
        <LogOutIcon
          class="h-5 w-5"
          :stroke-width="1.5"
        />
        <span class="hidden sm:inline">로그아웃</span>
      </button>
    </div>
  </header>
</template>
