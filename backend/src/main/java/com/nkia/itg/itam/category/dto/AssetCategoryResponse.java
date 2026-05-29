package com.nkia.itg.itam.category.dto;

import com.nkia.itg.itam.category.entity.AssetCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "자산 분류 단건 응답 DTO (트리 children 미포함)")
public record AssetCategoryResponse(
        @Schema(description = "분류 코드 (PK)", example = "HW_LAPTOP")
        String code,

        @Schema(description = "라벨", example = "노트북")
        String label,

        @Schema(description = "상위 분류 코드 (루트면 null)", example = "HW")
        String parentCode,

        @Schema(description = "트리 경로", example = "/HW/HW_LAPTOP/")
        String path,

        @Schema(description = "분류별 폼 메타 그룹 ID", example = "itg-asset-hw-laptop")
        String formMetaGroupId,

        @Schema(description = "활성 여부", example = "true")
        boolean active,

        @Schema(description = "정렬 순서", example = "1")
        int sortOrder,

        @Schema(description = "생성 일시", example = "2026-05-29T10:15:30")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시", example = "2026-05-29T11:20:45")
        LocalDateTime updatedAt
) {
    public static AssetCategoryResponse from(AssetCategory e) {
        return new AssetCategoryResponse(
                e.getCode(),
                e.getLabel(),
                e.getParentCode(),
                e.getPath(),
                e.getFormMetaGroupId(),
                e.isActive(),
                e.getSortOrder(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
