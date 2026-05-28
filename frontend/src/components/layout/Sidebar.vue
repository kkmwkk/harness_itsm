<script setup lang="ts">
import type { FunctionalComponent } from 'vue';
import {
  LayoutDashboardIcon,
  TicketCheckIcon,
  BoxesIcon,
  FolderKanbanIcon,
  LayersIcon,
  DatabaseIcon,
} from '@lucide/vue';
import { useLayoutStore } from '@/stores/useLayoutStore';

type NavItem = {
  to: string;
  label: string;
  icon: FunctionalComponent;
  /** 정확 매칭만 활성 처리 (예: "/" 가 모든 경로의 prefix 가 되는 문제 회피) */
  exact?: boolean;
};

const navItems: NavItem[] = [
  { to: '/', label: '대시보드', icon: LayoutDashboardIcon, exact: true },
  { to: '/itsm', label: 'ITSM', icon: TicketCheckIcon },
  { to: '/itam', label: 'ITAM', icon: BoxesIcon },
  { to: '/pms', label: 'PMS', icon: FolderKanbanIcon },
  { to: '/common', label: '공통', icon: LayersIcon },
  { to: '/system/meta', label: '시스템 / 메타 관리', icon: DatabaseIcon },
];

const layout = useLayoutStore();
</script>

<template>
  <aside
    class="fixed inset-y-0 left-0 z-40 w-60 shrink-0 border-r border-border bg-surface-muted transition-transform duration-200 ease-out md:static md:translate-x-0"
    :class="layout.sidebarMobileOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'"
    aria-label="주 메뉴"
  >
    <div class="flex h-14 items-center border-b border-border px-4">
      <span class="text-sm font-semibold tracking-tight text-foreground">메뉴</span>
    </div>
    <nav class="flex flex-col gap-1 p-3">
      <RouterLink
        v-for="item in navItems"
        :key="item.to"
        v-slot="{ href, navigate, isActive, isExactActive }"
        :to="item.to"
        custom
      >
        <a
          :href="href"
          :class="[
            'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
            (item.exact ? isExactActive : isActive)
              ? 'bg-surface-selected text-primary'
              : 'text-foreground hover:bg-surface-hover',
          ]"
          :aria-current="(item.exact ? isExactActive : isActive) ? 'page' : undefined"
          @click="
            (e) => {
              navigate(e);
              layout.closeMobile();
            }
          "
        >
          <component
            :is="item.icon"
            class="h-4 w-4"
            :stroke-width="1.5"
          />
          <span>{{ item.label }}</span>
        </a>
      </RouterLink>
    </nav>
  </aside>
</template>
