package com.nkia.itg.system.menu.dto;

import com.nkia.itg.system.menu.entity.Menu;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 메뉴 트리 노드. children 은 가변 리스트로 Service 트리 조립 단계에서 채운다.
 * permissionCode 는 권한 필터 후 노출된 노드에만 남으므로 클라이언트 표시용 참고값.
 */
@Schema(description = "메뉴 트리 노드")
public record MenuTreeNode(
        Long id,
        String code,
        Long parentId,
        String label,
        String icon,
        int sortOrder,
        String route,
        String groupId,
        String permissionCode,
        List<MenuTreeNode> children
) {
    public static MenuTreeNode of(Menu m) {
        return new MenuTreeNode(
                m.getId(), m.getCode(), m.getParentId(), m.getLabel(), m.getIcon(),
                m.getSortOrder(), m.getRoute(), m.getGroupId(), m.getPermissionCode(),
                new ArrayList<>());
    }
}
