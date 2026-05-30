import { computed, onScopeDispose, ref, type Ref } from 'vue';
import { useIntervalFn } from '@vueuse/core';
import { useApiFetch } from '@/lib/api';
import { UI, mapErrorCode } from '@/lib/ui-messages';
import type { ApiEnvelope } from '@/types/meta';
import type { PageResponse } from '@/types/page';
import type { NotificationItem } from '@/types/notification';

export interface UseNotificationsOptions {
  /** 미읽음만 조회 (기본 false — 전체). */
  unreadOnly?: boolean;
  /** 페이지 크기 (기본 20). */
  size?: number;
  /** 30초 폴링 활성화 (기본 false). */
  poll?: boolean;
  /** 폴링 주기(ms). */
  intervalMs?: number;
}

export interface UseNotificationsResult {
  items: Ref<NotificationItem[]>;
  unreadCount: Ref<number>;
  totalElements: Ref<number>;
  isFetching: Ref<boolean>;
  error: Ref<string | null>;
  reload: () => Promise<void>;
  markRead: (id: number) => Promise<void>;
  markAllRead: () => Promise<void>;
}

/**
 * 알림 목록 + 미읽음 개수 조회·읽음 처리 (`/api/notifications`). 실시간 push 없이 폴링 stub.
 * - opts.poll 이 true 면 intervalMs(기본 30초)마다 목록·미읽음 개수를 재조회한다(스코프 종료 시 정지).
 * - markRead/markAllRead 는 처리 후 목록·미읽음 개수를 재조회해 화면을 갱신한다.
 */
export function useNotifications(opts: UseNotificationsOptions = {}): UseNotificationsResult {
  const size = opts.size ?? 20;
  const listUrl = `/api/notifications?unreadOnly=${opts.unreadOnly ? 'true' : 'false'}&page=0&size=${size}`;

  const {
    data: listData,
    isFetching,
    error: fetchError,
    execute: reloadList,
  } = useApiFetch(listUrl, { refetch: true }).json<ApiEnvelope<PageResponse<NotificationItem>>>();

  const {
    data: countData,
    execute: reloadCount,
  } = useApiFetch('/api/notifications/unread-count', { refetch: true }).json<ApiEnvelope<number>>();

  // 미읽음 개수 — 서버 카운트를 우선 신뢰하되, 초기 fetch 전에는 0.
  const serverCount = computed<number>(() => countData.value?.data ?? 0);
  const unreadCount = ref(0);
  function syncCount() {
    unreadCount.value = serverCount.value;
  }

  const items = computed<NotificationItem[]>(() => listData.value?.data?.content ?? []);
  const totalElements = computed<number>(() => listData.value?.data?.totalElements ?? 0);

  const error = computed<string | null>(() => {
    if (!fetchError.value) return null;
    const code = listData.value?.errorCode;
    return code ? mapErrorCode(code) : UI.error.dataLoad;
  });

  async function reload(): Promise<void> {
    await Promise.all([reloadList(), reloadCount()]);
    syncCount();
  }

  async function markRead(id: number): Promise<void> {
    const { execute } = useApiFetch(`/api/notifications/${id}/read`).patch().json();
    await execute();
    await reload();
  }

  async function markAllRead(): Promise<void> {
    const { execute } = useApiFetch('/api/notifications/read-all').post().json();
    await execute();
    await reload();
  }

  if (opts.poll) {
    const { pause } = useIntervalFn(() => {
      void reload();
    }, opts.intervalMs ?? 30_000);
    onScopeDispose(pause);
  }

  return {
    items,
    unreadCount,
    totalElements,
    isFetching,
    error,
    reload,
    markRead,
    markAllRead,
  };
}
