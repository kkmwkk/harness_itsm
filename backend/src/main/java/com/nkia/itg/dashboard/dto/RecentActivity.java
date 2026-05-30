package com.nkia.itg.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 최근 활동 피드 항목 (티켓 접수·자산 이력 이벤트를 시간순으로 통합). */
@Schema(description = "최근 활동 피드 항목")
public record RecentActivity(
        @Schema(description = "활동 종류", example = "TICKET", allowableValues = {"TICKET", "ASSET"})
        String type,
        @Schema(description = "표시 제목", example = "노트북 부팅 불가 신고") String title,
        @Schema(description = "보조 설명", example = "ITSM · ITSM-00042") String detail,
        @Schema(description = "발생 시각") LocalDateTime at
) {
}
