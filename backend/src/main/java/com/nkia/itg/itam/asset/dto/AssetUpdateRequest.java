package com.nkia.itg.itam.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "자산 속성 수정 요청 (부분 수정, null 필드는 무시)")
public record AssetUpdateRequest(
        @Schema(description = "자산명", example = "샘플 자산")
        @Size(max = 200) String name,

        @Schema(description = "모델명", example = "SAMPLE-MODEL")
        String model,

        @Schema(description = "시리얼 번호", example = "SN-SAMPLE-1")
        String serialNo,

        @Schema(description = "분류", example = "노트북")
        String category,

        @Schema(description = "위치", example = "본사 3층")
        String location
) {}
