package com.nkia.itg.itam.asset.dto;

import com.nkia.itg.itam.asset.domain.AssetLifecycleEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Schema(description = "자산 이력 이벤트 기록 요청 (취득·이관·수리·폐기·갱신)")
public record AssetLifecycleEventCreateRequest(
        @Schema(description = "이벤트 유형", example = "TRANSFERRED")
        @NotNull AssetLifecycleEventType eventType,

        @Schema(description = "처리자 사용자 ID (옵션)", example = "5")
        Long byUserId,

        @Schema(description = "이벤트별 부가 정보 (JSONB, 옵션)")
        Map<String, Object> payload
) {}
