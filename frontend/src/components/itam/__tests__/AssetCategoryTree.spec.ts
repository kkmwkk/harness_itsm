import { describe, it, expect } from 'vitest';
import { treeNodeClass, findByCode } from '@/composables/useAssetCategory';
import type { AssetCategoryNode } from '@/types/asset-category';

/**
 * AssetCategoryTree 는 자기 자신을 재귀 렌더링하며, 선택 강조는 treeNodeClass 에 위임한다.
 * (vitest 환경이 node 라 DOM 마운트 대신 렌더링이 의존하는 순수 로직을 검증한다.)
 */

const tree: AssetCategoryNode[] = [
  {
    code: 'HW',
    label: 'HW',
    parentCode: null,
    path: 'HW',
    formMetaGroupId: null,
    sortOrder: 0,
    active: true,
    children: [
      {
        code: 'HW_LAPTOP',
        label: '노트북',
        parentCode: 'HW',
        path: 'HW/HW_LAPTOP',
        formMetaGroupId: 'itg-asset-hw-laptop',
        sortOrder: 0,
        active: true,
        children: [],
      },
    ],
  },
];

describe('treeNodeClass — selectedCode 강조', () => {
  it('선택된_코드는_surface-selected_강조', () => {
    expect(treeNodeClass('HW_LAPTOP', 'HW_LAPTOP')).toContain('bg-surface-selected');
  });

  it('미선택_코드는_hover_클래스만', () => {
    const cls = treeNodeClass('HW', 'HW_LAPTOP');
    expect(cls).not.toContain('bg-surface-selected');
    expect(cls).toContain('hover:bg-surface-hover');
  });

  it('selectedCode_null_이면_강조_없음', () => {
    expect(treeNodeClass('HW', null)).not.toContain('bg-surface-selected');
  });
});

describe('재귀 렌더링 — 자손 노드 탐색', () => {
  it('재귀_트리에서_자식_분류를_찾아낸다', () => {
    expect(findByCode(tree, 'HW_LAPTOP')?.label).toBe('노트북');
  });
});
