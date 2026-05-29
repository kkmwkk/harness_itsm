<script setup lang="ts">
import type { DeptTreeNode as DeptNode } from '@/types/system';

/**
 * 부서 트리 재귀 노드 (Sidebar 의 MenuTreeNode 와 같은 자기참조 재귀 패턴).
 * 표현 전용 — 선택 이벤트만 위로 올린다. 권한 가드는 페이지가 책임진다.
 */
interface Props {
  node: DeptNode;
  selectedId: number | null;
  depth?: number;
}
withDefaults(defineProps<Props>(), { depth: 0 });
const emit = defineEmits<{ (e: 'select', node: DeptNode): void }>();
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
      <span class="flex-1 truncate">{{ node.name }}</span>
      <span class="text-xs text-foreground-subtle truncate">{{ node.code }}</span>
    </button>
    <DeptTreeNode
      v-for="child in node.children"
      :key="child.id"
      :node="child"
      :selected-id="selectedId"
      :depth="depth + 1"
      @select="emit('select', $event)"
    />
  </div>
</template>
