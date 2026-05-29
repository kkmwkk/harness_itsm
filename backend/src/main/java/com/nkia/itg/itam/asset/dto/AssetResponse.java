package com.nkia.itg.itam.asset.dto;

import com.nkia.itg.itam.asset.domain.AssetStatus;
import com.nkia.itg.itam.asset.domain.AssetType;
import com.nkia.itg.itam.asset.entity.Asset;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "자산 응답 DTO (단건 조회·생성·수정 결과)")
public record AssetResponse(
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

        @Schema(description = "처분 일자 (RETIRED 전이 시)", example = "2026-05-29")
        LocalDate disposedAt,

        @Schema(description = "등록 시점의 메타 ID (이력 복원용)", example = "itg-asset-v1-1")
        String pageMetaIdAtRegistration,

        @Schema(description = "생성 일시", example = "2026-05-29T10:15:30")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시", example = "2026-05-29T11:20:45")
        LocalDateTime updatedAt
) {
    public static AssetResponse from(Asset e) {
        return new AssetResponse(
                e.getId(),
                e.getAssetNo(),
                e.getName(),
                e.getAssetType(),
                e.getStatus(),
                e.getModel(),
                e.getSerialNo(),
                e.getCategory(),
                e.getAssigneeId(),
                e.getLocation(),
                e.getAcquiredAt(),
                e.getDisposedAt(),
                e.getPageMetaIdAtRegistration(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
