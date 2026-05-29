package com.nkia.itg.itam.asset.dto;

import com.nkia.itg.itam.asset.domain.AssetLifecycleEventType;
import com.nkia.itg.itam.asset.entity.AssetLifecycleEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "자산 이력 이벤트 응답 DTO")
public record AssetLifecycleEventResponse(
        @Schema(description = "이벤트 ID", example = "1")
        Long id,

        @Schema(description = "자산 ID", example = "42")
        Long assetId,

        @Schema(description = "이벤트 유형", example = "TRANSFERRED")
        AssetLifecycleEventType eventType,

        @Schema(description = "이벤트 일자", example = "2026-05-29")
        LocalDate eventDate,

        @Schema(description = "처리자 사용자 ID", example = "5")
        Long byUserId,

        @Schema(description = "이벤트별 부가 정보 (JSONB)")
        Map<String, Object> payload,

        @Schema(description = "생성 일시", example = "2026-05-29T10:15:30")
        LocalDateTime createdAt
) {
    public static AssetLifecycleEventResponse from(AssetLifecycleEvent e) {
        return new AssetLifecycleEventResponse(
                e.getId(),
                e.getAssetId(),
                e.getEventType(),
                e.getEventDate(),
                e.getByUserId(),
                e.getPayload(),
                e.getCreatedAt()
        );
    }
}
