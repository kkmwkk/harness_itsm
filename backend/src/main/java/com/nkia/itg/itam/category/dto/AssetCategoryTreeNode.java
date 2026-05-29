package com.nkia.itg.itam.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * 자산 분류 트리 노드. children 은 가변 리스트로 Service 트리 조립 단계에서 채운다.
 */
@Schema(description = "자산 분류 트리 노드")
public record AssetCategoryTreeNode(
        String code,
        String label,
        String parentCode,
        String path,
        String formMetaGroupId,
        boolean active,
        int sortOrder,
        List<AssetCategoryTreeNode> children
) {
    public static AssetCategoryTreeNode of(
            String code, String label, String parentCode, String path,
            String formMetaGroupId, boolean active, int sortOrder) {
        return new AssetCategoryTreeNode(
                code, label, parentCode, path, formMetaGroupId, active, sortOrder, new ArrayList<>());
    }
}
