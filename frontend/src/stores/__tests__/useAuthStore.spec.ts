// @vitest-environment happy-dom
import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useAuthStore } from '@/stores/useAuthStore';
import type { TokenResponse } from '@/types/auth';

/** 가상 샘플만 사용한다(ADR-011). */
function sampleToken(): TokenResponse {
  return {
    accessToken: 'sample-access-token',
    refreshToken: 'sample-refresh-token',
    accessExpiresInSec: 900,
    user: {
      id: 1,
      username: 'sample-user',
      name: '샘플 사용자',
      email: 'sample@example.com',
      departmentName: '샘플팀',
    },
    roles: ['ROLE_ADMIN'],
    permissions: ['USER_ADMIN', 'META_PUBLISH'],
  };
}

describe('useAuthStore', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it('setSession_localStorage_저장_isAuthenticated_true', () => {
    const auth = useAuthStore();
    expect(auth.isAuthenticated).toBe(false);

    auth.setSession(sampleToken());

    expect(auth.isAuthenticated).toBe(true);
    expect(auth.accessToken).toBe('sample-access-token');
    expect(auth.user?.name).toBe('샘플 사용자');
    expect(localStorage.getItem('itg.auth.access')).toBe('sample-access-token');
    expect(localStorage.getItem('itg.auth.refresh')).toBe(
      'sample-refresh-token',
    );
  });

  it('clearSession_localStorage_제거_isAuthenticated_false', () => {
    const auth = useAuthStore();
    auth.setSession(sampleToken());

    auth.clearSession();

    expect(auth.isAuthenticated).toBe(false);
    expect(auth.accessToken).toBeNull();
    expect(auth.user).toBeNull();
    expect(auth.roles).toEqual([]);
    expect(auth.permissions).toEqual([]);
    expect(localStorage.getItem('itg.auth.access')).toBeNull();
    expect(localStorage.getItem('itg.auth.refresh')).toBeNull();
  });

  it('hasRole·hasPermission_검사', () => {
    const auth = useAuthStore();
    auth.setSession(sampleToken());

    expect(auth.hasRole('ROLE_ADMIN')).toBe(true);
    expect(auth.hasRole('ROLE_USER')).toBe(false);
    expect(auth.hasPermission('META_PUBLISH')).toBe(true);
    expect(auth.hasPermission('NONE')).toBe(false);
  });

  it('초기화_시_localStorage_의_토큰_복원', () => {
    localStorage.setItem('itg.auth.access', 'restored-access');
    localStorage.setItem('itg.auth.refresh', 'restored-refresh');
    setActivePinia(createPinia());

    const auth = useAuthStore();

    expect(auth.accessToken).toBe('restored-access');
    expect(auth.refreshToken).toBe('restored-refresh');
    expect(auth.isAuthenticated).toBe(true);
  });
});
