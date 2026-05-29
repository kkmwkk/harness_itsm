package com.nkia.itg.system.menu.dto;

import com.nkia.itg.system.menu.entity.Menu;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "메뉴 단건 응답")
public record MenuResponse(
        Long id,
        String code,
        Long parentId,
        String label,
        String icon,
        int sortOrder,
        String route,
        String groupId,
        String permissionCode,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MenuResponse from(Menu m) {
        return new MenuResponse(
                m.getId(), m.getCode(), m.getParentId(), m.getLabel(), m.getIcon(),
                m.getSortOrder(), m.getRoute(), m.getGroupId(), m.getPermissionCode(),
                m.isActive(), m.getCreatedAt(), m.getUpdatedAt());
    }
}
