/**
 * 워크플로우(자체 단계 엔진 MVP — ADR-015) 프론트 타입.
 * 백엔드 계약과 1:1:
 * - 단계 정의: GET /api/workflow-definitions/{code} 의 steps(JSONB, snake_case) → WorkflowStep(camelCase)
 * - 인스턴스 + 이력: GET /api/workflow-instances/by-ticket/{ticketId} 의 WorkflowInstanceResponse
 * useWorkflow 가 두 응답을 합쳐 WorkflowInstanceView 로 노출한다(steps=정의 스냅샷, history=실행 이력).
 */
export type StepAction = 'APPROVE' | 'REJECT' | 'FORWARD' | 'COMPLETE' | 'CONFIRM' | 'REOPEN';
export type WorkflowStatus = 'RUNNING' | 'COMPLETED' | 'CANCELED' | 'REJECTED';

export interface WorkflowStep {
  index: number;
  name: string;
  assigneeRoleCode: string;
  slaMinutes: number | null;
  allowedActions: StepAction[];
}

export interface WorkflowInstanceStepHistory {
  id: number;
  stepIndex: number;
  stepName: string;
  assigneeRole: string | null;
  assignedToUserId: number | null;
  startedAt: string;
  completedAt: string | null;
  slaDueAt: string | null;
  action: StepAction | null;
  actionByUserId: number | null;
  actionComment: string | null;
}

export interface WorkflowInstanceView {
  id: number;
  workflowDefCode: string;
  ticketId: number;
  currentStepIndex: number;
  status: WorkflowStatus;
  startedAt: string;
  completedAt: string | null;
  steps: WorkflowStep[]; // definition 의 steps 스냅샷
  history: WorkflowInstanceStepHistory[];
}
