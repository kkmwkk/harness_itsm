package com.nkia.itg.system.role.dto;

import com.nkia.itg.system.permission.entity.Permission;
import com.nkia.itg.system.role.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "역할 단건 응답 (보유 권한 코드 포함)")
public record RoleResponse(
        Long id,
        String code,
        String name,
        String description,
        List<String> permissionCodes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RoleResponse from(Role r) {
        List<String> permissionCodes = r.getPermissions().stream()
                .map(Permission::getCode)
                .sorted()
                .toList();
        return new RoleResponse(
                r.getId(), r.getCode(), r.getName(), r.getDescription(),
                permissionCodes, r.getCreatedAt(), r.getUpdatedAt());
    }
}
