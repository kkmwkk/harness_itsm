package com.nkia.itg.itsm.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.itsm.requesttype.domain.StepAction;
import com.nkia.itg.itsm.ticket.domain.Priority;
import com.nkia.itg.itsm.ticket.domain.TicketStatus;
import com.nkia.itg.itsm.ticket.entity.Ticket;
import com.nkia.itg.itsm.ticket.repository.TicketRepository;
import com.nkia.itg.itsm.workflow.domain.WorkflowStatus;
import com.nkia.itg.itsm.workflow.entity.WorkflowDefinition;
import com.nkia.itg.itsm.workflow.entity.WorkflowInstance;
import com.nkia.itg.itsm.workflow.entity.WorkflowInstanceStep;
import com.nkia.itg.itsm.workflow.repository.WorkflowDefinitionRepository;
import com.nkia.itg.itsm.workflow.repository.WorkflowInstanceRepository;
import com.nkia.itg.itsm.workflow.repository.WorkflowInstanceStepRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowEngineService 단위 테스트 (Mockito)")
class WorkflowEngineServiceTest {

    @Mock
    private WorkflowDefinitionRepository defRepo;
    @Mock
    private WorkflowInstanceRepository instanceRepo;
    @Mock
    private WorkflowInstanceStepRepository stepRepo;
    @Mock
    private TicketRepository ticketRepo;
    @Mock
    private com.nkia.itg.system.notification.service.NotificationService notificationService;

    @InjectMocks
    private WorkflowEngineService engine;

    private static final String WF = "WF_INCIDENT_STD";

    private Map<String, Object> step(int index, String name, String role,
                                     Integer sla, List<String> actions) {
        return Map.of(
                "index", index,
                "name", name,
                "assignee_role_code", role,
                "sla_minutes", sla == null ? "" : sla,
                "allowed_actions", actions);
    }

    private WorkflowDefinition def(List<Map<String, Object>> steps) {
        return WorkflowDefinition.builder()
                .code(WF).name("장애 표준").version(1).steps(steps).active(true).build();
    }

    private WorkflowInstance instance(int currentStep, WorkflowStatus status) {
        return WorkflowInstance.builder()
                .id(100L).workflowDefCode(WF).ticketId(1L)
                .currentStepIndex(currentStep).status(status)
                .startedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("start — request_type 의 workflow 로 인스턴스 생성, current_step 0, 첫 step row 추가")
    void start_인스턴스_생성_current_step_0_첫_step_row() {
        // given
        WorkflowDefinition def = def(List.of(
                step(0, "접수", "ROLE_IT_SUPPORT", 60, List.of("FORWARD", "COMPLETE")),
                step(1, "검토", "ROLE_TEAM_LEAD", 240, List.of("APPROVE", "REJECT"))));
        when(defRepo.findById(WF)).thenReturn(Optional.of(def));
        when(instanceRepo.save(any(WorkflowInstance.class))).thenReturn(instance(0, WorkflowStatus.RUNNING));
        Ticket ticket = Ticket.builder().id(1L).title("샘플").priority(Priority.MEDIUM)
                .status(TicketStatus.OPEN).build();

        // when
        WorkflowInstance result = engine.start(ticket, WF);

        // then
        assertThat(result.getCurrentStepIndex()).isEqualTo(0);
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(ticket.getWorkflowInstanceId()).isEqualTo(100L);
        ArgumentCaptor<WorkflowInstanceStep> captor = ArgumentCaptor.forClass(WorkflowInstanceStep.class);
        verify(stepRepo).save(captor.capture());
        WorkflowInstanceStep first = captor.getValue();
        assertThat(first.getStepIndex()).isEqualTo(0);
        assertThat(first.getStepName()).isEqualTo("접수");
        assertThat(first.getAssigneeRole()).isEqualTo("ROLE_IT_SUPPORT");
        assertThat(first.getSlaDueAt()).isNotNull();
    }

    @Test
    @DisplayName("executeAction APPROVE — 다음 단계 전이, 다음 step row 추가")
    void executeAction_APPROVE_다음_단계_전이_step_row() {
        // given
        WorkflowDefinition def = def(List.of(
                step(0, "1차 검토", "ROLE_TEAM_LEAD", 240, List.of("APPROVE", "REJECT")),
                step(1, "변경 적용", "ROLE_IT_SUPPORT", 480, List.of("COMPLETE"))));
        when(defRepo.findById(WF)).thenReturn(Optional.of(def));
        when(instanceRepo.findById(100L)).thenReturn(Optional.of(instance(0, WorkflowStatus.RUNNING)));

        // when
        WorkflowInstance result = engine.executeAction(
                100L, 0, StepAction.APPROVE, 5L, Set.of("ROLE_TEAM_LEAD"), "승인");

        // then
        assertThat(result.getCurrentStepIndex()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        ArgumentCaptor<WorkflowInstanceStep> captor = ArgumentCaptor.forClass(WorkflowInstanceStep.class);
        verify(stepRepo).save(captor.capture());
        assertThat(captor.getValue().getStepIndex()).isEqualTo(1);
        assertThat(captor.getValue().getStepName()).isEqualTo("변경 적용");
    }

    @Test
    @DisplayName("executeAction REJECT — status REJECTED 종결")
    void executeAction_REJECT_status_REJECTED_종결() {
        // given
        WorkflowDefinition def = def(List.of(
                step(0, "1차 검토", "ROLE_TEAM_LEAD", 240, List.of("APPROVE", "REJECT"))));
        when(defRepo.findById(WF)).thenReturn(Optional.of(def));
        when(instanceRepo.findById(100L)).thenReturn(Optional.of(instance(0, WorkflowStatus.RUNNING)));

        // when
        WorkflowInstance result = engine.executeAction(
                100L, 0, StepAction.REJECT, 5L, Set.of("ROLE_TEAM_LEAD"), "반려");

        // then
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.REJECTED);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("executeAction — assignee_role 없는 사용자는 INVALID_WORKFLOW_ACTION (400)")
    void executeAction_assignee_role_없는_사용자_INVALID_400() {
        // given
        WorkflowDefinition def = def(List.of(
                step(0, "1차 검토", "ROLE_TEAM_LEAD", 240, List.of("APPROVE", "REJECT"))));
        when(defRepo.findById(WF)).thenReturn(Optional.of(def));
        when(instanceRepo.findById(100L)).thenReturn(Optional.of(instance(0, WorkflowStatus.RUNNING)));

        // when & then — actorRoles 에 ROLE_TEAM_LEAD 없음
        assertThatThrownBy(() -> engine.executeAction(
                100L, 0, StepAction.APPROVE, 9L, Set.of("ROLE_USER"), null))
                .isInstanceOf(ITGException.class)
                .satisfies(ex -> {
                    ITGException itg = (ITGException) ex;
                    assertThat(itg.getErrorCode()).isEqualTo("INVALID_WORKFLOW_ACTION");
                    assertThat(itg.getHttpStatus().value()).isEqualTo(400);
                });
    }

    @Test
    @DisplayName("executeAction — COMPLETED 인스턴스 재호출 거부 (INVALID_WORKFLOW_ACTION)")
    void executeAction_COMPLETED_인스턴스_재호출_거부() {
        // given
        WorkflowDefinition def = def(List.of(
                step(0, "변경 적용", "ROLE_IT_SUPPORT", 480, List.of("COMPLETE"))));
        when(defRepo.findById(WF)).thenReturn(Optional.of(def));
        when(instanceRepo.findById(100L)).thenReturn(Optional.of(instance(0, WorkflowStatus.COMPLETED)));

        // when & then
        assertThatThrownBy(() -> engine.executeAction(
                100L, 0, StepAction.COMPLETE, 5L, Set.of("ROLE_IT_SUPPORT"), null))
                .isInstanceOf(ITGException.class)
                .satisfies(ex -> assertThat(((ITGException) ex).getErrorCode())
                        .isEqualTo("INVALID_WORKFLOW_ACTION"));
    }

    @Test
    @DisplayName("executeAction 마지막 단계 COMPLETE — status COMPLETED + Ticket CLOSED")
    void executeAction_마지막_단계_COMPLETE_status_COMPLETED_Ticket_CLOSED() {
        // given — 단일 단계 = 마지막 단계
        WorkflowDefinition def = def(List.of(
                step(0, "변경 적용", "ROLE_IT_SUPPORT", 480, List.of("COMPLETE"))));
        when(defRepo.findById(WF)).thenReturn(Optional.of(def));
        when(instanceRepo.findById(100L)).thenReturn(Optional.of(instance(0, WorkflowStatus.RUNNING)));
        Ticket ticket = Ticket.builder().id(1L).title("샘플").priority(Priority.MEDIUM)
                .status(TicketStatus.IN_PROGRESS).build();
        when(ticketRepo.findById(1L)).thenReturn(Optional.of(ticket));

        // when
        WorkflowInstance result = engine.executeAction(
                100L, 0, StepAction.COMPLETE, 5L, Set.of("ROLE_IT_SUPPORT"), "완료");

        // then
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("executeAction REOPEN — CLOSED(종결) 재오픈, current_step 0, RUNNING")
    void executeAction_REOPEN_재오픈_current_step_0() {
        // given — 종결(COMPLETED) 인스턴스, 마지막 단계가 REOPEN 허용
        WorkflowDefinition def = def(List.of(
                step(0, "종결 확인", "ROLE_REQUESTER", null, List.of("CONFIRM", "REOPEN"))));
        when(defRepo.findById(WF)).thenReturn(Optional.of(def));
        when(instanceRepo.findById(100L)).thenReturn(Optional.of(instance(0, WorkflowStatus.COMPLETED)));

        // when
        WorkflowInstance result = engine.executeAction(
                100L, 0, StepAction.REOPEN, 7L, Set.of("ROLE_REQUESTER"), "재오픈");

        // then
        assertThat(result.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(result.getCurrentStepIndex()).isEqualTo(0);
        assertThat(result.getCompletedAt()).isNull();
        verify(stepRepo).save(any(WorkflowInstanceStep.class));
    }

    @Test
    @DisplayName("findOverdue — SLA 초과 단계 위임 조회")
    void findOverdue_위임_조회() {
        // given
        WorkflowInstanceStep overdue = WorkflowInstanceStep.builder()
                .instanceId(100L).stepIndex(0).stepName("접수")
                .startedAt(LocalDateTime.now().minusHours(2))
                .slaDueAt(LocalDateTime.now().minusHours(1)).build();
        when(stepRepo.findByCompletedAtIsNullAndSlaDueAtIsNotNullAndSlaDueAtBefore(any()))
                .thenReturn(List.of(overdue));

        // when
        List<WorkflowInstanceStep> result = engine.findOverdue();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStepName()).isEqualTo("접수");
    }
}
