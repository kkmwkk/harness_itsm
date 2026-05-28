import { computed, toValue, type MaybeRefOrGetter, type Ref } from 'vue';
import { useApiFetch } from '@/lib/api';
import type { ApiEnvelope, PageMeta } from '@/types/meta';

export interface UsePageMetaResult {
  /** PUBLISHED 최신 메타. PUBLISHED 가 없으면 null. */
  meta: Ref<PageMeta | null>;
  /** PUBLISHED 가 없는 경우(404 + META_NOT_PUBLISHED) true */
  notPublished: Ref<boolean>;
  /** 기타 에러 메시지 */
  error: Ref<string | null>;
  isFetching: Ref<boolean>;
  /** 재조회 */
  execute: () => Promise<unknown>;
}

/**
 * 화면 노출용 메타 조회 단일 진입점 — GET /api/meta/active/{groupId}.
 * groupId 가 비어 있으면 요청하지 않는다(immediate:false 대신 빈 값일 때 호출 회피).
 * DRAFT 메타는 백엔드 버전 라우팅에서 차단되므로 절대 반환되지 않는다(클라이언트 사이드 안전망).
 */
export function usePageMeta(groupId: MaybeRefOrGetter<string>): UsePageMetaResult {
  const url = computed(() => `/api/meta/active/${encodeURIComponent(toValue(groupId))}`);

  const { data, statusCode, error, isFetching, execute } = useApiFetch(url, {
    refetch: true,
  }).json<ApiEnvelope<PageMeta>>();

  const meta = computed<PageMeta | null>(() => data.value?.data ?? null);

  const notPublished = computed<boolean>(
    () => statusCode.value === 404 && data.value?.errorCode === 'META_NOT_PUBLISHED',
  );

  const errorMsg = computed<string | null>(() => {
    if (notPublished.value) return null;
    if (error.value) return error.value.message ?? '메타 조회 실패';
    return null;
  });

  return {
    meta,
    notPublished,
    error: errorMsg,
    isFetching,
    execute,
  };
}
