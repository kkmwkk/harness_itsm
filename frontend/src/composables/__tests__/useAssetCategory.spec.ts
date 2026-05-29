import { describe, it, expect } from 'vitest';
import { findByCode, filterActiveTree } from '@/composables/useAssetCategory';
import type { AssetCategoryNode } from '@/types/asset-category';

/** 가상 샘플 분류만 사용한다(ADR-011). */
function node(
  code: string,
  active = true,
  children: AssetCategoryNode[] = [],
  formMetaGroupId: string | null = null,
): AssetCategoryNode {
  return {
    code,
    label: code,
    parentCode: null,
    path: code,
    formMetaGroupId,
    sortOrder: 0,
    active,
    children,
  };
}

const tree: AssetCategoryNode[] = [
  node('HW', true, [
    node('HW_LAPTOP', true, [], 'itg-asset-hw-laptop'),
    node('HW_SERVER', false, [], 'itg-asset-hw-server'), // 비활성
  ]),
  node('SW', true, [node('SW_LICENSE', true, [], 'itg-asset-sw-license')]),
];

describe('findByCode', () => {
  it('루트_노드를_찾는다', () => {
    expect(findByCode(tree, 'HW')?.code).toBe('HW');
  });

  it('자손_노드를_재귀_탐색한다', () => {
    expect(findByCode(tree, 'SW_LICENSE')?.formMetaGroupId).toBe(
      'itg-asset-sw-license',
    );
  });

  it('없는_코드는_null', () => {
    expect(findByCode(tree, 'NOPE')).toBeNull();
  });
});

describe('filterActiveTree', () => {
  it('비활성_노드를_재귀적으로_가지치기한다', () => {
    const active = filterActiveTree(tree);
    const hw = findByCode(active, 'HW');
    expect(hw?.children.map((c) => c.code)).toEqual(['HW_LAPTOP']);
    expect(findByCode(active, 'HW_SERVER')).toBeNull();
  });

  it('원본_트리를_변형하지_않는다', () => {
    filterActiveTree(tree);
    expect(findByCode(tree, 'HW')?.children).toHaveLength(2);
  });
});
