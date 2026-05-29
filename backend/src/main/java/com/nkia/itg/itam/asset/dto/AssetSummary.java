package com.nkia.itg.itam.asset.dto;

import com.nkia.itg.itam.asset.domain.AssetStatus;
import com.nkia.itg.itam.asset.domain.AssetType;
import com.nkia.itg.itam.asset.entity.Asset;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "자산 목록(그리드)용 요약 DTO")
public record AssetSummary(
        @Schema(description = "내부 ID", example = "1")
        Long id,

        @Schema(description = "표시용 자산 번호", example = "AST-00001")
        String assetNo,

        @Schema(description = "자산명", example = "샘플 자산")
        String name,

        @Schema(description = "자산 유형", example = "HARDWARE")
        AssetType assetType,

        @Schema(description = "상태", example = "ACTIVE")
        AssetStatus status,

        @Schema(description = "담당자 ID", example = "assignee-sample-1")
        String assigneeId,

        @Schema(description = "취득 일자", example = "2026-01-15")
        LocalDate acquiredAt
) {
    public static AssetSummary from(Asset e) {
        return new AssetSummary(
                e.getId(),
                e.getAssetNo(),
                e.getName(),
                e.getAssetType(),
                e.getStatus(),
                e.getAssigneeId(),
                e.getAcquiredAt()
        );
    }
}
