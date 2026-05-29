import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ref } from 'vue';
import { setActivePinia, createPinia } from 'pinia';
import { useMenuStore } from '@/stores/useMenuStore';
import type { MenuNode } from '@/types/menu';

// useApiFetch 를 모킹해 네트워크 없이 store 동작만 검증한다.
const useApiFetchMock = vi.fn();
vi.mock('@/lib/api', () => ({
  useApiFetch: (...args: unknown[]) => useApiFetchMock(...args),
}));

/** `.json()` 이 { data, error } ref 를 반환하는 useApiFetch 형태를 흉내낸다. */
function mockFetch(data: unknown, error: unknown = null) {
  useApiFetchMock.mockReturnValue({
    json: () => ({ data: ref(data), error: ref(error) }),
  });
}

function sampleTree(): MenuNode[] {
  return [
    {
      id: 1,
      code: 'ITSM',
      label: 'ITSM',
      icon: 'TicketCheck',
      route: '/itsm',
      groupId: 'itg-ticket',
      permissionCode: 'TICKET_READ',
      sortOrder: 1,
      children: [],
    },
    {
      id: 2,
      code: 'SYSTEM',
      label: '시스템 관리',
      icon: 'Database',
      route: null,
      groupId: null,
      permissionCode: 'USER_ADMIN',
      sortOrder: 9,
      children: [
        {
          id: 3,
          code: 'SYSTEM_USERS',
          label: '사용자 관리',
          icon: null,
          route: '/system/users',
          groupId: null,
          permissionCode: 'USER_ADMIN',
          sortOrder: 1,
          children: [],
        },
      ],
    },
  ];
}

describe('useMenuStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    useApiFetchMock.mockReset();
  });

  it('load_성공_시_tree_채움', async () => {
    mockFetch({ success: true, data: sampleTree() });
    const menu = useMenuStore();

    await menu.load();

    expect(menu.tree).toHaveLength(2);
    expect(menu.tree[0]?.code).toBe('ITSM');
    expect(menu.lastError).toBeNull();
    expect(menu.isLoading).toBe(false);
  });

  it('load_실패_시_lastError_set', async () => {
    mockFetch(null, new Error('boom'));
    const menu = useMenuStore();

    await menu.load();

    expect(menu.tree).toEqual([]);
    expect(menu.lastError).toBe('메뉴를 불러올 수 없습니다.');
  });

  it('clear_tree_비움', async () => {
    mockFetch({ success: true, data: sampleTree() });
    const menu = useMenuStore();
    await menu.load();
    expect(menu.tree).toHaveLength(2);

    menu.clear();

    expect(menu.tree).toEqual([]);
    expect(menu.lastError).toBeNull();
  });

  it('findByRoute_DFS_매칭', async () => {
    mockFetch({ success: true, data: sampleTree() });
    const menu = useMenuStore();
    await menu.load();

    expect(menu.findByRoute('/itsm')?.code).toBe('ITSM');
    expect(menu.findByRoute('/system/users')?.code).toBe('SYSTEM_USERS');
    expect(menu.findByRoute('/none')).toBeNull();
  });
});
