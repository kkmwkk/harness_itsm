package com.nkia.itg.itsm.requesttype.dto;

import com.nkia.itg.itsm.requesttype.entity.TicketRequestType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "요청 유형 응답 DTO")
public record TicketRequestTypeResponse(
        @Schema(description = "요청 유형 코드 (PK)", example = "INCIDENT")
        String code,

        @Schema(description = "라벨", example = "장애")
        String label,

        @Schema(description = "폼 메타 그룹 ID", example = "itg-ticket-incident")
        String formMetaGroupId,

        @Schema(description = "기본 워크플로우 정의 코드", example = "WF_INCIDENT_STD")
        String defaultWorkflowCode,

        @Schema(description = "기본 SLA(분)", example = "240")
        Integer slaMinutesDefault,

        @Schema(description = "활성 여부", example = "true")
        boolean active,

        @Schema(description = "생성 일시", example = "2026-05-29T10:15:30")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시", example = "2026-05-29T11:20:45")
        LocalDateTime updatedAt
) {
    public static TicketRequestTypeResponse from(TicketRequestType e) {
        return new TicketRequestTypeResponse(
                e.getCode(),
                e.getLabel(),
                e.getFormMetaGroupId(),
                e.getDefaultWorkflowCode(),
                e.getSlaMinutesDefault(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
