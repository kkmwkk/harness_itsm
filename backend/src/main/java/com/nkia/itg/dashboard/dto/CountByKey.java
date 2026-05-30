package com.nkia.itg.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 키별 집계 카운트 (우선순위·상태·자산 분류 분포 차트용). */
@Schema(description = "키별 집계 카운트")
public record CountByKey(
        @Schema(description = "집계 키", example = "HIGH") String key,
        @Schema(description = "건수", example = "12") long count
) {
}
