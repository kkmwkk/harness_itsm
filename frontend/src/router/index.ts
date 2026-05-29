import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
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
        meta: { title: 'ITSM', groupId: 'itg-ticket' },
      },
      {
        path: 'itam',
        name: 'itam',
        component: () => import('@/pages/_DynamicRoute.vue'),
        meta: { title: 'ITAM', groupId: 'itg-asset' },
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

export default createRouter({
  history: createWebHistory(),
  routes,
});
