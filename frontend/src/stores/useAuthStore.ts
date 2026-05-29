import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { TokenResponse, UserSummary } from '@/types/auth';

const ACCESS_KEY = 'itg.auth.access';
const REFRESH_KEY = 'itg.auth.refresh';

/**
 * JWT 세션 store (ADR-008). access/refresh 토큰은 localStorage 에 보존하고,
 * user/roles/permissions 는 로그인·/me 응답으로 채운다(새로고침 시 /me 재호출).
 *
 * localStorage 는 XSS 위험이 있으나 httpOnly cookie 도입은 별도 ADR 로 미룬다 —
 * v2.1 MVP 는 localStorage 로 시작한다. 키는 `itg.auth.*` 규칙만 사용한다.
 */
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem(ACCESS_KEY));
  const refreshToken = ref<string | null>(localStorage.getItem(REFRESH_KEY));
  const user = ref<UserSummary | null>(null);
  const roles = ref<string[]>([]);
  const permissions = ref<string[]>([]);

  const isAuthenticated = computed(() => !!accessToken.value);

  function hasRole(code: string): boolean {
    return roles.value.includes(code);
  }
  function hasPermission(code: string): boolean {
    return permissions.value.includes(code);
  }

  function setSession(t: TokenResponse): void {
    accessToken.value = t.accessToken;
    refreshToken.value = t.refreshToken;
    user.value = t.user;
    roles.value = t.roles;
    permissions.value = t.permissions;
    localStorage.setItem(ACCESS_KEY, t.accessToken);
    localStorage.setItem(REFRESH_KEY, t.refreshToken);
  }

  function clearSession(): void {
    accessToken.value = null;
    refreshToken.value = null;
    user.value = null;
    roles.value = [];
    permissions.value = [];
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  }

  return {
    accessToken,
    refreshToken,
    user,
    roles,
    permissions,
    isAuthenticated,
    hasRole,
    hasPermission,
    setSession,
    clearSession,
  };
});
