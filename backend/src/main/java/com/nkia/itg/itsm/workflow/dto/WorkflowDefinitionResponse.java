package com.nkia.itg.itsm.workflow.dto;

import com.nkia.itg.itsm.workflow.entity.WorkflowDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "워크플로우 정의 응답 DTO")
public record WorkflowDefinitionResponse(
        @Schema(description = "워크플로우 정의 코드 (PK)", example = "WF_INCIDENT_STD")
        String code,

        @Schema(description = "이름", example = "장애 표준 워크플로우")
        String name,

        @Schema(description = "버전", example = "1")
        int version,

        @Schema(description = "단계 배열 (JSONB)")
        List<Map<String, Object>> steps,

        @Schema(description = "활성 여부", example = "true")
        boolean active,

        @Schema(description = "생성 일시", example = "2026-05-29T10:15:30")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시", example = "2026-05-29T11:20:45")
        LocalDateTime updatedAt
) {
    public static WorkflowDefinitionResponse from(WorkflowDefinition e) {
        return new WorkflowDefinitionResponse(
                e.getCode(),
                e.getName(),
                e.getVersion(),
                e.getSteps(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
