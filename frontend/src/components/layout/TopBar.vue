<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { MenuIcon, CircleUserIcon, LogOutIcon } from '@lucide/vue';
import { storeToRefs } from 'pinia';
import { useLayoutStore } from '@/stores/useLayoutStore';
import { useAuthStore } from '@/stores/useAuthStore';

const layout = useLayoutStore();
const auth = useAuthStore();
const router = useRouter();
const { user } = storeToRefs(auth);

const displayName = computed(() => user.value?.name ?? user.value?.username ?? '');

function onLogout() {
  auth.clearSession();
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
    <div class="ml-auto flex items-center gap-1">
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
