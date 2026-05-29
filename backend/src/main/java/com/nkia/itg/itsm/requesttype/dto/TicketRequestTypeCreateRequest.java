package com.nkia.itg.itsm.requesttype.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "요청 유형 생성 요청")
public record TicketRequestTypeCreateRequest(
        @Schema(description = "요청 유형 코드 (PK, 영문 대문자 코드)", example = "INCIDENT")
        @NotBlank @Size(max = 40) String code,

        @Schema(description = "라벨", example = "장애")
        @NotBlank @Size(max = 80) String label,

        @Schema(description = "폼 메타 그룹 ID", example = "itg-ticket-incident")
        @Size(max = 100) String formMetaGroupId,

        @Schema(description = "기본 워크플로우 정의 코드", example = "WF_INCIDENT_STD")
        @Size(max = 40) String defaultWorkflowCode,

        @Schema(description = "기본 SLA(분)", example = "240")
        Integer slaMinutesDefault
) {}
