<script setup lang="ts">
import type { MenuAdminNode } from '@/types/system';

/**
 * 메뉴 트리 재귀 노드 (관리자 화면 — 권한 필터 없음, Sidebar 의 MenuTreeNode 와 같은 재귀 패턴).
 * 표현 전용 — 선택 이벤트만 위로 올린다.
 */
interface Props {
  node: MenuAdminNode;
  selectedId: number | null;
  depth?: number;
}
withDefaults(defineProps<Props>(), { depth: 0 });
const emit = defineEmits<{ (e: 'select', node: MenuAdminNode): void }>();
</script>

<template>
  <div>
    <button
      type="button"
      :class="[
        'flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition-colors',
        selectedId === node.id
          ? 'bg-surface-selected text-primary'
          : 'hover:bg-surface-hover',
        !node.active ? 'text-foreground-subtle line-through' : '',
      ]"
      :style="{ paddingLeft: `${depth * 12 + 8}px` }"
      :aria-current="selectedId === node.id ? 'true' : undefined"
      @click="emit('select', node)"
    >
      <span class="flex-1 truncate">{{ node.label }}</span>
      <span class="text-xs text-foreground-subtle truncate">{{ node.route ?? node.code }}</span>
    </button>
    <MenuAdminTreeNode
      v-for="child in node.children"
      :key="child.id"
      :node="child"
      :selected-id="selectedId"
      :depth="depth + 1"
      @select="emit('select', $event)"
    />
  </div>
</template>
