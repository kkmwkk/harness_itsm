package com.nkia.itg.itsm.workflow.entity;

import com.nkia.itg.itsm.requesttype.domain.StepAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단계 실행 이력. instanceId 로 워크플로우 인스턴스에 종속.
 * step_name·assignee_role 은 정의 변경에도 이력을 보존하기 위한 스냅샷.
 * 액션 실행 시 finalize() 로 completed_at·action 을 한 번만 채운다.
 */
@Entity
@Table(name = "workflow_instance_step")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowInstanceStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "step_name", length = 120, nullable = false)
    private String stepName;

    @Column(name = "assignee_role", length = 40)
    private String assigneeRole;

    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20)
    private StepAction action;

    @Column(name = "action_by_user_id")
    private Long actionByUserId;

    @Column(name = "action_comment", columnDefinition = "text")
    private String actionComment;

    /** 단계 진입 시작점. startedAt 미설정이면 now 로 채운다. */
    public void onStart() {
        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }

    /** 단계 종료 — 액션·실행자·코멘트·완료시각을 한 번만 기록. */
    public void finalizeStep(StepAction action, Long actorUserId, String comment) {
        if (this.completedAt != null) {
            throw new IllegalStateException("이미 종료된 단계입니다. (step " + this.stepIndex + ")");
        }
        this.action = action;
        this.actionByUserId = actorUserId;
        this.actionComment = comment;
        this.completedAt = LocalDateTime.now();
    }

    /** SLA 초과 여부 (slaDueAt 미설정이면 false). */
    public boolean isOverdue(LocalDateTime now) {
        return slaDueAt != null && completedAt == null && now.isAfter(slaDueAt);
    }
}
