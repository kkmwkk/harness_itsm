package com.nkia.itg.meta.dto;

import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.entity.PageMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "PageMeta 버전 이력 항목 — meta_json 본문은 제외한 경량 응답")
public record PageMetaVersionResponse(
        @Schema(description = "메타 ID", example = "itg-ticket-v1-2")
        String id,

        @Schema(description = "페이지 그룹 ID", example = "itg-ticket")
        String groupId,

        @Schema(description = "메이저 버전", example = "1")
        int majorVersion,

        @Schema(description = "마이너 버전", example = "2")
        int minorVersion,

        @Schema(description = "메타 상태", example = "PUBLISHED")
        MetaStatus metaStatus,

        @Schema(description = "생성 일시", example = "2026-05-28T10:15:30")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시", example = "2026-05-28T11:20:45")
        LocalDateTime updatedAt
) {
    public static PageMetaVersionResponse from(PageMeta e) {
        return new PageMetaVersionResponse(
                e.getId(),
                e.getGroupId(),
                e.getMajorVersion(),
                e.getMinorVersion(),
                e.getMetaStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
