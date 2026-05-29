package com.nkia.itg.itsm.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@Schema(description = "워크플로우 정의 수정 요청 (code 는 불변)")
public record WorkflowDefinitionUpdateRequest(
        @Schema(description = "이름", example = "장애 표준 워크플로우")
        @NotBlank @Size(max = 120) String name,

        @Schema(description = "버전", example = "2")
        int version,

        @Schema(description = "단계 배열 (각 항목 index·name·assignee_role_code·sla_minutes·allowed_actions)")
        @NotEmpty List<Map<String, Object>> steps,

        @Schema(description = "활성 여부", example = "true")
        boolean active
) {}
