import { computed, type Ref } from 'vue';
import { useApiFetch } from '@/lib/api';
import { UI, mapErrorCode } from '@/lib/ui-messages';
import type { ApiEnvelope } from '@/types/meta';
import type { AssetCategoryNode } from '@/types/asset-category';

/** code 로 트리에서 노드를 재귀 탐색(없으면 null). */
export function findByCode(
  nodes: AssetCategoryNode[],
  code: string,
): AssetCategoryNode | null {
  for (const n of nodes) {
    if (n.code === code) return n;
    const found = findByCode(n.children, code);
    if (found) return found;
  }
  return null;
}

/**
 * 비활성 분류를 제외한 트리를 재귀적으로 만든다(금지사항: 비-활성 분류 노출 금지).
 * 비활성 노드는 자손까지 통째로 가지치기한다 — path 기반이라 비활성 부모의 자식은 접근 불가.
 */
export function filterActiveTree(nodes: AssetCategoryNode[]): AssetCategoryNode[] {
  return nodes
    .filter((n) => n.active)
    .map((n) => ({ ...n, children: filterActiveTree(n.children) }));
}

/** 트리 노드 강조 클래스 — 선택된 분류만 surface-selected 로 표시(UI_GUIDE §3-2). */
export function treeNodeClass(code: string, selectedCode: string | null): string {
  return code === selectedCode
    ? 'bg-surface-selected text-foreground font-medium'
    : 'text-foreground hover:bg-surface-hover';
}

export interface UseAssetCategoryTreeResult {
  /** 활성 분류만 담은 트리(비활성·자손 가지치기 완료). */
  tree: Ref<AssetCategoryNode[]>;
  isFetching: Ref<boolean>;
  error: Ref<string | null>;
  reload: () => Promise<void>;
}

/**
 * 자산 분류 트리 조회 (GET /api/asset-categories/tree).
 * 응답을 활성 노드만 남기도록 가지치기한 뒤 노출한다.
 */
export function useAssetCategoryTree(): UseAssetCategoryTreeResult {
  const {
    data,
    isFetching,
    error: fetchError,
    execute,
  } = useApiFetch('/api/asset-categories/tree', { refetch: true }).json<
    ApiEnvelope<AssetCategoryNode[]>
  >();

  const tree = computed<AssetCategoryNode[]>(() =>
    filterActiveTree(data.value?.data ?? []),
  );

  const error = computed<string | null>(() => {
    if (!fetchError.value) return null;
    const code = data.value?.errorCode;
    return code ? mapErrorCode(code) : UI.error.dataLoad;
  });

  async function reload(): Promise<void> {
    await execute();
  }

  return { tree, isFetching, error, reload };
}
