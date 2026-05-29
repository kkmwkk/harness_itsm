package com.nkia.itg.itsm.ticket.dto;

import com.nkia.itg.itsm.ticket.domain.Priority;
import com.nkia.itg.itsm.ticket.domain.TicketStatus;
import com.nkia.itg.itsm.ticket.entity.Ticket;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "티켓 목록용 경량 DTO (content 제외)")
public record TicketSummary(
        @Schema(description = "내부 ID", example = "1")
        Long id,

        @Schema(description = "표시용 티켓 번호", example = "ITSM-00001")
        String ticketNo,

        @Schema(description = "제목", example = "샘플 티켓 제목")
        String title,

        @Schema(description = "우선순위", example = "MEDIUM")
        Priority priority,

        @Schema(description = "상태", example = "OPEN")
        TicketStatus status,

        @Schema(description = "담당자 ID", example = "assignee-sample-1")
        String assigneeId,

        @Schema(description = "생성 일시", example = "2026-05-29T10:15:30")
        LocalDateTime createdAt
) {
    public static TicketSummary from(Ticket e) {
        return new TicketSummary(
                e.getId(),
                e.getTicketNo(),
                e.getTitle(),
                e.getPriority(),
                e.getStatus(),
                e.getAssigneeId(),
                e.getCreatedAt()
        );
    }
}
