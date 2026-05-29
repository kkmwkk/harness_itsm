package com.nkia.itg.itsm.ticket.dto;

import com.nkia.itg.itsm.ticket.domain.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "티켓 상태 전이 요청")
public record TicketStatusChangeRequest(
        @Schema(description = "전이할 상태", example = "IN_PROGRESS")
        @NotNull TicketStatus next
) {}
