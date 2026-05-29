package com.nkia.itg.itsm.ticket.dto;

import com.nkia.itg.itsm.ticket.domain.Priority;
import com.nkia.itg.itsm.ticket.domain.TicketStatus;
import com.nkia.itg.itsm.ticket.entity.Ticket;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "티켓 응답 DTO (단건 조회·생성·수정 결과)")
public record TicketResponse(
        @Schema(description = "내부 ID", example = "1")
        Long id,

        @Schema(description = "표시용 티켓 번호", example = "ITSM-00001")
        String ticketNo,

        @Schema(description = "제목", example = "샘플 티켓 제목")
        String title,

        @Schema(description = "본문", example = "샘플 본문")
        String content,

        @Schema(description = "우선순위", example = "MEDIUM")
        Priority priority,

        @Schema(description = "상태", example = "OPEN")
        TicketStatus status,

        @Schema(description = "분류", example = "BUG")
        String category,

        @Schema(description = "담당자 ID", example = "assignee-sample-1")
        String assigneeId,

        @Schema(description = "생성 일시", example = "2026-05-29T10:15:30")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시", example = "2026-05-29T11:20:45")
        LocalDateTime updatedAt,

        @Schema(description = "종료 일시 (CLOSED 전이 시)", example = "2026-05-29T12:00:00")
        LocalDateTime closedAt,

        @Schema(description = "요청 유형 코드", example = "INCIDENT")
        String requestTypeCode,

        @Schema(description = "연결된 워크플로우 인스턴스 ID", example = "100")
        Long workflowInstanceId
) {
    public static TicketResponse from(Ticket e) {
        return new TicketResponse(
                e.getId(),
                e.getTicketNo(),
                e.getTitle(),
                e.getContent(),
                e.getPriority(),
                e.getStatus(),
                e.getCategory(),
                e.getAssigneeId(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getClosedAt(),
                e.getRequestTypeCode(),
                e.getWorkflowInstanceId()
        );
    }
}
