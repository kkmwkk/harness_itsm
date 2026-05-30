package com.nkia.itg.pms.project.dto;

import com.nkia.itg.pms.project.domain.ProjectStatus;
import com.nkia.itg.pms.project.entity.Project;
import com.nkia.itg.pms.project.entity.Task;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "프로젝트 응답 (태스크 포함 — 간트 표시용)")
public record ProjectResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "PRJ-2026-001") String code,
        @Schema(example = "샘플-프로젝트") String name,
        @Schema(example = "IN_PROGRESS") ProjectStatus status,
        @Schema(example = "3") Long ownerUserId,
        @Schema(example = "2") Long deptId,
        @Schema(example = "2026-05-01") LocalDate startDate,
        @Schema(example = "2026-07-15") LocalDate dueDate,
        @Schema(example = "45") int progress,
        List<TaskResponse> tasks) {

    public static ProjectResponse from(Project p, List<Task> tasks) {
        return new ProjectResponse(
                p.getId(), p.getCode(), p.getName(), p.getStatus(),
                p.getOwnerUserId(), p.getDeptId(), p.getStartDate(), p.getDueDate(), p.getProgress(),
                tasks.stream().map(TaskResponse::from).toList());
    }
}
