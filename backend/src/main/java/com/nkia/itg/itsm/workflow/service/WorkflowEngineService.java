package com.nkia.itg.itsm.workflow.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.itsm.requesttype.domain.StepAction;
import com.nkia.itg.itsm.ticket.entity.Ticket;
import com.nkia.itg.itsm.ticket.repository.TicketRepository;
import com.nkia.itg.itsm.workflow.entity.WorkflowDefinition;
import com.nkia.itg.itsm.workflow.entity.WorkflowInstance;
import com.nkia.itg.itsm.workflow.entity.WorkflowInstanceStep;
import com.nkia.itg.itsm.workflow.repository.WorkflowDefinitionRepository;
import com.nkia.itg.itsm.workflow.repository.WorkflowInstanceRepository;
import com.nkia.itg.itsm.workflow.repository.WorkflowInstanceStepRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워크플로우 엔진 (자체 단계 엔진 MVP — ADR-015).
 *
 * <p>단계 전이 매트릭스(ARCHITECTURE §15-2)는 {@link WorkflowInstance} 의 도메인 메서드
 * advance/reject/complete/reopen 에 박혀 있고, 본 Service 는 권한·상태 가드와
 * 단계 row 의 생성·종료(트랜잭션 경계)만 책임진다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class WorkflowEngineService {

    private static final String STEP_NAME = "name";
    private static final String STEP_ASSIGNEE_ROLE = "assignee_role_code";
    private static final String STEP_SLA_MINUTES = "sla_minutes";
    private static final String STEP_ALLOWED_ACTIONS = "allowed_actions";

    private final WorkflowDefinitionRepository defRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowInstanceStepRepository stepRepo;
    private final TicketRepository ticketRepo;

    /** 티켓 생성 시 호출 — request_type 의 default_workflow 로 인스턴스 시작 (current_step 0). */
    public WorkflowInstance start(Ticket ticket, String workflowDefCode) {
        WorkflowDefinition def = loadDefinition(workflowDefCode);
        if (def.stepCount() == 0) {
            throw new ITGException("INVALID_WORKFLOW_DEFINITION",
                    "워크플로우 정의에 단계가 없습니다: " + workflowDefCode);
        }
        WorkflowInstance instance = instanceRepo.save(WorkflowInstance.builder()
                .workflowDefCode(workflowDefCode)
                .ticketId(ticket.getId())
                .currentStepIndex(0)
                .build());
        stepRepo.save(newStepRow(instance.getId(), 0, def.stepAt(0)));
        ticket.linkWorkflowInstance(instance.getId());
        return instance;
    }

    /** 단계 액션 실행 — 현재 사용자가 단계의 assignee_role 을 보유했는지 검증 후 전이. */
    public WorkflowInstance executeAction(
            Long instanceId, int stepIndex, StepAction action,
            Long actorUserId, Set<String> actorRoles, String comment) {

        WorkflowInstance instance = instanceRepo.findById(instanceId)
                .orElseThrow(() -> new ITGException("WORKFLOW_INSTANCE_NOT_FOUND",
                        "워크플로우 인스턴스를 찾을 수 없습니다: " + instanceId, HttpStatus.NOT_FOUND));
        WorkflowDefinition def = loadDefinition(instance.getWorkflowDefCode());

        if (stepIndex != instance.getCurrentStepIndex()) {
            throw invalidAction("현재 단계가 아닙니다. 요청=" + stepIndex
                    + ", 현재=" + instance.getCurrentStepIndex());
        }
        Map<String, Object> stepDef = def.stepAt(stepIndex);
        if (stepDef == null) {
            throw invalidAction("단계 정의를 찾을 수 없습니다: index=" + stepIndex);
        }

        // 상태 가드: REOPEN 은 종결 상태에서만, 그 외 액션은 RUNNING 에서만.
        if (action == StepAction.REOPEN) {
            if (!instance.isTerminal()) {
                throw invalidAction("진행 중인 워크플로우는 재오픈할 수 없습니다.");
            }
        } else if (!instance.isRunning()) {
            throw invalidAction("종결된 워크플로우(" + instance.getStatus() + ")에는 액션할 수 없습니다.");
        }

        // 액션 허용·역할 가드.
        if (!allowedActions(stepDef).contains(action.name())) {
            throw invalidAction("현재 단계에서 허용되지 않은 액션입니다: " + action);
        }
        String requiredRole = assigneeRole(stepDef);
        if (requiredRole != null && (actorRoles == null || !actorRoles.contains(requiredRole))) {
            throw invalidAction("단계 담당 역할(" + requiredRole + ")이 없어 액션할 수 없습니다.");
        }

        // 현재 진행 중 단계 row 종료 (REOPEN 은 열린 row 가 없을 수 있어 null 허용).
        stepRepo.findByInstanceIdAndStepIndexAndCompletedAtIsNull(instanceId, stepIndex)
                .ifPresent(row -> row.finalizeStep(action, actorUserId, comment));

        applyTransition(instance, def, stepIndex, action);
        return instance;
    }

    /** SLA 초과 단계 조회 (배치 또는 polling 용). */
    @Transactional(readOnly = true)
    public List<WorkflowInstanceStep> findOverdue() {
        return stepRepo.findByCompletedAtIsNullAndSlaDueAtIsNotNullAndSlaDueAtBefore(
                LocalDateTime.now());
    }

    // ── 전이 적용 (매트릭스는 Entity 도메인 메서드에 위임) ────────────────────────

    private void applyTransition(WorkflowInstance instance, WorkflowDefinition def,
                                 int stepIndex, StepAction action) {
        switch (action) {
            case APPROVE, FORWARD, COMPLETE -> {
                if (def.isLastStep(stepIndex)) {
                    instance.complete();
                    closeTicket(instance);
                } else {
                    int next = stepIndex + 1;
                    instance.advance(next);
                    stepRepo.save(newStepRow(instance.getId(), next, def.stepAt(next)));
                }
            }
            case CONFIRM -> {
                instance.complete();
                closeTicket(instance);
            }
            case REJECT -> instance.reject();
            case REOPEN -> {
                instance.reopen();
                stepRepo.save(newStepRow(instance.getId(), 0, def.stepAt(0)));
            }
        }
    }

    private void closeTicket(WorkflowInstance instance) {
        ticketRepo.findById(instance.getTicketId()).ifPresent(Ticket::closeByWorkflow);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private WorkflowDefinition loadDefinition(String code) {
        return defRepo.findById(code)
                .orElseThrow(() -> new ITGException("WORKFLOW_DEF_NOT_FOUND",
                        "워크플로우 정의를 찾을 수 없습니다: " + code, HttpStatus.NOT_FOUND));
    }

    private WorkflowInstanceStep newStepRow(Long instanceId, int stepIndex, Map<String, Object> stepDef) {
        LocalDateTime now = LocalDateTime.now();
        Integer slaMinutes = slaMinutes(stepDef);
        return WorkflowInstanceStep.builder()
                .instanceId(instanceId)
                .stepIndex(stepIndex)
                .stepName(stepName(stepDef))
                .assigneeRole(assigneeRole(stepDef))
                .startedAt(now)
                .slaDueAt(slaMinutes != null ? now.plusMinutes(slaMinutes) : null)
                .build();
    }

    private ITGException invalidAction(String message) {
        return new ITGException("INVALID_WORKFLOW_ACTION", message, HttpStatus.BAD_REQUEST);
    }

    private String stepName(Map<String, Object> stepDef) {
        Object v = stepDef.get(STEP_NAME);
        return v != null ? v.toString() : "단계";
    }

    private String assigneeRole(Map<String, Object> stepDef) {
        Object v = stepDef.get(STEP_ASSIGNEE_ROLE);
        return v != null ? v.toString() : null;
    }

    private Integer slaMinutes(Map<String, Object> stepDef) {
        Object v = stepDef.get(STEP_SLA_MINUTES);
        return v instanceof Number n ? n.intValue() : null;
    }

    private List<String> allowedActions(Map<String, Object> stepDef) {
        Object v = stepDef.get(STEP_ALLOWED_ACTIONS);
        if (v instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }
}
