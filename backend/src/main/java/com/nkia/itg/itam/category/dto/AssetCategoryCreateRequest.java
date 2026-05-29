package com.nkia.itg.itam.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "자산 분류 생성 요청")
public record AssetCategoryCreateRequest(
        @Schema(description = "분류 코드 (PK)", example = "HW_LAPTOP")
        @NotBlank @Size(max = 40) String code,

        @Schema(description = "라벨", example = "노트북")
        @NotBlank @Size(max = 80) String label,

        @Schema(description = "상위 분류 코드 (루트면 생략)", example = "HW")
        @Size(max = 40) String parentCode,

        @Schema(description = "분류별 폼 메타 그룹 ID", example = "itg-asset-hw-laptop")
        @Size(max = 100) String formMetaGroupId,

        @Schema(description = "정렬 순서", example = "1")
        int sortOrder
) {}
