package com.nkia.itg.pms.project.dto;

import com.nkia.itg.pms.project.domain.TaskStatus;
import com.nkia.itg.pms.project.entity.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "태스크 응답")
public record TaskResponse(
        @Schema(example = "10") Long id,
        @Schema(example = "요구사항 분석") String title,
        @Schema(example = "IN_PROGRESS") TaskStatus status,
        @Schema(example = "5") Long assigneeUserId,
        @Schema(example = "2026-05-01") LocalDate startDate,
        @Schema(example = "2026-05-12") LocalDate dueDate,
        @Schema(example = "60") int progress) {

    public static TaskResponse from(Task t) {
        return new TaskResponse(
                t.getId(), t.getTitle(), t.getStatus(), t.getAssigneeUserId(),
                t.getStartDate(), t.getDueDate(), t.getProgress());
    }
}
