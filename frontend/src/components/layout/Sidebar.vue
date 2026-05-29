<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { useMenuStore } from '@/stores/useMenuStore';
import { useLayoutStore } from '@/stores/useLayoutStore';
import MenuTreeNode from '@/components/layout/MenuTreeNode.vue';

const menuStore = useMenuStore();
const layout = useLayoutStore();
const { tree, isLoading } = storeToRefs(menuStore);
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
      <p
        v-if="isLoading"
        class="px-2 text-xs text-foreground-muted"
      >
        메뉴 로드 중...
      </p>
      <template v-else>
        <MenuTreeNode
          v-for="node in tree"
          :key="node.id"
          :node="node"
        />
      </template>
    </nav>
  </aside>
</template>
