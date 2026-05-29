import { ref, type Ref } from 'vue';
import { useApiFetch } from '@/lib/api';
import { UI, mapErrorCode } from '@/lib/ui-messages';
import type { ApiEnvelope } from '@/types/meta';

export interface MutationResult<TIn, TOut> {
  isLoading: Ref<boolean>;
  error: Ref<string | null>;
  submit: (path: string, payload: TIn) => Promise<TOut | null>;
}

/**
 * POST 류 변경 요청 공통 helper (CLAUDE.md 절대 규칙 — VueUse useFetch 기반).
 * usePageData 가 조회(GET)를 담당하듯, 본 composable 은 생성/수정 호출 시점 제어를 담당한다.
 * - 호출 시점 제어: immediate:false · refetch:false 로 두고 submit() 안에서 1회 실행.
 * - 결과 정규화: 성공이면 ApiEnvelope.data, 실패면 null 을 돌려주고 error 에 메시지 적재.
 * - 도메인-중립: TIn/TOut 은 호출자가 알려준다(DynamicPage 는 Record<string, unknown>).
 */
export function useDataMutation<TIn, TOut>(): MutationResult<TIn, TOut> {
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function submit(path: string, payload: TIn): Promise<TOut | null> {
    isLoading.value = true;
    error.value = null;
    try {
      const {
        data,
        statusCode,
        error: fetchError,
      } = await useApiFetch(path, {
        immediate: false,
        refetch: false,
      })
        .post(payload)
        .json<ApiEnvelope<TOut>>();

      if (fetchError.value || (statusCode.value && statusCode.value >= 400)) {
        // errorCode 가 있으면 카탈로그(ADR-020)로 매핑, 없으면 백엔드 message → UI.error.submit fallback.
        const code = data.value?.errorCode;
        error.value = code
          ? mapErrorCode(code)
          : (data.value?.message ?? fetchError.value?.message ?? UI.error.submit);
        return null;
      }
      return data.value?.data ?? null;
    } finally {
      isLoading.value = false;
    }
  }

  return { isLoading, error, submit };
}
