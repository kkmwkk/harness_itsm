package com.nkia.itg.itsm.workflow.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nkia.itg.itsm.workflow.domain.WorkflowStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WorkflowInstance 도메인 메서드 (전이 매트릭스)")
class WorkflowInstanceTest {

    private WorkflowInstance running(int step) {
        return WorkflowInstance.builder()
                .id(1L).workflowDefCode("WF").ticketId(1L)
                .currentStepIndex(step).status(WorkflowStatus.RUNNING)
                .startedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("advance — RUNNING 에서 다음 step 으로 currentStepIndex 갱신")
    void advance_다음_step() {
        WorkflowInstance i = running(0);
        i.advance(1);
        assertThat(i.getCurrentStepIndex()).isEqualTo(1);
        assertThat(i.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
    }

    @Test
    @DisplayName("complete — COMPLETED + completedAt set")
    void complete_종결() {
        WorkflowInstance i = running(2);
        i.complete();
        assertThat(i.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(i.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("reject — REJECTED + completedAt set")
    void reject_종결() {
        WorkflowInstance i = running(1);
        i.reject();
        assertThat(i.getStatus()).isEqualTo(WorkflowStatus.REJECTED);
        assertThat(i.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("종결 인스턴스의 advance/complete 는 IllegalStateException")
    void 종결_인스턴스_advance_불가() {
        WorkflowInstance i = running(0);
        i.complete();
        assertThatThrownBy(() -> i.advance(1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(i::reject).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reopen — 종결 상태에서 RUNNING + currentStepIndex 0 + completedAt null")
    void reopen_재오픈() {
        WorkflowInstance i = running(3);
        i.complete();
        i.reopen();
        assertThat(i.getStatus()).isEqualTo(WorkflowStatus.RUNNING);
        assertThat(i.getCurrentStepIndex()).isEqualTo(0);
        assertThat(i.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("reopen — RUNNING 인스턴스는 재오픈 불가")
    void reopen_RUNNING_불가() {
        WorkflowInstance i = running(0);
        assertThatThrownBy(i::reopen).isInstanceOf(IllegalStateException.class);
    }
}
