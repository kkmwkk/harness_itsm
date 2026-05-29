package com.nkia.itg.itsm.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "티켓 담당자 변경 요청 (null/blank 는 담당자 해제)")
public record TicketAssignRequest(
        @Schema(description = "담당자 ID (null/blank 허용 — 해제)", example = "assignee-sample-2")
        String assigneeId
) {}
