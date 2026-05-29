import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useApiFetch } from '@/lib/api';
import type { ApiEnvelope } from '@/types/meta';
import type { MenuNode } from '@/types/menu';

/**
 * 동적 메뉴 store (ARCHITECTURE §8-3).
 * `/api/menu` 는 현재 로그인 사용자의 권한으로 필터된 트리를 반환한다 —
 * 클라이언트에서 다시 필터하지 않는다.
 */
export const useMenuStore = defineStore('menu', () => {
  const tree = ref<MenuNode[]>([]);
  const isLoading = ref(false);
  const lastError = ref<string | null>(null);

  async function load() {
    if (isLoading.value) return;
    isLoading.value = true;
    lastError.value = null;
    try {
      const { data, error } = await useApiFetch('/api/menu').json<
        ApiEnvelope<MenuNode[]>
      >();
      if (error.value) {
        lastError.value = '메뉴를 불러올 수 없습니다.';
        return;
      }
      tree.value = data.value?.data ?? [];
    } finally {
      isLoading.value = false;
    }
  }

  function clear() {
    tree.value = [];
    lastError.value = null;
  }

  /** route 로 메뉴 노드 찾기 (active 표시·매핑용 — DFS). */
  function findByRoute(route: string): MenuNode | null {
    function dfs(nodes: MenuNode[]): MenuNode | null {
      for (const node of nodes) {
        if (node.route === route) return node;
        const found = dfs(node.children);
        if (found) return found;
      }
      return null;
    }
    return dfs(tree.value);
  }

  return { tree, isLoading, lastError, load, clear, findByRoute };
});
