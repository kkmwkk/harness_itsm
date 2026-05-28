package com.nkia.itg.meta.dto;

import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.entity.PageMeta;
import java.time.LocalDateTime;

public record PageMetaVersionResponse(
        String id,
        String groupId,
        int majorVersion,
        int minorVersion,
        MetaStatus metaStatus,
        LocalDateTime createdAt,
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
