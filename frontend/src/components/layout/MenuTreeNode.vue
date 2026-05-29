<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRoute, RouterLink } from 'vue-router';
import * as Lucide from '@lucide/vue';
import type { Component } from 'vue';
import { useLayoutStore } from '@/stores/useLayoutStore';
import type { MenuNode } from '@/types/menu';

interface Props {
  node: MenuNode;
  depth?: number;
}
const props = withDefaults(defineProps<Props>(), { depth: 0 });

const route = useRoute();
const layout = useLayoutStore();
const open = ref(true);

const hasChildren = computed(() => props.node.children.length > 0);
const isActive = computed(
  () =>
    !!props.node.route &&
    (route.path === props.node.route ||
      route.path.startsWith(props.node.route + '/')),
);

/** lucide 아이콘명(예: 'TicketCheck' | 'TicketCheckIcon') → 컴포넌트. 없으면 null. */
function resolveIcon(name: string | null): Component | null {
  if (!name) return null;
  const all = Lucide as unknown as Record<string, Component>;
  return all[name] ?? all[`${name}Icon`] ?? null;
}
const IconComp = computed(() => resolveIcon(props.node.icon));

function onActivate() {
  if (hasChildren.value) {
    open.value = !open.value;
  } else {
    // 리프 노드(라우팅)는 모바일 사이드바를 닫는다.
    layout.closeMobile();
  }
}
</script>

<template>
  <div>
    <component
      :is="node.route && !hasChildren ? RouterLink : 'button'"
      :to="node.route && !hasChildren ? node.route : undefined"
      :class="[
        'flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition-colors',
        isActive ? 'bg-surface-selected text-primary' : 'hover:bg-surface-hover',
      ]"
      :style="{ paddingLeft: `${depth * 12 + 8}px` }"
      :aria-current="isActive ? 'page' : undefined"
      @click="onActivate"
    >
      <component
        :is="IconComp"
        v-if="IconComp"
        class="size-4 shrink-0"
        :stroke-width="1.5"
      />
      <span class="flex-1 truncate">{{ node.label }}</span>
      <Lucide.ChevronDownIcon
        v-if="hasChildren"
        :class="['size-4 transition-transform', open ? '' : '-rotate-90']"
        :stroke-width="1.5"
      />
    </component>

    <div
      v-if="hasChildren && open"
      class="flex flex-col gap-1"
    >
      <MenuTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :depth="depth + 1"
      />
    </div>
  </div>
</template>
