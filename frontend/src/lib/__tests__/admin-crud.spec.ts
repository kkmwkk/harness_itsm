import { describe, it, expect } from 'vitest';
import {
  buildAdminQueryUrl,
  buildMenuTree,
  isDescendant,
} from '@/lib/admin-crud';
import type { MenuItem } from '@/types/system';

describe('buildAdminQueryUrl', () => {
  it('buildAdminQueryUrl_기본_+_filters_파라미터', () => {
    const url = buildAdminQueryUrl(
      '/api/users',
      { page: 1, size: 50, kw: '샘플' },
      { deptId: '3', status: '', role: 'ROLE_USER' },
    );
    expect(url.startsWith('/api/users?')).toBe(true);
    const params = new URLSearchParams(url.split('?')[1]);
    expect(params.get('page')).toBe('1');
    expect(params.get('size')).toBe('50');
    expect(params.get('kw')).toBe('샘플');
    expect(params.get('deptId')).toBe('3');
    expect(params.get('role')).toBe('ROLE_USER');
    // 빈 값 필터는 제외된다
    expect(params.has('status')).toBe(false);
  });

  it('공백뿐인 kw 는 생략한다', () => {
    const url = buildAdminQueryUrl('/api/users', { page: 0, size: 20, kw: '   ' });
    expect(new URLSearchParams(url.split('?')[1]).has('kw')).toBe(false);
  });
});

function menu(
  id: number,
  parentId: number | null,
  sortOrder: number,
  label = `m${id}`,
): MenuItem {
  return {
    id,
    code: `M${id}`,
    parentId,
    label,
    icon: null,
    sortOrder,
    route: null,
    groupId: null,
    permissionCode: null,
    active: true,
  };
}

describe('buildMenuTree', () => {
  it('parentId 로 트리를 조립하고 sortOrder 로 정렬한다', () => {
    const flat = [menu(2, 1, 1), menu(1, null, 0), menu(3, 1, 0)];
    const tree = buildMenuTree(flat);
    expect(tree).toHaveLength(1);
    expect(tree[0]!.id).toBe(1);
    expect(tree[0]!.children.map((c) => c.id)).toEqual([3, 2]);
  });
});

describe('isDescendant', () => {
  it('자기 자신·자손을 부모로 두는 이동을 차단한다', () => {
    const tree = buildMenuTree([menu(1, null, 0), menu(2, 1, 0), menu(3, 2, 0)]);
    expect(isDescendant(tree, 1, 1)).toBe(true); // 자기 자신
    expect(isDescendant(tree, 1, 3)).toBe(true); // 손자
    expect(isDescendant(tree, 2, 1)).toBe(false); // 부모로의 이동은 허용
  });
});
