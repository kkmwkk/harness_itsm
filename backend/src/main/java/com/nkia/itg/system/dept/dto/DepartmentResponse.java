package com.nkia.itg.system.dept.dto;

import com.nkia.itg.system.dept.entity.Department;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "부서 단건 응답")
public record DepartmentResponse(
        Long id,
        String code,
        String name,
        Long parentId,
        String path,
        Long managerUserId,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DepartmentResponse from(Department d) {
        return new DepartmentResponse(
                d.getId(), d.getCode(), d.getName(), d.getParentId(), d.getPath(),
                d.getManagerUserId(), d.isActive(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
