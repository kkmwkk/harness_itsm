import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/useAuthStore';
import { useMenuStore } from '@/stores/useMenuStore';
import { useApiFetch } from '@/lib/api';
import type { ApiEnvelope } from '@/types/meta';
import type { MeResponse } from '@/types/auth';

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/LoginPage.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/pages/HomePage.vue'),
        meta: { title: '대시보드' },
      },
      {
        path: 'itsm',
        name: 'itsm',
        component: () => import('@/pages/_DynamicRoute.vue'),
        meta: { title: 'ITSM', groupId: 'itg-ticket', detailUrlTemplate: '/itsm/{id}' },
      },
      {
        path: 'itsm/new/:requestType',
        name: 'itsm-new',
        component: () => import('@/pages/itsm/TicketNewPage.vue'),
        meta: { title: '티켓 등록' },
      },
      {
        path: 'itsm/:id(\\d+)',
        name: 'itsm-detail',
        component: () => import('@/pages/itsm/TicketDetailPage.vue'),
        meta: { title: '티켓 상세' },
      },
      {
        path: 'itam',
        name: 'itam',
        component: () => import('@/pages/_DynamicRoute.vue'),
        meta: { title: 'ITAM', groupId: 'itg-asset', detailUrlTemplate: '/itam/{id}' },
      },
      {
        path: 'itam/:id(\\d+)',
        name: 'itam-detail',
        component: () => import('@/pages/itam/DetailPage.vue'),
        meta: { title: '자산 상세' },
      },
      {
        path: 'pms',
        name: 'pms',
        component: () => import('@/pages/_DynamicRoute.vue'),
        meta: { title: 'PMS', groupId: 'itg-project' },
      },
      {
        path: 'common',
        name: 'common',
        component: () => import('@/pages/_DynamicRoute.vue'),
        meta: { title: '공통', groupId: 'itg-code' },
      },
      {
        path: 'system/meta',
        name: 'system-meta',
        component: () => import('@/pages/system/MetaPage.vue'),
        meta: { title: '시스템 / 메타 관리' },
      },
      {
        path: 'system/users',
        name: 'system-users',
        component: () => import('@/pages/system/UserPage.vue'),
        meta: { title: '사용자 관리' },
      },
      {
        path: 'system/depts',
        name: 'system-depts',
        component: () => import('@/pages/system/DeptPage.vue'),
        meta: { title: '부서 관리' },
      },
      {
        path: 'system/roles',
        name: 'system-roles',
        component: () => import('@/pages/system/RolePage.vue'),
        meta: { title: '역할 관리' },
      },
      {
        path: 'system/menus',
        name: 'system-menus',
        component: () => import('@/pages/system/MenuPage.vue'),
        meta: { title: '메뉴 관리' },
      },
    ],
  },
  {
    path: '/_dev/tokens',
    name: 'dev-tokens',
    component: () => import('@/views/_dev/TokenGallery.vue'),
  },
  {
    path: '/_dev/shadcn',
    name: 'dev-shadcn',
    component: () => import('@/views/_dev/ShadcnSampler.vue'),
  },
  {
    path: '/_dev/data-table',
    name: 'dev-data-table',
    component: () => import('@/views/_dev/DataTableSampler.vue'),
  },
  {
    path: '/_dev/ag-grid',
    name: 'dev-ag-grid',
    component: () => import('@/views/_dev/AgGridSampler.vue'),
  },
  {
    path: '/_dev/dynamic-grid',
    name: 'dev-dynamic-grid',
    component: () => import('@/views/_dev/DynamicGridSampler.vue'),
  },
  {
    path: '/_dev/dynamic-form',
    name: 'dev-dynamic-form',
    component: () => import('@/views/_dev/DynamicFormSampler.vue'),
  },
  {
    path: '/_dev/dynamic-page',
    name: 'dev-dynamic-page',
    component: () => import('@/views/_dev/DynamicPageSampler.vue'),
  },
  {
    path: '/_dev/badges',
    name: 'dev-badges',
    component: () => import('@/views/_dev/BadgeGallery.vue'),
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

/**
 * 인증 가드 (PRD §5-6 · ARCHITECTURE §8).
 * - public 라우트(`/login`)는 통과.
 * - 미인증이면 `/login?next=...` 으로 리다이렉트 (loop 방지 — /login 은 public).
 * - 인증됐지만 user 가 비어있으면(새로고침 케이스) /me 로 user/roles/permissions 복원.
 *   /me 실패(토큰 만료 등)는 useApiFetch onFetchError 가 /login 리다이렉트를 처리한다.
 */
router.beforeEach(async (to) => {
  if (to.meta.public) return true;
  const auth = useAuthStore();
  if (!auth.isAuthenticated) {
    return { path: '/login', query: { next: to.fullPath } };
  }
  if (!auth.user) {
    try {
      const { data } = await useApiFetch('/api/auth/me').json<
        ApiEnvelope<MeResponse>
      >();
      if (data.value?.data) {
        auth.user = data.value.data.user;
        auth.roles = data.value.data.roles;
        auth.permissions = data.value.data.permissions;
      }
    } catch {
      /* /me 실패 시 onFetchError 가 /login 리다이렉트 */
    }
  }
  // 로그인 직후·새로고침 복원 직후 1회만 메뉴 트리 로드 (네비게이션마다 재호출하지 않음).
  const menu = useMenuStore();
  if (menu.tree.length === 0 && !menu.isLoading) {
    await menu.load();
  }
  return true;
});

export default router;
