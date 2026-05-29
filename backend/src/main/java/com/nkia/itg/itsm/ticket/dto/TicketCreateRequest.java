package com.nkia.itg.itsm.ticket.dto;

import com.nkia.itg.itsm.ticket.domain.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "티켓 신규 생성 요청")
public record TicketCreateRequest(
        @Schema(description = "제목", example = "샘플 티켓 제목")
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "본문", example = "샘플 본문")
        String content,

        @Schema(description = "우선순위", example = "MEDIUM")
        @NotNull Priority priority,

        @Schema(description = "분류", example = "BUG")
        String category,

        @Schema(description = "담당자 ID", example = "assignee-sample-1")
        String assigneeId
) {}
