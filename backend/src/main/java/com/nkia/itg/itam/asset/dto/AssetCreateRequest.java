package com.nkia.itg.itam.asset.dto;

import com.nkia.itg.itam.asset.domain.AssetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "자산 신규 등록 요청")
public record AssetCreateRequest(
        @Schema(description = "자산명", example = "샘플 자산")
        @NotBlank @Size(max = 200) String name,

        @Schema(description = "자산 유형", example = "HARDWARE")
        @NotNull AssetType assetType,

        @Schema(description = "모델명", example = "SAMPLE-MODEL")
        String model,

        @Schema(description = "시리얼 번호", example = "SN-SAMPLE-1")
        String serialNo,

        @Schema(description = "분류", example = "노트북")
        String category,

        @Schema(description = "담당자 ID", example = "assignee-sample-1")
        String assigneeId,

        @Schema(description = "위치", example = "본사 3층")
        String location,

        @Schema(description = "취득 일자", example = "2026-01-15")
        LocalDate acquiredAt,

        @Schema(description = "자산이 속한 메타 그룹 ID (보통 'itg-asset')", example = "itg-asset")
        @NotBlank String pageGroupId
) {}
