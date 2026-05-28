package com.nkia.itg.fixture;

import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.entity.PageMeta;
import java.util.HashMap;

public final class MetaFixture {

    private MetaFixture() {
    }

    public static PageMeta.PageMetaBuilder baseBuilder(String groupId, int major, int minor) {
        return PageMeta.builder()
                .id("%s-v%d-%d".formatted(groupId, major, minor))
                .title("샘플 페이지 " + groupId)
                .systemType(SystemType.ITSM)
                .packageType(PackageType.PACKAGE)
                .groupId(groupId)
                .majorVersion(major)
                .minorVersion(minor)
                .metaJson(new HashMap<>())
                .active(true);
    }

    public static PageMeta draft(String groupId, int major, int minor) {
        return baseBuilder(groupId, major, minor).metaStatus(MetaStatus.DRAFT).build();
    }

    public static PageMeta published(String groupId, int major, int minor) {
        return baseBuilder(groupId, major, minor).metaStatus(MetaStatus.PUBLISHED).build();
    }

    public static PageMeta archived(String groupId, int major, int minor) {
        return baseBuilder(groupId, major, minor).metaStatus(MetaStatus.ARCHIVED).build();
    }
}
