package com.nkia.itg.itsm.workflow.dto;

import com.nkia.itg.itsm.requesttype.domain.StepAction;
import com.nkia.itg.itsm.workflow.entity.WorkflowInstanceStep;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "워크플로우 단계 실행 이력 DTO")
public record WorkflowInstanceStepResponse(
        @Schema(description = "단계 이력 ID", example = "1")
        Long id,

        @Schema(description = "단계 인덱스 (0-based)", example = "0")
        int stepIndex,

        @Schema(description = "단계명 (스냅샷)", example = "접수")
        String stepName,

        @Schema(description = "담당 역할 코드", example = "ROLE_IT_SUPPORT")
        String assigneeRole,

        @Schema(description = "직접 지정 담당자 사용자 ID", example = "5")
        Long assignedToUserId,

        @Schema(description = "단계 시작 일시", example = "2026-05-29T10:15:30")
        LocalDateTime startedAt,

        @Schema(description = "단계 완료 일시", example = "2026-05-29T11:20:45")
        LocalDateTime completedAt,

        @Schema(description = "SLA 마감 일시", example = "2026-05-29T11:15:30")
        LocalDateTime slaDueAt,

        @Schema(description = "실행 액션", example = "APPROVE")
        StepAction action,

        @Schema(description = "액션 실행자 사용자 ID", example = "5")
        Long actionByUserId,

        @Schema(description = "액션 코멘트", example = "승인합니다")
        String actionComment
) {
    public static WorkflowInstanceStepResponse from(WorkflowInstanceStep e) {
        return new WorkflowInstanceStepResponse(
                e.getId(),
                e.getStepIndex(),
                e.getStepName(),
                e.getAssigneeRole(),
                e.getAssignedToUserId(),
                e.getStartedAt(),
                e.getCompletedAt(),
                e.getSlaDueAt(),
                e.getAction(),
                e.getActionByUserId(),
                e.getActionComment()
        );
    }
}
