package com.nkia.itg.itsm.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@Schema(description = "워크플로우 정의 생성 요청. steps 는 단계 배열 JSONB.")
public record WorkflowDefinitionCreateRequest(
        @Schema(description = "워크플로우 정의 코드 (PK)", example = "WF_INCIDENT_STD")
        @NotBlank @Size(max = 40) String code,

        @Schema(description = "이름", example = "장애 표준 워크플로우")
        @NotBlank @Size(max = 120) String name,

        @Schema(description = "버전", example = "1")
        int version,

        @Schema(description = "단계 배열 (각 항목 index·name·assignee_role_code·sla_minutes·allowed_actions)")
        @NotEmpty List<Map<String, Object>> steps
) {}
