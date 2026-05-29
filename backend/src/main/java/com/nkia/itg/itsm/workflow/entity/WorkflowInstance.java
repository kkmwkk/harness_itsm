package com.nkia.itg.itsm.workflow.entity;

import com.nkia.itg.itsm.workflow.domain.WorkflowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 워크플로우 인스턴스 (티켓 1개당 1개). 단계 전이 매트릭스(ARCHITECTURE §15-2)는
 * 본 Entity 의 도메인 메서드 advance/reject/complete/reopen 에 박아 둔다 —
 * Service 안에 if/else 로 흩뿌리지 않는다.
 *
 * <p>상태 가드: RUNNING 만 advance/reject/complete 허용. 종결(COMPLETED/CANCELED/REJECTED)
 * 상태는 액션 거부. reopen 은 종결 상태에서만 RUNNING 으로 되돌린다.
 */
@Entity
@Table(name = "workflow_instance")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "workflow_def_code", length = 40, nullable = false)
    private String workflowDefCode;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "current_step_index", nullable = false)
    @Builder.Default
    private int currentStepIndex = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.RUNNING;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = WorkflowStatus.RUNNING;
        }
    }

    public boolean isRunning() {
        return this.status == WorkflowStatus.RUNNING;
    }

    public boolean isTerminal() {
        return this.status != WorkflowStatus.RUNNING;
    }

    /** 다음 단계로 전이 (RUNNING 유지). RUNNING 아니면 전이 불가. */
    public void advance(int nextStepIndex) {
        ensureRunning();
        this.currentStepIndex = nextStepIndex;
    }

    /** REJECT — 인스턴스 REJECTED 종결. */
    public void reject() {
        ensureRunning();
        this.status = WorkflowStatus.REJECTED;
        this.completedAt = LocalDateTime.now();
    }

    /** APPROVE/COMPLETE/CONFIRM 의 마지막 단계 — 인스턴스 COMPLETED 종결. */
    public void complete() {
        ensureRunning();
        this.status = WorkflowStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /** REOPEN — 종결 상태에서 첫 단계로 재오픈. RUNNING 이면 재오픈 불가. */
    public void reopen() {
        if (isRunning()) {
            throw new IllegalStateException("진행 중인 워크플로우는 재오픈할 수 없습니다.");
        }
        this.status = WorkflowStatus.RUNNING;
        this.currentStepIndex = 0;
        this.completedAt = null;
    }

    private void ensureRunning() {
        if (!isRunning()) {
            throw new IllegalStateException(
                "종결된 워크플로우(" + this.status + ")는 단계를 전이할 수 없습니다.");
        }
    }
}
