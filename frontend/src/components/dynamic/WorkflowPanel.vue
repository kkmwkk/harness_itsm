<script setup lang="ts">
import { computed, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { toast } from 'vue-sonner';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { useAuthStore } from '@/stores/useAuthStore';
import { useWorkflow, availableActions } from '@/composables/useWorkflow';
import { UI } from '@/lib/ui-messages';
import type { StepAction } from '@/types/workflow';

/**
 * 티켓의 워크플로우 진행 단계·현재 단계 액션·실행 이력을 표시하는 도메인-중립 패널(ADR-015).
 * 단계 구성은 전적으로 워크플로우 정의(steps 메타)에서 온다 — ticket/change 등 도메인 분기 없음.
 * 액션 버튼은 현재 단계 assignee_role 을 보유한(useAuthStore.hasRole) 사용자에게만 노출한다.
 */
interface Props {
  ticketId: number;
}
const props = defineProps<Props>();

// 액션 성공 시 상위 페이지가 티켓을 재조회하도록 트리거(grid·상세 reload).
const emit = defineEmits<{ acted: [] }>();

const auth = useAuthStore();
const { roles } = storeToRefs(auth);

const { view, currentStep, hasInstance, isFetching, error, actionError, executeAction } =
  useWorkflow(() => props.ticketId);

const comment = ref('');
const submitting = ref(false);

// roles 를 의존성에 포함해 로그인 사용자 변경 시 버튼 노출이 재계산되도록 한다.
const actions = computed<StepAction[]>(() => {
  void roles.value;
  return availableActions(view.value, auth.hasRole);
});

/** 부정 액션은 사유 입력을 권장 — comment 누락 시 클릭 사고를 줄인다(금지사항 반영). */
const COMMENT_RECOMMENDED: ReadonlyArray<StepAction> = ['REJECT', 'REOPEN'];

const ACTION_LABELS: Record<StepAction, string> = {
  APPROVE: '승인',
  REJECT: '반려',
  FORWARD: '전달',
  COMPLETE: '완료',
  CONFIRM: '확인 종결',
  REOPEN: '재오픈',
};

function actionLabel(a: StepAction): string {
  return ACTION_LABELS[a];
}
function actionVariant(a: StepAction): 'default' | 'destructive' | 'secondary' {
  if (a === 'REJECT') return 'destructive';
  if (a === 'FORWARD' || a === 'REOPEN') return 'secondary';
  return 'default';
}

const STATUS_LABELS: Record<string, string> = {
  RUNNING: '진행 중',
  COMPLETED: '완료',
  CANCELED: '취소',
  REJECTED: '반려',
};
function statusLabel(s: string | undefined): string {
  return s ? (STATUS_LABELS[s] ?? s) : '';
}
function statusColor(s: string | undefined): string {
  if (s === 'COMPLETED') return 'text-success';
  if (s === 'REJECTED' || s === 'CANCELED') return 'text-danger';
  return 'text-primary';
}

/** stepper 각 단계 색 — 완료(이전)·현재·예정. 종결이 비정상이면 현재 단계를 danger 로. */
function stepColor(index: number): string {
  const v = view.value;
  if (!v) return 'text-foreground-muted';
  if (index < v.currentStepIndex) return 'text-success';
  if (index === v.currentStepIndex) {
    return v.status === 'REJECTED' || v.status === 'CANCELED'
      ? 'text-danger'
      : 'text-primary';
  }
  return 'text-foreground-subtle';
}

// 현재 단계의 이력 레코드(SLA·시작 정보) — 미완료 우선.
const currentHistory = computed(() => {
  const v = view.value;
  if (!v) return null;
  return (
    v.history.find((h) => h.stepIndex === v.currentStepIndex && h.completedAt === null) ??
    v.history.find((h) => h.stepIndex === v.currentStepIndex) ??
    null
  );
});

// SLA 잔여(분) — 마감 미설정이면 null. 음수면 초과.
const slaRemainingMin = computed<number | null>(() => {
  const due = currentHistory.value?.slaDueAt;
  if (!due) return null;
  return Math.round((new Date(due).getTime() - Date.now()) / 60000);
});

async function onAction(action: StepAction): Promise<void> {
  const v = view.value;
  if (!v) return;
  if (COMMENT_RECOMMENDED.includes(action) && !comment.value.trim()) {
    toast.error('사유(코멘트)를 입력하세요.');
    return;
  }
  submitting.value = true;
  try {
    const ok = await executeAction(v.currentStepIndex, action, comment.value);
    if (ok) {
      toast.success('단계 액션이 처리되었습니다.');
      comment.value = '';
      emit('acted');
    } else {
      toast.error(actionError.value ?? UI.error.submit);
    }
  } finally {
    submitting.value = false;
  }
}

function fmt(dt: string | null): string {
  return dt ? dt.replace('T', ' ').slice(0, 16) : '-';
}
</script>

<template>
  <Card>
    <CardHeader>
      <CardTitle class="flex items-center gap-2">
        워크플로우 진행
        <span
          v-if="view"
          :class="['text-[12px] font-normal', statusColor(view.status)]"
        >
          · {{ statusLabel(view.status) }}
        </span>
      </CardTitle>
    </CardHeader>
    <CardContent class="space-y-6">
      <p
        v-if="isFetching && !view"
        class="text-foreground-muted text-sm"
      >
        {{ UI.loading.data }}
      </p>
      <p
        v-else-if="error"
        class="text-danger text-sm"
      >
        {{ error }}
      </p>
      <p
        v-else-if="!hasInstance"
        class="text-foreground-muted text-sm"
      >
        이 티켓에는 연결된 워크플로우가 없습니다.
      </p>

      <template v-else-if="view">
        <!-- 상단: 진행 단계 stepper -->
        <ol class="flex flex-wrap items-start gap-2">
          <li
            v-for="(s, i) in view.steps"
            :key="s.index"
            class="flex items-center gap-2"
          >
            <div class="flex flex-col items-center text-center min-w-20">
              <span
                :class="[
                  'flex h-8 w-8 items-center justify-center rounded-full border text-[13px] font-semibold',
                  stepColor(s.index),
                ]"
              >
                {{ s.index + 1 }}
              </span>
              <span
                :class="['mt-1 text-[12px] font-medium', stepColor(s.index)]"
              >{{ s.name }}</span>
              <span class="text-[11px] text-foreground-subtle">{{ s.assigneeRoleCode }}</span>
            </div>
            <span
              v-if="i < view.steps.length - 1"
              class="text-foreground-subtle"
            >→</span>
          </li>
        </ol>

        <!-- 중간: 현재 단계 정보 + 액션 -->
        <div
          v-if="currentStep"
          class="rounded-lg border border-border p-4 space-y-3"
        >
          <div class="flex flex-wrap items-center gap-x-6 gap-y-1 text-[13px]">
            <span>
              <span class="text-foreground-muted">현재 단계 </span>
              <span class="font-semibold">{{ currentStep.name }}</span>
            </span>
            <span>
              <span class="text-foreground-muted">담당 역할 </span>
              <span class="font-mono text-[12px]">{{ currentStep.assigneeRoleCode }}</span>
            </span>
            <span v-if="slaRemainingMin !== null">
              <span class="text-foreground-muted">SLA </span>
              <span :class="slaRemainingMin < 0 ? 'text-danger font-semibold' : 'text-foreground'">
                {{ slaRemainingMin >= 0 ? `잔여 ${slaRemainingMin}분` : `${-slaRemainingMin}분 초과` }}
              </span>
            </span>
          </div>

          <template v-if="view.status === 'RUNNING'">
            <Textarea
              v-model="comment"
              placeholder="처리 사유·코멘트 (반려·재오픈 시 필수)"
              class="min-h-20"
            />
            <div
              v-if="actions.length"
              class="flex flex-wrap gap-2"
            >
              <Button
                v-for="a in actions"
                :key="a"
                :variant="actionVariant(a)"
                :disabled="submitting"
                @click="onAction(a)"
              >
                {{ actionLabel(a) }}
              </Button>
            </div>
            <p
              v-else
              class="text-[12px] text-foreground-muted"
            >
              현재 단계 담당 역할이 아니어서 처리할 수 있는 액션이 없습니다.
            </p>
          </template>
          <p
            v-else
            class="text-[12px] text-foreground-muted"
          >
            종결된 워크플로우입니다 — 추가 액션이 없습니다.
          </p>
        </div>

        <!-- 하단: 단계 실행 이력 -->
        <div>
          <h3 class="text-[13px] font-semibold mb-2">
            단계 이력
          </h3>
          <table class="w-full text-[12px]">
            <thead>
              <tr class="border-b border-border text-left text-foreground-muted">
                <th class="py-1 pr-2 font-semibold">
                  단계
                </th>
                <th class="py-1 pr-2 font-semibold">
                  시작
                </th>
                <th class="py-1 pr-2 font-semibold">
                  종료
                </th>
                <th class="py-1 pr-2 font-semibold">
                  액션
                </th>
                <th class="py-1 pr-2 font-semibold">
                  실행자
                </th>
                <th class="py-1 font-semibold">
                  코멘트
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="h in view.history"
                :key="h.id"
                class="border-b border-border-subtle"
              >
                <td class="py-1 pr-2">
                  {{ h.stepName }}
                </td>
                <td class="py-1 pr-2 font-mono">
                  {{ fmt(h.startedAt) }}
                </td>
                <td class="py-1 pr-2 font-mono">
                  {{ fmt(h.completedAt) }}
                </td>
                <td class="py-1 pr-2">
                  {{ h.action ? actionLabel(h.action) : '-' }}
                </td>
                <td class="py-1 pr-2">
                  {{ h.actionByUserId ?? '-' }}
                </td>
                <td class="py-1 text-foreground-muted">
                  {{ h.actionComment ?? '-' }}
                </td>
              </tr>
              <tr v-if="view.history.length === 0">
                <td
                  colspan="6"
                  class="py-2 text-foreground-muted"
                >
                  이력이 없습니다.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </CardContent>
  </Card>
</template>
