package com.nkia.itg.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 시스템 관리자(ROLE_ADMIN) 전용 추가 지표. 비관리자 응답에서는 null. */
@Schema(description = "관리자 전용 추가 지표 (비관리자는 null)")
public record AdminStats(
        @Schema(description = "전체 사용자 수", example = "4") long userCount,
        @Schema(description = "전체 메뉴 수", example = "12") long menuCount,
        @Schema(description = "PUBLISHED 메타 그룹 수", example = "9") long metaGroupCount
) {
}
