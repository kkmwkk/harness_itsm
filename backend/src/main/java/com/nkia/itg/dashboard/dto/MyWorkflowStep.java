package com.nkia.itg.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 현재 사용자가 처리해야 할 워크플로우 단계 (역할·직접 지정 매칭). */
@Schema(description = "내 워크플로우 처리 대기 단계")
public record MyWorkflowStep(
        @Schema(description = "워크플로우 인스턴스 ID", example = "100") Long instanceId,
        @Schema(description = "연결된 티켓 ID", example = "42") Long ticketId,
        @Schema(description = "단계 인덱스(0-based)", example = "1") int stepIndex,
        @Schema(description = "단계명", example = "1차 검토") String stepName,
        @Schema(description = "담당 역할 코드", example = "ROLE_TEAM_LEAD") String assigneeRole,
        @Schema(description = "단계 시작 시각") LocalDateTime startedAt,
        @Schema(description = "SLA 마감 시각(없으면 null)") LocalDateTime slaDueAt,
        @Schema(description = "SLA 초과 여부", example = "false") boolean overdue
) {
}
