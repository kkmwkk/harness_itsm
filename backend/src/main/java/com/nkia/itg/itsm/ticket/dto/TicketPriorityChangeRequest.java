package com.nkia.itg.itsm.ticket.dto;

import com.nkia.itg.itsm.ticket.domain.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "티켓 우선순위 변경 요청")
public record TicketPriorityChangeRequest(
        @Schema(description = "변경할 우선순위", example = "HIGH")
        @NotNull Priority next
) {}
