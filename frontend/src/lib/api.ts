import { createFetch } from '@vueuse/core';
import { useAuthStore } from '@/stores/useAuthStore';

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/**
 * JWT 토큰 게터 — useAuthStore 의 accessToken 을 읽는다.
 * Pinia 가 setup 되기 전(app 부팅 전) 호출되면 예외가 날 수 있어 try/catch 로 보호한다.
 * 반환이 null 이면 Authorization 헤더를 붙이지 않는다.
 */
function readToken(): string | null {
  try {
    return useAuthStore().accessToken;
  } catch {
    return null;
  }
}

/**
 * 프로젝트 표준 API 클라이언트 (CLAUDE.md 절대 규칙 — VueUse useFetch).
 * - baseURL: VITE_API_BASE_URL
 * - beforeFetch: JWT Authorization 헤더 자동 주입
 * - onFetchError: 401 시 세션 클리어 + /login 리다이렉트, errorCode/message 를 ctx.error 로 정규화
 */
export const useApiFetch = createFetch({
  baseUrl: baseURL,
  options: {
    onFetchError(ctx) {
      const data = ctx.data as { errorCode?: string; message?: string } | null;
      const status = ctx.response?.status;
      // 401 AUTH_REQUIRED 또는 토큰 만료 → 세션 클리어 + /login 리다이렉트 (loop 방지)
      if (status === 401) {
        try {
          useAuthStore().clearSession();
          if (
            typeof window !== 'undefined' &&
            !location.pathname.startsWith('/login')
          ) {
            const next = encodeURIComponent(location.pathname + location.search);
            location.href = `/login?next=${next}`;
          }
        } catch {
          /* Pinia 미설정 등 — 무시 */
        }
      }
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
