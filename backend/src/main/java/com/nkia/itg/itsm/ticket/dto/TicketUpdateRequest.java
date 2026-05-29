package com.nkia.itg.itsm.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "티켓 본문 부분 수정 요청 (priority/status/assignee 는 별도 엔드포인트)")
public record TicketUpdateRequest(
        @Schema(description = "제목", example = "수정된 샘플 제목")
        @Size(max = 200) String title,

        @Schema(description = "본문", example = "수정된 샘플 본문")
        String content,

        @Schema(description = "분류", example = "REQ")
        String category
) {}
