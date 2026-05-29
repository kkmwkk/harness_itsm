package com.nkia.itg.system.permission.dto;

import com.nkia.itg.system.permission.entity.Permission;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "권한 단건 응답")
public record PermissionResponse(
        Long id,
        String code,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PermissionResponse from(Permission p) {
        return new PermissionResponse(
                p.getId(), p.getCode(), p.getName(), p.getDescription(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
