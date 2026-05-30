<script setup lang="ts">
/**
 * PMS 프로젝트 목록 + 간트 (phase 16 step 5).
 * GET /api/projects(인증) 로 프로젝트(태스크 포함)를 불러와, 프로젝트 그리드와 간트 차트를 그린다.
 * 프로젝트를 선택하면 간트가 그 프로젝트 + 하위 태스크 행으로 전환된다.
 * 백엔드 미기동(또는 미배포) 시에는 친화 빈/에러 상태로 안전하게 렌더(UX 카탈로그, ADR-020).
 */
import { computed, ref } from 'vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import GanttChart from '@/components/dataviz/GanttChart.vue';
import { useApiFetch } from '@/lib/api';
import { UI } from '@/lib/ui-messages';
import type { ApiEnvelope } from '@/types/meta';
import type { GanttBar } from '@/lib/gantt';
import type { ProjectStatus, ProjectSummary, TaskStatus } from '@/types/pms';

const {
  data: env,
  isFetching,
  error,
} = useApiFetch<ApiEnvelope<ProjectSummary[]>>('/api/projects', { refetch: true }).json<
  ApiEnvelope<ProjectSummary[]>
>();

const projects = computed<ProjectSummary[]>(() => env.value?.data ?? []);

const selectedId = ref<number | null>(null);
const selected = computed<ProjectSummary | null>(
  () => projects.value.find((p) => p.id === selectedId.value) ?? null,
);

function selectProject(id: number): void {
  selectedId.value = selectedId.value === id ? null : id;
}

// 유효한 기간(start·end 둘 다)이 있는 항목만 간트 막대로 — 날짜 없는 행은 그리드에만 표시.
function toBar(
  id: string,
  label: string,
  start: string | null,
  end: string | null,
  progress: number,
  category?: string,
): GanttBar | null {
  if (!start || !end) return null;
  return { id, label, start, end, progress, category };
}

const bars = computed<GanttBar[]>(() => {
  if (selected.value) {
    const p = selected.value;
    const out: GanttBar[] = [];
    const projBar = toBar(`p-${p.id}`, p.name, p.startDate, p.dueDate, p.progress, '프로젝트');
    if (projBar) out.push(projBar);
    for (const t of p.tasks) {
      const tb = toBar(`t-${t.id}`, t.title, t.startDate, t.dueDate, t.progress, p.name);
      if (tb) out.push(tb);
    }
    return out;
  }
  return projects.value
    .map((p) => toBar(`p-${p.id}`, p.name, p.startDate, p.dueDate, p.progress, '프로젝트'))
    .filter((b): b is GanttBar => b !== null);
});

const PROJECT_STATUS: Record<ProjectStatus, { label: string; chip: string }> = {
  PLANNED: { label: '계획', chip: 'bg-info/10 text-info' },
  IN_PROGRESS: { label: '진행 중', chip: 'bg-primary/10 text-primary' },
  DONE: { label: '완료', chip: 'bg-success/10 text-success' },
  ON_HOLD: { label: '보류', chip: 'bg-warning/10 text-warning' },
};
const TASK_STATUS: Record<TaskStatus, string> = {
  TODO: '대기',
  IN_PROGRESS: '진행 중',
  DONE: '완료',
};

function projectStatus(s: ProjectStatus): { label: string; chip: string } {
  return PROJECT_STATUS[s] ?? { label: s, chip: 'bg-neutral/10 text-neutral' };
}
function taskStatusLabel(s: TaskStatus): string {
  return TASK_STATUS[s] ?? s;
}
function period(start: string | null, due: string | null): string {
  if (!start && !due) return '-';
  return `${start ?? '?'} ~ ${due ?? '?'}`;
}
</script>

<template>
  <section class="space-y-4">
    <PageHeader title="PMS / 프로젝트" />

    <p
      v-if="isFetching && projects.length === 0"
      class="text-foreground-muted"
    >
      {{ UI.loading.data }}
    </p>
    <Card
      v-else-if="error"
      class="border-danger"
    >
      <CardContent class="py-6 text-danger">
        {{ error.message ?? UI.error.dataLoad }}
      </CardContent>
    </Card>

    <template v-else>
      <Card v-if="projects.length === 0">
        <CardContent class="py-12 text-center text-foreground-muted">
          아직 등록된 프로젝트가 없습니다.
        </CardContent>
      </Card>

      <template v-else>
        <!-- 간트 -->
        <Card>
          <CardHeader class="flex flex-row items-center justify-between space-y-0">
            <CardTitle>
              일정 간트
              <span class="ml-2 text-[13px] font-normal text-foreground-muted">
                {{ selected ? `${selected.name} · 태스크 ${selected.tasks.length}` : `전체 프로젝트 ${projects.length}` }}
              </span>
            </CardTitle>
            <Button
              v-if="selected"
              size="sm"
              variant="outline"
              @click="selectedId = null"
            >
              전체 보기
            </Button>
          </CardHeader>
          <CardContent>
            <GanttChart
              v-if="bars.length > 0"
              :bars="bars"
            />
            <p
              v-else
              class="py-8 text-center text-sm text-foreground-muted"
            >
              표시할 일정(시작·종료일)이 없습니다.
            </p>
          </CardContent>
        </Card>

        <!-- 프로젝트 그리드 -->
        <Card>
          <CardHeader>
            <CardTitle>프로젝트 목록</CardTitle>
          </CardHeader>
          <CardContent>
            <table class="w-full text-[13px]">
              <thead>
                <tr class="border-b border-border text-left text-[13px] font-semibold text-foreground-muted">
                  <th class="px-2 py-2">
                    코드
                  </th>
                  <th class="px-2 py-2">
                    프로젝트명
                  </th>
                  <th class="px-2 py-2">
                    상태
                  </th>
                  <th class="px-2 py-2">
                    기간
                  </th>
                  <th class="px-2 py-2 text-right">
                    진행률
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="p in projects"
                  :key="p.id"
                  class="cursor-pointer border-b border-border-subtle transition-colors last:border-b-0 hover:bg-surface-hover"
                  :class="{ 'bg-surface-selected': p.id === selectedId }"
                  @click="selectProject(p.id)"
                >
                  <td class="px-2 py-2 font-mono text-[12px]">
                    {{ p.code }}
                  </td>
                  <td class="px-2 py-2 text-foreground">
                    {{ p.name }}
                  </td>
                  <td class="px-2 py-2">
                    <span
                      class="inline-flex items-center rounded-pill px-2.5 py-0.5 text-[12px] font-semibold"
                      :class="projectStatus(p.status).chip"
                    >
                      {{ projectStatus(p.status).label }}
                    </span>
                  </td>
                  <td class="px-2 py-2 font-mono text-[12px] text-foreground-muted">
                    {{ period(p.startDate, p.dueDate) }}
                  </td>
                  <td class="px-2 py-2 text-right tabular-nums">
                    {{ p.progress }}%
                  </td>
                </tr>
              </tbody>
            </table>
          </CardContent>
        </Card>

        <!-- 선택 프로젝트의 태스크 목록 -->
        <Card v-if="selected">
          <CardHeader>
            <CardTitle>태스크 — {{ selected.name }}</CardTitle>
          </CardHeader>
          <CardContent>
            <p
              v-if="selected.tasks.length === 0"
              class="text-sm text-foreground-muted"
            >
              등록된 태스크가 없습니다.
            </p>
            <ul
              v-else
              class="space-y-2"
            >
              <li
                v-for="t in selected.tasks"
                :key="t.id"
                class="flex items-center justify-between gap-3 border-b border-border-subtle pb-2 text-[13px] last:border-b-0 last:pb-0"
              >
                <span class="min-w-0 truncate text-foreground">{{ t.title }}</span>
                <span class="flex shrink-0 items-center gap-3 text-foreground-muted">
                  <span class="font-mono text-[12px]">{{ period(t.startDate, t.dueDate) }}</span>
                  <span>{{ taskStatusLabel(t.status) }}</span>
                  <span class="tabular-nums">{{ t.progress }}%</span>
                </span>
              </li>
            </ul>
          </CardContent>
        </Card>
      </template>
    </template>
  </section>
</template>
