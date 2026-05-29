import {
  computed,
  toValue,
  type MaybeRefOrGetter,
  type Ref,
} from 'vue';
import { useApiFetch } from '@/lib/api';
import { useDataMutation } from '@/composables/useDataMutation';
import { UI, mapErrorCode } from '@/lib/ui-messages';
import type { ApiEnvelope } from '@/types/meta';
import type {
  StepAction,
  WorkflowInstanceStepHistory,
  WorkflowInstanceView,
  WorkflowStatus,
  WorkflowStep,
} from '@/types/workflow';

/** 백엔드 WorkflowInstanceResponse — steps 는 정의가 아니라 실행 '이력'이다. */
interface WorkflowInstanceRaw {
  id: number;
  workflowDefCode: string;
  ticketId: number;
  currentStepIndex: number;
  status: WorkflowStatus;
  startedAt: string;
  completedAt: string | null;
  steps: WorkflowInstanceStepHistory[];
}

/** 백엔드 WorkflowDefinitionResponse — steps 는 JSONB 원본(snake_case key) 배열. */
interface WorkflowDefinitionRaw {
  code: string;
  steps: Record<string, unknown>[];
}

/**
 * 워크플로우 정의 단계(JSONB snake_case) → WorkflowStep(camelCase) 매핑.
 * 도메인-중립: 어떤 워크플로우든 동일 키(index/name/assignee_role_code/sla_minutes/allowed_actions)로 표현된다.
 */
export function mapDefinitionStep(raw: Record<string, unknown>): WorkflowStep {
  const allowed = Array.isArray(raw.allowed_actions) ? raw.allowed_actions : [];
  return {
    index: typeof raw.index === 'number' ? raw.index : Number(raw.index ?? 0),
    name: typeof raw.name === 'string' ? raw.name : '',
    assigneeRoleCode:
      typeof raw.assignee_role_code === 'string' ? raw.assignee_role_code : '',
    slaMinutes: typeof raw.sla_minutes === 'number' ? raw.sla_minutes : null,
    allowedActions: allowed.filter(
      (a): a is StepAction => typeof a === 'string',
    ),
  };
}

/** 인스턴스(이력 포함) + 정의 단계 → 화면용 통합 뷰. */
export function buildWorkflowView(
  instance: WorkflowInstanceRaw,
  defSteps: WorkflowStep[],
): WorkflowInstanceView {
  return {
    id: instance.id,
    workflowDefCode: instance.workflowDefCode,
    ticketId: instance.ticketId,
    currentStepIndex: instance.currentStepIndex,
    status: instance.status,
    startedAt: instance.startedAt,
    completedAt: instance.completedAt,
    steps: defSteps,
    history: instance.steps ?? [],
  };
}

/** 현재 진행 중인 단계 정의(없으면 null). */
export function currentStepOf(view: WorkflowInstanceView | null): WorkflowStep | null {
  if (!view) return null;
  return view.steps.find((s) => s.index === view.currentStepIndex) ?? null;
}

/**
 * 현재 단계에서 인증 사용자가 실행 가능한 액션 목록.
 * RUNNING 이고 현재 단계 assignee_role 을 보유한 사용자에게만 allowedActions 를 노출한다.
 * 그 외(종결 상태·역할 미보유)는 빈 배열 — 액션 버튼 미노출(금지사항: 전체 사용자 노출 금지).
 */
export function availableActions(
  view: WorkflowInstanceView | null,
  hasRole: (code: string) => boolean,
): StepAction[] {
  if (!view || view.status !== 'RUNNING') return [];
  const step = currentStepOf(view);
  if (!step) return [];
  if (!hasRole(step.assigneeRoleCode)) return [];
  return step.allowedActions;
}

export interface UseWorkflowResult {
  view: Ref<WorkflowInstanceView | null>;
  currentStep: Ref<WorkflowStep | null>;
  /** 해당 티켓에 워크플로우 인스턴스가 존재하는지 (구티켓 등은 false). */
  hasInstance: Ref<boolean>;
  isFetching: Ref<boolean>;
  error: Ref<string | null>;
  /** 액션 실행 실패 메시지(토스트용). */
  actionError: Ref<string | null>;
  reload: () => Promise<void>;
  executeAction: (
    stepIndex: number,
    action: StepAction,
    comment?: string,
  ) => Promise<boolean>;
}

/**
 * 티켓의 워크플로우 인스턴스 + 단계 정의 + 이력을 조회하고 단계 액션을 실행한다.
 * 인스턴스(by-ticket)와 정의(by-code) 두 번 fetch 하여 통합 뷰를 만든다.
 * - 인스턴스 404 = 워크플로우 미연결(구티켓 등) → 에러 아님(hasInstance=false, 패널 미노출).
 * - executeAction 성공 시 인스턴스를 재조회하여 현재 단계·이력을 갱신한다.
 */
export function useWorkflow(
  ticketId: MaybeRefOrGetter<number | string | null | undefined>,
): UseWorkflowResult {
  const instanceUrl = computed<string>(() => {
    const t = toValue(ticketId);
    if (t === null || t === undefined || t === '') return '';
    return `/api/workflow-instances/by-ticket/${t}`;
  });

  const {
    data: instData,
    statusCode: instStatus,
    error: instErr,
    isFetching: instFetching,
    execute: reloadInstance,
  } = useApiFetch(instanceUrl, { refetch: true }).json<
    ApiEnvelope<WorkflowInstanceRaw>
  >();

  const defCode = computed<string>(() => instData.value?.data?.workflowDefCode ?? '');
  const defUrl = computed<string>(() =>
    defCode.value
      ? `/api/workflow-definitions/${encodeURIComponent(defCode.value)}`
      : '',
  );
  const {
    data: defData,
    isFetching: defFetching,
    execute: reloadDef,
  } = useApiFetch(defUrl, { refetch: true }).json<
    ApiEnvelope<WorkflowDefinitionRaw>
  >();

  const view = computed<WorkflowInstanceView | null>(() => {
    const inst = instData.value?.data;
    if (!inst) return null;
    const rawSteps = defData.value?.data?.steps ?? [];
    const defSteps = rawSteps
      .map(mapDefinitionStep)
      .sort((a, b) => a.index - b.index);
    return buildWorkflowView(inst, defSteps);
  });

  const currentStep = computed<WorkflowStep | null>(() => currentStepOf(view.value));
  const hasInstance = computed<boolean>(() => !!instData.value?.data);
  const isFetching = computed<boolean>(() => instFetching.value || defFetching.value);

  const error = computed<string | null>(() => {
    // 404 = 해당 티켓에 워크플로우 인스턴스 없음 — 에러 아님(패널 미노출 처리).
    if (instStatus.value === 404) return null;
    if (instErr.value) {
      const code = instData.value?.errorCode;
      return code ? mapErrorCode(code) : UI.error.dataLoad;
    }
    return null;
  });

  const { error: actionError, submit } = useDataMutation<
    { action: StepAction; comment: string | null },
    WorkflowInstanceRaw
  >();

  async function reload(): Promise<void> {
    await reloadInstance();
    if (defCode.value) await reloadDef();
  }

  async function executeAction(
    stepIndex: number,
    action: StepAction,
    comment?: string,
  ): Promise<boolean> {
    const id = instData.value?.data?.id;
    if (id == null) return false;
    const path = `/api/workflow-instances/${id}/step/${stepIndex}/action`;
    const trimmed = comment?.trim();
    const result = await submit(path, {
      action,
      comment: trimmed ? trimmed : null,
    });
    if (result) {
      await reloadInstance();
      return true;
    }
    return false;
  }

  return {
    view,
    currentStep,
    hasInstance,
    isFetching,
    error,
    actionError,
    reload,
    executeAction,
  };
}
