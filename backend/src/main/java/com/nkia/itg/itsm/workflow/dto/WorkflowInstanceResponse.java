package com.nkia.itg.itsm.workflow.dto;

import com.nkia.itg.itsm.workflow.domain.WorkflowStatus;
import com.nkia.itg.itsm.workflow.entity.WorkflowInstance;
import com.nkia.itg.itsm.workflow.entity.WorkflowInstanceStep;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "워크플로우 인스턴스 + 단계 이력 응답 DTO")
public record WorkflowInstanceResponse(
        @Schema(description = "인스턴스 ID", example = "100")
        Long id,

        @Schema(description = "워크플로우 정의 코드", example = "WF_INCIDENT_STD")
        String workflowDefCode,

        @Schema(description = "티켓 ID", example = "42")
        Long ticketId,

        @Schema(description = "현재 단계 인덱스 (0-based)", example = "1")
        int currentStepIndex,

        @Schema(description = "인스턴스 상태", example = "RUNNING")
        WorkflowStatus status,

        @Schema(description = "시작 일시", example = "2026-05-29T10:15:30")
        LocalDateTime startedAt,

        @Schema(description = "완료 일시", example = "2026-05-29T12:00:00")
        LocalDateTime completedAt,

        @Schema(description = "단계 실행 이력 (인덱스 오름차순)")
        List<WorkflowInstanceStepResponse> steps
) {
    public static WorkflowInstanceResponse of(WorkflowInstance instance, List<WorkflowInstanceStep> steps) {
        return new WorkflowInstanceResponse(
                instance.getId(),
                instance.getWorkflowDefCode(),
                instance.getTicketId(),
                instance.getCurrentStepIndex(),
                instance.getStatus(),
                instance.getStartedAt(),
                instance.getCompletedAt(),
                steps.stream().map(WorkflowInstanceStepResponse::from).toList()
        );
    }
}
