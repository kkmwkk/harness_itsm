package com.nkia.itg.itam.asset.dto;

import com.nkia.itg.itam.asset.domain.AssetStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "자산 상태 전이 요청")
public record AssetStatusChangeRequest(
        @Schema(description = "전이할 상태", example = "STORAGE")
        @NotNull AssetStatus next
) {}
