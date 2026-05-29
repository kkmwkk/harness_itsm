# Step 0: login-and-auth-store

## 읽어야 할 파일

- `/CLAUDE.md` v2.1 — 시드 사용자 정보·절대 규칙
- `/docs/PRD.md` §5-6 인증·권한·메뉴, §6 UX 베이스라인
- `/docs/ARCHITECTURE.md` §2-2 frontend 디렉토리, §8 JWT 흐름
- `/docs/ADR.md` ADR-008·ADR-020
- `/phases/9-auth-and-users/index.json` summaries — backend endpoints `/api/auth/login`·`/me`·`/refresh`, 401 AUTH_REQUIRED·403 FORBIDDEN 한글 응답
- `/frontend/src/lib/api.ts` (현재 `readToken()` 이 `null` 고정)
- `/frontend/src/router/index.ts`
- `/frontend/src/components/layout/AppLayout.vue`·`TopBar.vue`

## 작업

이 step 의 목적은 **`/login` 페이지를 만들고, `useAuthStore` (Pinia) 로 JWT 토큰·사용자 정보를 관리하며, `useApiFetch` 의 Authorization 헤더가 자동 주입되도록 wiring 하고, 비인증 사용자는 `/login` 으로 자동 리다이렉트하는 것**이다.

### 1. 타입 정의 — `src/types/auth.ts`

```ts
export type UserStatus = 'ACTIVE' | 'LOCKED' | 'RETIRED';

export interface UserSummary {
  id: number; username: string; name: string;
  email: string | null; departmentName: string | null;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  accessExpiresInSec: number;
  user: UserSummary;
  roles: string[];
  permissions: string[];
}

export interface MeResponse {
  user: UserSummary;
  roles: string[];
  permissions: string[];
}
```

### 2. `useAuthStore` — `src/stores/useAuthStore.ts`

Pinia setup store. setup() 안에서 `ref`/`computed` 사용.

```ts
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { TokenResponse, UserSummary } from '@/types/auth';

const ACCESS_KEY  = 'itg.auth.access';
const REFRESH_KEY = 'itg.auth.refresh';

export const useAuthStore = defineStore('auth', () => {
  const accessToken  = ref<string | null>(localStorage.getItem(ACCESS_KEY));
  const refreshToken = ref<string | null>(localStorage.getItem(REFRESH_KEY));
  const user         = ref<UserSummary | null>(null);
  const roles        = ref<string[]>([]);
  const permissions  = ref<string[]>([]);

  const isAuthenticated = computed(() => !!accessToken.value);

  function hasRole(code: string): boolean       { return roles.value.includes(code); }
  function hasPermission(code: string): boolean { return permissions.value.includes(code); }

  function setSession(t: TokenResponse): void {
    accessToken.value  = t.accessToken;
    refreshToken.value = t.refreshToken;
    user.value         = t.user;
    roles.value        = t.roles;
    permissions.value  = t.permissions;
    localStorage.setItem(ACCESS_KEY,  t.accessToken);
    localStorage.setItem(REFRESH_KEY, t.refreshToken);
  }

  function clearSession(): void {
    accessToken.value  = null;
    refreshToken.value = null;
    user.value         = null;
    roles.value        = [];
    permissions.value  = [];
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  }

  return {
    accessToken, refreshToken, user, roles, permissions,
    isAuthenticated, hasRole, hasPermission,
    setSession, clearSession,
  };
});
```

> localStorage 가 XSS 위험이 있지만 httpOnly cookie 도입은 별도 ADR. v2.1 MVP 는 localStorage 로 시작.

### 3. `lib/api.ts` 업데이트 — `readToken()` 정식 구현 + 401 핸들링

기존 stub:
```ts
function readToken(): string | null {
  // TODO(phase-2-auth): ...
  return null;
}
```

→ 변경:
```ts
import { useAuthStore } from '@/stores/useAuthStore';
import { useRouter } from 'vue-router';

function readToken(): string | null {
  // Pinia 가 setup 되어 있어야 함. app 부팅 후에는 정상.
  try {
    return useAuthStore().accessToken;
  } catch { return null; }
}

export const useApiFetch = createFetch({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  options: {
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
    onFetchError(ctx) {
      const data = ctx.data as { errorCode?: string; message?: string } | null;
      // 401 AUTH_REQUIRED 또는 토큰 만료 시 세션 클리어 + /login 리다이렉트
      const status = ctx.response?.status;
      if (status === 401) {
        try {
          useAuthStore().clearSession();
          // router 는 외부에서 setup 되어 있어야. setup 외부 호출 시 hash 직접 변경 fallback.
          if (typeof window !== 'undefined' && !location.pathname.startsWith('/login')) {
            const next = encodeURIComponent(location.pathname + location.search);
            location.href = `/login?next=${next}`;
          }
        } catch { /* ignore */ }
      }
      if (data?.errorCode) {
        ctx.error = new Error(`${data.errorCode}: ${data.message ?? ''}`.trim());
      }
      return ctx;
    },
  },
  fetchOptions: { mode: 'cors' },
});
```

> `useAuthStore()` 가 router setup 전에 호출되면 에러 — try/catch 로 보호.

### 4. `LoginPage.vue` — `src/pages/LoginPage.vue`

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { toast } from 'vue-sonner';
import { Card, CardHeader, CardTitle, CardContent, CardDescription }
  from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { useApiFetch } from '@/lib/api';
import { useAuthStore } from '@/stores/useAuthStore';
import type { ApiEnvelope } from '@/types/meta';
import type { TokenResponse } from '@/types/auth';

const username = ref('');
const password = ref('');
const isSubmitting = ref(false);
const router = useRouter();
const route  = useRoute();
const auth   = useAuthStore();

async function onSubmit() {
  if (!username.value || !password.value || isSubmitting.value) return;
  isSubmitting.value = true;
  try {
    const { data, statusCode, error } = await useApiFetch('/api/auth/login', {
      immediate: false, refetch: false,
    }).post({ username: username.value, password: password.value })
      .json<ApiEnvelope<TokenResponse>>();
    if (statusCode.value && statusCode.value >= 400) {
      toast.error('아이디 또는 비밀번호가 올바르지 않습니다.');
      return;
    }
    if (!data.value?.data) {
      toast.error('로그인에 실패했습니다.');
      return;
    }
    auth.setSession(data.value.data);
    toast.success(`${data.value.data.user.name}님 환영합니다.`);
    const next = (route.query.next as string | undefined) ?? '/';
    void router.replace(next);
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <main class="min-h-screen flex items-center justify-center bg-surface-muted p-4">
    <Card class="w-full max-w-md">
      <CardHeader>
        <CardTitle class="text-xl">Polestar10 ITG 로그인</CardTitle>
        <CardDescription>업무용 계정으로 로그인하세요.</CardDescription>
      </CardHeader>
      <CardContent>
        <form class="space-y-4" @submit.prevent="onSubmit">
          <div class="space-y-1">
            <Label for="login-username" class="text-sm font-medium">아이디</Label>
            <Input id="login-username" v-model="username" autocomplete="username"
                   placeholder="예: admin" />
          </div>
          <div class="space-y-1">
            <Label for="login-password" class="text-sm font-medium">비밀번호</Label>
            <Input id="login-password" v-model="password" type="password"
                   autocomplete="current-password" placeholder="비밀번호" />
          </div>
          <Button type="submit" class="w-full" :disabled="isSubmitting">
            {{ isSubmitting ? '로그인 중...' : '로그인' }}
          </Button>
        </form>
      </CardContent>
    </Card>
  </main>
</template>
```

### 5. 라우터 — `src/router/index.ts` 갱신

```ts
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/useAuthStore';
import { useApiFetch } from '@/lib/api';
import type { ApiEnvelope } from '@/types/meta';
import type { MeResponse } from '@/types/auth';

const routes: RouteRecordRaw[] = [
  // 로그인 (인증 불필요)
  { path: '/login', name: 'login', component: () => import('@/pages/LoginPage.vue'),
    meta: { public: true } },

  // 메인 영역 (인증 필요)
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      // 기존 children (HomePage·_DynamicRoute 등) 그대로 유지
      /* ... */
    ],
  },

  // _dev (인증 필요로 유지하거나 public 으로 — 본 step 은 인증 필요)

  { path: '/:pathMatch(.*)*', redirect: '/' },
];

const router = createRouter({ history: createWebHistory(), routes });

// 인증 가드
router.beforeEach(async (to) => {
  if (to.meta.public) return true;
  const auth = useAuthStore();
  if (!auth.isAuthenticated) {
    return { path: '/login', query: { next: to.fullPath } };
  }
  // user 가 비어있으면 /me 호출로 채우기 (새로고침 케이스)
  if (!auth.user) {
    try {
      const { data } = await useApiFetch('/api/auth/me')
        .json<ApiEnvelope<MeResponse>>();
      if (data.value?.data) {
        auth.user = data.value.data.user;
        auth.roles = data.value.data.roles;
        auth.permissions = data.value.data.permissions;
      }
    } catch { /* /me 실패 시 useApiFetch onFetchError 가 /login 리다이렉트 */ }
  }
  return true;
});

export default router;
```

> 일부 store 필드(`user`/`roles`/`permissions`) 를 가드에서 직접 set 하려면 Pinia store 의 reactive ref 가 노출되어야 함. setup store 의 return 객체 그대로 사용 OK.

### 6. `TopBar.vue` 갱신 — 로그인 사용자 표시 + 로그아웃 버튼

기존 placeholder 사용자명 자리에:
- `useAuthStore` 의 `user.name` (또는 username)
- 우측 끝에 "로그아웃" 버튼 → `auth.clearSession()` + `router.push('/login')`

### 7. 단위 테스트

`src/stores/__tests__/useAuthStore.spec.ts`:

1. `setSession_localStorage_저장_isAuthenticated_true`.
2. `clearSession_localStorage_제거_isAuthenticated_false`.
3. `hasRole·hasPermission_검사`.
4. `초기화_시_localStorage_의_토큰_복원`.

Vitest + happy-dom 환경 필요 (`vitest.config.ts` 의 `environment: 'happy-dom'` — phase 4 또는 본 step 에서 추가).

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 파일
test -f src/types/auth.ts
test -f src/stores/useAuthStore.ts
test -f src/pages/LoginPage.vue
test -f src/stores/__tests__/useAuthStore.spec.ts

# 2) lib/api.ts 갱신 — readToken 이 useAuthStore 호출
grep -q "useAuthStore" src/lib/api.ts

# 3) 라우터 가드
grep -q "router.beforeEach\|requiresAuth" src/router/index.ts

# 4) 정적·테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 5) dev 부팅 + /login 200
pnpm dev &
sleep 6
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/login)" = "200"
# 비인증 / → 본 step 에서는 SPA 가 200 응답 (라우터 가드는 브라우저 측)
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/)" = "200"
kill %1
```

수동 검증 (backend + 시드 살아있는 상태):
- 브라우저로 `/` 진입 → 라우터 가드가 `/login?next=/` 로 리다이렉트.
- admin / admin-sample-1234 입력 → 홈으로 이동.
- TopBar 에 `시스템 관리자` (또는 `admin`) 표시.
- 로그아웃 → /login 으로.

## 검증 절차

1. AC + 수동 검증 통과.
2. 아키텍처 체크:
   - 토큰이 Authorization 헤더에 자동 주입?
   - 401 시 자동 /login 리다이렉트 (loop 없이 — /login 경로 자체는 제외)?
   - 새로고침 후 localStorage 토큰 복원 + /me 로 user 채움?
   - LoginPage 디자인이 UI_GUIDE §5-3 카드 + §5-2 인풋 + Primary 버튼?
   - 로그인 실패 메시지가 한글·통합?
3. step 0 업데이트:
   - 성공 → `"summary": "useAuthStore (Pinia setup, localStorage access/refresh + user/roles/permissions, hasRole/hasPermission) + lib/api.ts readToken 실 구현 + 401 자동 /login 리다이렉트 + LoginPage.vue (카드 + 폼 + 토스트) + 라우터 가드(meta.requiresAuth + /me 자동 호출) + TopBar 로그인 사용자 + 로그아웃. 단위 테스트 4 케이스."`

## 금지사항

- 비밀번호를 localStorage·sessionStorage 에 저장 금지. 토큰만.
- 로그인 실패 시 "사용자 없음" / "비밀번호 틀림" 분리 메시지 금지 — 백엔드가 통합 메시지 반환.
- 라우터 가드가 무한 루프 만들지 마라 — `/login` 자체는 `requiresAuth: false`.
- LoginPage 에 자동 로그인 (cookie 기반) 도입 금지 — 본 phase 범위 밖.
- 백엔드 코드 수정 금지.
- 시스템 페이지(`/system/users` 등) 본 step 에서 만들지 마라 — step 2.
- 새로고침 후 store 가 안 비어있는데 /me 다시 호출 X (불필요한 fetch).
- 운영 코드 console.log 금지.
- localStorage 키 이름 규칙: `itg.auth.*` 외 키 추가 금지.
