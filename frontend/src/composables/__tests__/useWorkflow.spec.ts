import { describe, it, expect } from 'vitest';
import {
  mapDefinitionStep,
  buildWorkflowView,
  currentStepOf,
  availableActions,
} from '@/composables/useWorkflow';
import type { WorkflowInstanceView } from '@/types/workflow';

/** 가상 샘플만 사용한다(ADR-011). */

describe('mapDefinitionStep', () => {
  it('snake_case_JSONB_를_camelCase_WorkflowStep_으로_매핑', () => {
    const step = mapDefinitionStep({
      index: 1,
      name: '1차 검토',
      assignee_role_code: 'ROLE_TEAM_LEAD',
      sla_minutes: 240,
      allowed_actions: ['APPROVE', 'REJECT'],
    });
    expect(step).toEqual({
      index: 1,
      name: '1차 검토',
      assigneeRoleCode: 'ROLE_TEAM_LEAD',
      slaMinutes: 240,
      allowedActions: ['APPROVE', 'REJECT'],
    });
  });

  it('sla_minutes_null_과_누락_필드_방어', () => {
    const step = mapDefinitionStep({ name: '종결 확인', assignee_role_code: 'ROLE_REQUESTER' });
    expect(step.slaMinutes).toBeNull();
    expect(step.index).toBe(0);
    expect(step.allowedActions).toEqual([]);
  });
});

describe('buildWorkflowView · currentStepOf', () => {
  const defSteps = [
    { index: 0, name: '접수', assigneeRoleCode: 'ROLE_IT_SUPPORT', slaMinutes: 60, allowedActions: ['FORWARD', 'COMPLETE'] as const },
    { index: 1, name: '1차 검토', assigneeRoleCode: 'ROLE_TEAM_LEAD', slaMinutes: 240, allowedActions: ['APPROVE', 'REJECT'] as const },
  ].map((s) => ({ ...s, allowedActions: [...s.allowedActions] }));

  const instance = {
    id: 100,
    workflowDefCode: 'WF_INCIDENT_STD',
    ticketId: 42,
    currentStepIndex: 1,
    status: 'RUNNING' as const,
    startedAt: '2026-05-29T10:00:00',
    completedAt: null,
    steps: [
      {
        id: 1, stepIndex: 0, stepName: '접수', assigneeRole: 'ROLE_IT_SUPPORT',
        assignedToUserId: null, startedAt: '2026-05-29T10:00:00', completedAt: '2026-05-29T10:30:00',
        slaDueAt: null, action: 'FORWARD' as const, actionByUserId: 5, actionComment: '전달',
      },
    ],
  };

  it('인스턴스_이력_history_와_정의_steps_를_합쳐_뷰_구성', () => {
    const view = buildWorkflowView(instance, defSteps);
    expect(view.steps).toHaveLength(2);
    expect(view.history).toHaveLength(1);
    expect(view.currentStepIndex).toBe(1);
  });

  it('currentStepOf_는_currentStepIndex_단계_반환', () => {
    const view = buildWorkflowView(instance, defSteps);
    expect(currentStepOf(view)?.name).toBe('1차 검토');
  });

  it('currentStepOf_null_뷰_방어', () => {
    expect(currentStepOf(null)).toBeNull();
  });
});

describe('availableActions — 권한 분기', () => {
  const view: WorkflowInstanceView = {
    id: 100,
    workflowDefCode: 'WF_INCIDENT_STD',
    ticketId: 42,
    currentStepIndex: 1,
    status: 'RUNNING',
    startedAt: '2026-05-29T10:00:00',
    completedAt: null,
    steps: [
      { index: 0, name: '접수', assigneeRoleCode: 'ROLE_IT_SUPPORT', slaMinutes: 60, allowedActions: ['FORWARD'] },
      { index: 1, name: '1차 검토', assigneeRoleCode: 'ROLE_TEAM_LEAD', slaMinutes: 240, allowedActions: ['APPROVE', 'REJECT'] },
    ],
    history: [],
  };

  it('현재_단계_역할_보유자는_allowedActions_노출', () => {
    const actions = availableActions(view, (code) => code === 'ROLE_TEAM_LEAD');
    expect(actions).toEqual(['APPROVE', 'REJECT']);
  });

  it('역할_미보유자는_빈_배열_액션_미노출', () => {
    const actions = availableActions(view, (code) => code === 'ROLE_IT_SUPPORT');
    expect(actions).toEqual([]);
  });

  it('종결_상태는_역할_보유여도_빈_배열', () => {
    const closed: WorkflowInstanceView = { ...view, status: 'COMPLETED' };
    const actions = availableActions(closed, () => true);
    expect(actions).toEqual([]);
  });

  it('null_뷰는_빈_배열', () => {
    expect(availableActions(null, () => true)).toEqual([]);
  });
});
