import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ref } from 'vue';

// useApiFetch 를 mock — 실제 네트워크 없이 목록·미읽음 카운트·읽음 처리 경로만 검증한다.
// list/count 셋업 호출은 .json(), mutation 은 .patch().json()/.post().json() 체인을 탄다.
const listData = ref<unknown>(null);
const countData = ref<unknown>(null);
const listExecute = vi.fn().mockResolvedValue(undefined);
const countExecute = vi.fn().mockResolvedValue(undefined);
const patchExecute = vi.fn().mockResolvedValue(undefined);
const postExecute = vi.fn().mockResolvedValue(undefined);

vi.mock('@/lib/api', () => ({
  useApiFetch: vi.fn((url: string) => {
    if (url.includes('unread-count')) {
      return {
        json: () => ({
          data: countData,
          isFetching: ref(false),
          error: ref(null),
          execute: countExecute,
        }),
      };
    }
    if (url.includes('/read-all')) {
      return { post: () => ({ json: () => ({ execute: postExecute }) }) };
    }
    if (/\/api\/notifications\/\d+\/read$/.test(url)) {
      return { patch: () => ({ json: () => ({ execute: patchExecute }) }) };
    }
    // 목록 fetch
    return {
      json: () => ({
        data: listData,
        isFetching: ref(false),
        error: ref(null),
        execute: listExecute,
      }),
    };
  }),
}));

import { useNotifications } from '@/composables/useNotifications';

describe('useNotifications', () => {
  beforeEach(() => {
    listData.value = null;
    countData.value = null;
    listExecute.mockClear();
    countExecute.mockClear();
    patchExecute.mockClear();
    postExecute.mockClear();
  });

  it('fetch — 목록 content 를 items 로, totalElements 를 노출', () => {
    listData.value = {
      data: {
        content: [
          { id: 1, type: 'TICKET_STATUS_CHANGED', title: '제목', body: '본문', relatedUrl: '/itsm/1', read: false, readAt: null, createdAt: '2026-05-30T10:00:00' },
        ],
        totalElements: 1,
      },
    };
    const { items, totalElements } = useNotifications();

    expect(items.value).toHaveLength(1);
    expect(items.value[0]?.id).toBe(1);
    expect(totalElements.value).toBe(1);
  });

  it('unreadCount — reload 후 서버 카운트를 반영', async () => {
    countData.value = { data: 3 };
    const { unreadCount, reload } = useNotifications();

    expect(unreadCount.value).toBe(0); // 초기엔 0
    await reload();
    expect(unreadCount.value).toBe(3);
    expect(listExecute).toHaveBeenCalled();
    expect(countExecute).toHaveBeenCalled();
  });

  it('markRead — 읽음 PATCH 후 목록·카운트 재조회', async () => {
    const { markRead } = useNotifications();
    listExecute.mockClear();
    countExecute.mockClear();

    await markRead(42);

    expect(patchExecute).toHaveBeenCalledOnce();
    expect(listExecute).toHaveBeenCalled();
    expect(countExecute).toHaveBeenCalled();
  });

  it('markAllRead — 전체 읽음 POST 후 재조회', async () => {
    const { markAllRead } = useNotifications();

    await markAllRead();

    expect(postExecute).toHaveBeenCalledOnce();
    expect(listExecute).toHaveBeenCalled();
  });
});
