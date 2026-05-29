package com.nkia.itg.system.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 부서 트리 노드. children 은 가변 리스트로 Service 트리 조립 단계에서 채운다.
 */
@Schema(description = "부서 트리 노드")
public record DepartmentTreeNode(
        Long id,
        String code,
        String name,
        Long parentId,
        String path,
        boolean active,
        List<DepartmentTreeNode> children
) {
    public static DepartmentTreeNode of(
            Long id, String code, String name, Long parentId, String path, boolean active) {
        return new DepartmentTreeNode(id, code, name, parentId, path, active, new ArrayList<>());
    }
}
