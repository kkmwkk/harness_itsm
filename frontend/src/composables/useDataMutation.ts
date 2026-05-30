import { ref, onScopeDispose, getCurrentScope, type Ref } from 'vue';
import { useApiFetch } from '@/lib/api';
import { UI, mapErrorCode } from '@/lib/ui-messages';
import type { ApiEnvelope } from '@/types/meta';

/** optimisticUpdate 가 돌려주는 되돌리기 함수 — 실패 시 화면 변경을 원복한다. */
export type Rollback = () => void;

export interface MutationResult<TIn, TOut> {
  isLoading: Ref<boolean>;
  error: Ref<string | null>;
  submit: (path: string, payload: TIn) => Promise<TOut | null>;
  /**
   * Optimistic 변경 helper (UI_GUIDE §9). optimisticUpdate() 를 **즉시** 적용해 화면을
   * 먼저 바꾸고(반환된 rollback 보관) 요청을 보낸다.
   * - 실패(4xx/네트워크): rollback() 으로 화면 원복 + error 적재 → null 반환.
   * - 성공: rollback 하지 않고 data 반환(호출부가 reload 로 백엔드 truth 와 보정).
   * - 사용자가 요청 도중 스코프를 떠나면(언마운트) rollback·상태 변경을 건너뛴다(race 방지).
   */
  submitOptimistic: (
    path: string,
    payload: TIn,
    optimisticUpdate: () => Rollback,
  ) => Promise<TOut | null>;
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

  // 스코프 종료(컴포넌트 언마운트) 추적 — 응답 도착 후 rollback·상태 변경을 막아 race 방지.
  // 활성 effect scope 밖(테스트 직접 호출)에서는 등록을 생략한다(dev 경고 회피).
  let disposed = false;
  if (getCurrentScope()) {
    onScopeDispose(() => {
      disposed = true;
    });
  }

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

  async function submitOptimistic(
    path: string,
    payload: TIn,
    optimisticUpdate: () => Rollback,
  ): Promise<TOut | null> {
    isLoading.value = true;
    error.value = null;
    // 화면을 먼저 바꾼다(optimistic). 실패하면 이 rollback 으로 되돌린다.
    const rollback = optimisticUpdate();
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

      // 사용자가 응답 전에 떠났으면 화면 변경·rollback 을 하지 않는다(이미 사라진 화면).
      if (disposed) return null;

      if (fetchError.value || (statusCode.value && statusCode.value >= 400)) {
        rollback();
        const code = data.value?.errorCode;
        error.value = code
          ? mapErrorCode(code)
          : (data.value?.message ?? fetchError.value?.message ?? UI.error.submit);
        return null;
      }
      return data.value?.data ?? null;
    } finally {
      if (!disposed) isLoading.value = false;
    }
  }

  return { isLoading, error, submit, submitOptimistic };
}
