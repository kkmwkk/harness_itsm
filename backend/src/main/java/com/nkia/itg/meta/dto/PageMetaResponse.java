package com.nkia.itg.meta.dto;

import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.entity.PageMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "PageMeta 응답 DTO — 화면 렌더링 및 단건 조회에 사용")
public record PageMetaResponse(
        @Schema(description = "메타 ID ({groupId}-v{major}-{minor})", example = "itg-ticket-v1-2")
        String id,

        @Schema(description = "페이지 타이틀", example = "ITSM 티켓 관리")
        String title,

        @Schema(description = "시스템 모듈 구분", example = "ITSM")
        SystemType systemType,

        @Schema(description = "패키지 구분 (본사 기본 / 고객사 커스터마이징)", example = "PACKAGE")
        PackageType packageType,

        @Schema(description = "페이지 그룹 ID", example = "itg-ticket")
        String groupId,

        @Schema(description = "메이저 버전", example = "1")
        int majorVersion,

        @Schema(description = "마이너 버전", example = "2")
        int minorVersion,

        @Schema(description = "메타 상태", example = "PUBLISHED")
        MetaStatus metaStatus,

        @Schema(
                description = "메타 본문 (api / grid / form / detail / actions 구조)",
                example = "{\"api\":\"/api/tickets\",\"grid\":{\"columns\":[]},\"form\":{\"fields\":[]}}"
        )
        Map<String, Object> metaJson,

        @Schema(description = "활성 여부 (soft delete 플래그)", example = "true")
        boolean active,

        @Schema(description = "생성 일시", example = "2026-05-28T10:15:30")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시", example = "2026-05-28T11:20:45")
        LocalDateTime updatedAt
) {
    public static PageMetaResponse from(PageMeta e) {
        return new PageMetaResponse(
                e.getId(),
                e.getTitle(),
                e.getSystemType(),
                e.getPackageType(),
                e.getGroupId(),
                e.getMajorVersion(),
                e.getMinorVersion(),
                e.getMetaStatus(),
                e.getMetaJson(),
                e.isActive(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    public String versionLabel() {
        return "v" + majorVersion + "." + minorVersion;
    }
}
