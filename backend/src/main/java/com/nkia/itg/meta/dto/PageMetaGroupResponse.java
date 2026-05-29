package com.nkia.itg.meta.dto;

import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.entity.PageMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

@Schema(description = "PageMeta 그룹 요약 — group_id 단위로 집계한 No-code 편집기 좌측 목록 항목")
public record PageMetaGroupResponse(
        @Schema(description = "페이지 그룹 ID", example = "itg-ticket")
        String groupId,

        @Schema(description = "대표 타이틀 (최신 PUBLISHED → 없으면 최신 버전 기준)", example = "ITSM 티켓 관리")
        String title,

        @Schema(description = "시스템 모듈 구분", example = "ITSM")
        SystemType systemType,

        @Schema(description = "패키지 구분", example = "PACKAGE")
        PackageType packageType,

        @Schema(description = "최신 PUBLISHED 메타 ID (없으면 null)", example = "itg-ticket-v1-2")
        String publishedId,

        @Schema(description = "최신 PUBLISHED 버전 라벨 v{major}.{minor} (없으면 null)", example = "v1.2")
        String publishedVersion,

        @Schema(description = "편집 가능한 DRAFT 버전 존재 여부", example = "true")
        boolean hasDraft,

        @Schema(description = "그룹 내 전체 버전 수", example = "3")
        int versionCount
) {

    private static final Comparator<PageMeta> BY_VERSION_ASC = Comparator
            .comparingInt(PageMeta::getMajorVersion)
            .thenComparingInt(PageMeta::getMinorVersion);

    /**
     * 동일 groupId 의 버전 목록 하나에서 그룹 요약을 만든다.
     *
     * <p>대표 title·systemType·packageType 은 (활성 PUBLISHED 최신 → 없으면 전체 최신 버전) 기준이다.
     * publishedId·publishedVersion 은 화면 노출 중인 버전이 없으면 null.
     */
    public static PageMetaGroupResponse from(List<PageMeta> versions) {
        PageMeta latestPublished = versions.stream()
                .filter(m -> m.getMetaStatus() == MetaStatus.PUBLISHED && m.isActive())
                .max(BY_VERSION_ASC)
                .orElse(null);

        PageMeta representative = latestPublished != null
                ? latestPublished
                : versions.stream().max(BY_VERSION_ASC).orElseThrow();

        boolean hasDraft = versions.stream()
                .anyMatch(m -> m.getMetaStatus() == MetaStatus.DRAFT);

        return new PageMetaGroupResponse(
                representative.getGroupId(),
                representative.getTitle(),
                representative.getSystemType(),
                representative.getPackageType(),
                latestPublished != null ? latestPublished.getId() : null,
                latestPublished != null
                        ? "v" + latestPublished.getMajorVersion() + "." + latestPublished.getMinorVersion()
                        : null,
                hasDraft,
                versions.size());
    }
}
