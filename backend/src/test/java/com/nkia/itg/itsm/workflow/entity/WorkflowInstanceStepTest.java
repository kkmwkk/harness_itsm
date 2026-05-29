package com.nkia.itg.itsm.workflow.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nkia.itg.itsm.requesttype.domain.StepAction;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WorkflowInstanceStep 도메인 메서드")
class WorkflowInstanceStepTest {

    private WorkflowInstanceStep openStep() {
        return WorkflowInstanceStep.builder()
                .instanceId(1L).stepIndex(0).stepName("접수")
                .assigneeRole("ROLE_IT_SUPPORT")
                .startedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("finalizeStep — action·실행자·코멘트·완료시각 기록")
    void finalizeStep_기록() {
        WorkflowInstanceStep s = openStep();
        s.finalizeStep(StepAction.COMPLETE, 5L, "처리 완료");
        assertThat(s.getAction()).isEqualTo(StepAction.COMPLETE);
        assertThat(s.getActionByUserId()).isEqualTo(5L);
        assertThat(s.getActionComment()).isEqualTo("처리 완료");
        assertThat(s.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("finalizeStep — 이미 종료된 단계 재종료는 IllegalStateException")
    void finalizeStep_중복_종료_불가() {
        WorkflowInstanceStep s = openStep();
        s.finalizeStep(StepAction.APPROVE, 5L, null);
        assertThatThrownBy(() -> s.finalizeStep(StepAction.REJECT, 6L, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("isOverdue — slaDueAt 초과 + 미완료면 true")
    void isOverdue_초과() {
        WorkflowInstanceStep s = WorkflowInstanceStep.builder()
                .instanceId(1L).stepIndex(0).stepName("접수")
                .startedAt(LocalDateTime.now().minusHours(2))
                .slaDueAt(LocalDateTime.now().minusHours(1)).build();
        assertThat(s.isOverdue(LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("isOverdue — 완료된 단계는 false")
    void isOverdue_완료된_단계_false() {
        WorkflowInstanceStep s = openStep();
        s.finalizeStep(StepAction.COMPLETE, 5L, null);
        assertThat(s.isOverdue(LocalDateTime.now().plusDays(1))).isFalse();
    }
}
