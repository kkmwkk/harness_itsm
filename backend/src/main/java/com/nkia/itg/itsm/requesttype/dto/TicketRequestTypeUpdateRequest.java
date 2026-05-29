package com.nkia.itg.itsm.requesttype.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "요청 유형 수정 요청 (code 는 불변)")
public record TicketRequestTypeUpdateRequest(
        @Schema(description = "라벨", example = "장애")
        @NotBlank @Size(max = 80) String label,

        @Schema(description = "폼 메타 그룹 ID", example = "itg-ticket-incident")
        @Size(max = 100) String formMetaGroupId,

        @Schema(description = "기본 워크플로우 정의 코드", example = "WF_INCIDENT_STD")
        @Size(max = 40) String defaultWorkflowCode,

        @Schema(description = "기본 SLA(분)", example = "240")
        Integer slaMinutesDefault,

        @Schema(description = "활성 여부", example = "true")
        boolean active
) {}
