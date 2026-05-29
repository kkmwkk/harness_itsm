<script setup lang="ts">
import {
  TicketCheckIcon,
  BoxesIcon,
  FolderKanbanIcon,
  LayersIcon,
  DatabaseIcon,
} from '@lucide/vue';
import type { FunctionalComponent } from 'vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';

type ModuleCard = {
  to: string;
  title: string;
  description: string;
  icon: FunctionalComponent;
  /** PUBLISHED 메타가 시드되어 DynamicPage 가 즉시 동작하는 모듈. */
  ready: boolean;
};

const modules: ModuleCard[] = [
  {
    to: '/itsm',
    title: 'ITSM',
    description: '티켓·변경·문제·SLA 워크플로우',
    icon: TicketCheckIcon,
    ready: true,
  },
  {
    to: '/itam',
    title: 'ITAM',
    description: '자산원장·라이선스·계약 이력',
    icon: BoxesIcon,
    ready: true,
  },
  {
    to: '/pms',
    title: 'PMS',
    description: '프로젝트·태스크·일정 관리',
    icon: FolderKanbanIcon,
    ready: false,
  },
  {
    to: '/common',
    title: '공통',
    description: '코드 관리·공지·첨부',
    icon: LayersIcon,
    ready: false,
  },
  {
    to: '/system/meta',
    title: '시스템 / 메타 관리',
    description: 'PageMeta 버전 그룹 관리',
    icon: DatabaseIcon,
    ready: true,
  },
];
</script>

<template>
  <section>
    <PageHeader />
    <p class="mb-6 text-sm text-foreground-muted">
      신규 화면은 Vue 파일을 작성하지 않고 page_meta 한 건만 추가하여 자동 생성한다.
    </p>
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <RouterLink
        v-for="m in modules"
        :key="m.to"
        :to="m.to"
        class="block rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <Card class="h-full transition-colors hover:bg-surface-hover">
          <CardHeader>
            <div class="flex items-start gap-3">
              <span
                class="inline-flex h-9 w-9 items-center justify-center rounded-md bg-surface-muted text-primary"
              >
                <component
                  :is="m.icon"
                  class="h-5 w-5"
                  :stroke-width="1.5"
                />
              </span>
              <div>
                <CardTitle>{{ m.title }}</CardTitle>
                <CardDescription>{{ m.description }}</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <p
              :class="[
                'text-xs',
                m.ready ? 'text-success' : 'text-foreground-subtle',
              ]"
            >
              <span v-if="m.ready">● 메타 PUBLISHED — 화면 자동 생성 중</span>
              <span v-else>○ 메타 미배포 — DRAFT 생성 후 publish 필요</span>
            </p>
          </CardContent>
        </Card>
      </RouterLink>
    </div>
  </section>
</template>
