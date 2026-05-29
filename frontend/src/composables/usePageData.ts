import { computed, ref, type Ref } from 'vue';
import { useApiFetch } from '@/lib/api';
import type { ApiEnvelope } from '@/types/meta';
import type { PageResponse } from '@/types/page';

export interface DataQuery {
  page?:    number;
  size?:    number;
  sort?:    string;                 // "field,asc" | "field,desc"
  filters?: Record<string, string>;
}

export interface UsePageDataResult<T> {
  rows:           Ref<T[]>;
  page:           Ref<number>;
  totalElements:  Ref<number>;
  totalPages:     Ref<number>;
  size:           Ref<number>;
  isFetching:     Ref<boolean>;
  error:          Ref<string | null>;
  reload:         () => Promise<unknown>;
  setQuery:       (q: DataQuery) => void;
}

/**
 * meta.api + 페이지·정렬·필터 파라미터를 query string 으로 빌드.
 * 별도 함수로 분리하여 단위 테스트 가능하게 한다(빈 필터 값 제외 규칙 포함).
 */
export function buildUrl(api: string, q: DataQuery): string {
  const p = new URLSearchParams();
  p.set('page', String(q.page ?? 0));
  p.set('size', String(q.size ?? 20));
  if (q.sort) p.set('sort', q.sort);
  for (const [k, v] of Object.entries(q.filters ?? {})) {
    if (v !== '' && v !== undefined) p.set(k, v);
  }
  return `${api}?${p.toString()}`;
}

/**
 * meta.api 경로로 ApiEnvelope<PageResponse<T>> 를 fetch (CLAUDE.md — VueUse useFetch 표준).
 * reactive URL + refetch 패턴이라 setQuery 로 page/sort/filters 를 바꾸면 자동 재조회된다.
 * api 가 비어있거나 null 이면 빈 결과로 동작(no-op).
 * 도메인-중립: T 는 호출자가 알려준다(DynamicPage 는 Record<string, unknown>).
 */
export function usePageData<T>(api: Ref<string | null | undefined>): UsePageDataResult<T> {
  const query = ref<DataQuery>({ page: 0, size: 20 });

  const url = computed<string>(() => {
    const a = api.value;
    if (!a) return '';
    return buildUrl(a, query.value);
  });

  const { data, statusCode, error, isFetching, execute } = useApiFetch(url, {
    refetch: true,
  }).json<ApiEnvelope<PageResponse<T>>>();

  const rows          = computed<T[]>(() => data.value?.data?.content ?? []);
  const page          = computed(() => data.value?.data?.number        ?? 0);
  const totalElements = computed(() => data.value?.data?.totalElements ?? 0);
  const totalPages    = computed(() => data.value?.data?.totalPages    ?? 0);
  const size          = computed(() => data.value?.data?.size          ?? (query.value.size ?? 20));

  const errorMsg = computed<string | null>(() => {
    if (error.value) return error.value.message ?? '데이터 조회 실패';
    if (statusCode.value && statusCode.value >= 400) {
      return data.value?.message ?? `HTTP ${statusCode.value}`;
    }
    return null;
  });

  function setQuery(q: DataQuery): void {
    query.value = { ...query.value, ...q };
  }

  return {
    rows,
    page,
    totalElements,
    totalPages,
    size,
    isFetching,
    error: errorMsg,
    reload: execute,
    setQuery,
  };
}
