import { computed, toValue, type MaybeRefOrGetter, type Ref } from 'vue';
import { useApiFetch } from '@/lib/api';
import type { ApiEnvelope, PageMeta } from '@/types/meta';

/**
 * 메타 조회 식별자 — 두 모드 중 하나.
 * - `{ groupId }` : PUBLISHED 최신 1건 (화면 노출용, 버전 라우팅 / 404 → notPublished)
 * - `{ metaId }`  : 특정 버전 단건 (어떤 metaStatus 든 반환, 이력 복원용 / 404 → error)
 */
export type MetaIdent = { groupId: string } | { metaId: string };

export interface UsePageMetaResult {
  /** 조회된 메타. 없으면 null. */
  meta: Ref<PageMeta | null>;
  /**
   * group 모드에서 PUBLISHED 가 없는 경우(404 + META_NOT_PUBLISHED) true.
   * metaId 모드는 명시적 버전 지정이므로 항상 false.
   */
  notPublished: Ref<boolean>;
  /** 기타 에러 메시지 */
  error: Ref<string | null>;
  isFetching: Ref<boolean>;
  /** 재조회 */
  execute: () => Promise<unknown>;
}

/**
 * 식별자 → 조회 URL 결정. string 단축은 groupId(active) 로 해석(기존 호환).
 * encodeURIComponent 로 한글·슬래시 등을 안전하게 escape 한다.
 */
export function resolvePageMetaUrl(ident: MetaIdent | string): string {
  if (typeof ident === 'string') return `/api/meta/active/${encodeURIComponent(ident)}`;
  if ('metaId' in ident) return `/api/meta/${encodeURIComponent(ident.metaId)}`;
  return `/api/meta/active/${encodeURIComponent(ident.groupId)}`;
}

/** metaId(단건) 모드인지 판별 — notPublished 의미 분기에 사용. */
export function isMetaIdMode(ident: MetaIdent | string): boolean {
  return typeof ident !== 'string' && 'metaId' in ident;
}

/**
 * 메타 조회 단일 진입점 (ADR-004 · ADR-006).
 * - string | { groupId } → GET /api/meta/active/{groupId} (PUBLISHED 최신, DRAFT 차단)
 * - { metaId }           → GET /api/meta/{metaId}         (어떤 상태든, 이력 복원)
 * 식별자는 reactive(MaybeRefOrGetter) 로 받아 라우트 파라미터 변경에 자연스럽게 반응한다.
 */
export function usePageMeta(
  ident: MaybeRefOrGetter<MetaIdent | string>,
): UsePageMetaResult {
  const resolved = computed(() => toValue(ident));
  const url = computed(() => resolvePageMetaUrl(resolved.value));
  const metaIdMode = computed(() => isMetaIdMode(resolved.value));

  const { data, statusCode, error, isFetching, execute } = useApiFetch(url, {
    refetch: true,
  }).json<ApiEnvelope<PageMeta>>();

  const meta = computed<PageMeta | null>(() => data.value?.data ?? null);

  const notPublished = computed<boolean>(() => {
    if (metaIdMode.value) return false;
    if (statusCode.value !== 404) return false;
    // envelope.errorCode 우선, 누락 시 error.message 의 토큰 검사 (방어적 매칭)
    return (
      data.value?.errorCode === 'META_NOT_PUBLISHED' ||
      (error.value?.message?.includes('META_NOT_PUBLISHED') ?? false)
    );
  });

  const errorMsg = computed<string | null>(() => {
    if (notPublished.value) return null;
    if (error.value) {
      // 백엔드 envelope 의 errorCode 가 있고 메시지가 코드를 포함하면 한글 메시지만 노출
      const raw = error.value.message ?? '';
      const code = data.value?.errorCode;
      if (code && raw.startsWith(`${code}:`)) {
        return raw.slice(code.length + 1).trim() || '메타 조회 실패';
      }
      return raw || '메타 조회 실패';
    }
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
