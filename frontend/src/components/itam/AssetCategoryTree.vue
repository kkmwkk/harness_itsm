<script setup lang="ts">
import { treeNodeClass } from '@/composables/useAssetCategory';
import type { AssetCategoryNode } from '@/types/asset-category';

/**
 * 자산 분류 트리 사이드 네비 — 자기 자신을 재귀 렌더링하여 children 을 들여쓰기한다.
 * 선택된 분류(selectedCode)는 treeNodeClass 로 강조한다. 활성 분류만 들어온다(IndexPage 가 가지치기).
 */
defineOptions({ name: 'AssetCategoryTree' });

interface Props {
  nodes: AssetCategoryNode[];
  selectedCode: string | null;
  /** 들여쓰기 깊이 — 재귀 시 +1 (ARCHITECTURE §16-2 의 16px 단위는 UI 토큰 4 배수). */
  depth?: number;
}
const props = withDefaults(defineProps<Props>(), { depth: 0 });

const emit = defineEmits<{ select: [code: string] }>();
</script>

<template>
  <ul class="space-y-0.5">
    <li
      v-for="node in props.nodes"
      :key="node.code"
    >
      <button
        type="button"
        :class="[
          'flex w-full items-center rounded-md px-2 py-1.5 text-left text-[13px] transition-colors',
          treeNodeClass(node.code, props.selectedCode),
        ]"
        :style="{ paddingLeft: `${8 + props.depth * 16}px` }"
        @click="emit('select', node.code)"
      >
        <span class="truncate">{{ node.label }}</span>
        <span
          v-if="!node.formMetaGroupId"
          class="ml-2 text-[11px] text-foreground-subtle"
        >(상위)</span>
      </button>

      <!-- 재귀: 자식 분류 -->
      <AssetCategoryTree
        v-if="node.children.length"
        :nodes="node.children"
        :selected-code="props.selectedCode"
        :depth="props.depth + 1"
        @select="(code) => emit('select', code)"
      />
    </li>
  </ul>
</template>
