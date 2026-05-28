import { createFetch } from '@vueuse/core';

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/**
 * JWT 토큰 게터. 현재 phase 는 토큰 없음 — 다음 phase 에서 auth store 연결.
 * 반환이 null 이면 Authorization 헤더를 붙이지 않는다.
 */
function readToken(): string | null {
  // TODO(phase-2-auth): useAuthStore 또는 localStorage 에서 토큰 조회
  return null;
}

/**
 * 프로젝트 표준 API 클라이언트 (CLAUDE.md 절대 규칙 — VueUse useFetch).
 * - baseURL: VITE_API_BASE_URL
 * - beforeFetch: JWT Authorization 헤더 자동 주입 골격
 * - onFetchError: ApiEnvelope 의 errorCode/message 를 ctx.error 로 정규화
 */
export const useApiFetch = createFetch({
  baseUrl: baseURL,
  options: {
    onFetchError(ctx) {
      const data = ctx.data as { errorCode?: string; message?: string } | null;
      if (data?.errorCode) {
        ctx.error = new Error(`${data.errorCode}: ${data.message ?? ''}`.trim());
      }
      return ctx;
    },
    beforeFetch(ctx) {
      const token = readToken();
      if (token) {
        ctx.options.headers = {
          ...(ctx.options.headers ?? {}),
          Authorization: `Bearer ${token}`,
        };
      }
      return ctx;
    },
  },
  fetchOptions: {
    mode: 'cors',
  },
});
