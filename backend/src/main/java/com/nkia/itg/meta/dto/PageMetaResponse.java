package com.nkia.itg.meta.dto;

import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.entity.PageMeta;
import java.time.LocalDateTime;
import java.util.Map;

public record PageMetaResponse(
        String id,
        String title,
        SystemType systemType,
        PackageType packageType,
        String groupId,
        int majorVersion,
        int minorVersion,
        MetaStatus metaStatus,
        Map<String, Object> metaJson,
        boolean active,
        LocalDateTime createdAt,
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
