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
        component: () => import('@/pages/itsm/IndexPage.vue'),
        meta: { title: 'ITSM' },
      },
      {
        path: 'itam',
        name: 'itam',
        component: () => import('@/pages/itam/IndexPage.vue'),
        meta: { title: 'ITAM' },
      },
      {
        path: 'pms',
        name: 'pms',
        component: () => import('@/pages/pms/IndexPage.vue'),
        meta: { title: 'PMS' },
      },
      {
        path: 'common',
        name: 'common',
        component: () => import('@/pages/common/IndexPage.vue'),
        meta: { title: '공통' },
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
  { path: '/:pathMatch(.*)*', redirect: '/' },
];

export default createRouter({
  history: createWebHistory(),
  routes,
});
