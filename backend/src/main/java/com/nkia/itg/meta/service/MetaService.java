package com.nkia.itg.meta.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.dto.PageMetaResponse;
import com.nkia.itg.meta.dto.PageMetaVersionResponse;
import com.nkia.itg.meta.entity.PageMeta;
import com.nkia.itg.meta.repository.MetaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MetaService {

    private final MetaRepository metaRepository;

    @Transactional(readOnly = true)
    public PageMetaResponse getActive(String groupId) {
        PageMeta meta = metaRepository
                .findTopByGroupIdAndMetaStatusAndActiveTrueOrderByMajorVersionDescMinorVersionDesc(
                        groupId, MetaStatus.PUBLISHED)
                .orElseThrow(() -> new ITGException(
                        "META_NOT_PUBLISHED",
                        "배포된 메타가 없습니다: " + groupId,
                        HttpStatus.NOT_FOUND));
        return PageMetaResponse.from(meta);
    }

    @Transactional(readOnly = true)
    public PageMetaResponse getById(String metaId) {
        return PageMetaResponse.from(loadOrThrow(metaId));
    }

    @Transactional(readOnly = true)
    public List<PageMetaVersionResponse> getVersions(String groupId) {
        return metaRepository.findAllByGroupIdOrderByMajorVersionDescMinorVersionDesc(groupId)
                .stream()
                .map(PageMetaVersionResponse::from)
                .toList();
    }

    public PageMetaResponse publish(String metaId) {
        PageMeta target = loadOrThrow(metaId);

        if (target.getMetaStatus() != MetaStatus.DRAFT) {
            throw new ITGException(
                    "INVALID_STATUS",
                    "DRAFT 상태만 배포 가능합니다. 현재 상태: " + target.getMetaStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        metaRepository.deprecatePublished(target.getGroupId(), target.getId());

        target.publish();

        return PageMetaResponse.from(metaRepository.save(target));
    }

    public PageMetaResponse archive(String metaId) {
        PageMeta target = loadOrThrow(metaId);
        if (target.getMetaStatus() == MetaStatus.ARCHIVED) {
            return PageMetaResponse.from(target);
        }
        target.archive();
        return PageMetaResponse.from(metaRepository.save(target));
    }

    public PageMetaResponse copy(String metaId) {
        PageMeta origin = loadOrThrow(metaId);

        int nextMinor = metaRepository
                .findMaxMinorVersion(origin.getGroupId(), origin.getMajorVersion())
                .orElse(0) + 1;

        String newId = "%s-v%d-%d".formatted(origin.getGroupId(), origin.getMajorVersion(), nextMinor);
        PageMeta copied = origin.copyAs(newId, nextMinor);

        return PageMetaResponse.from(metaRepository.save(copied));
    }

    @Transactional(readOnly = true)
    public List<PageMetaResponse> getActiveBySystem(SystemType systemType) {
        return metaRepository
                .findAllBySystemTypeAndMetaStatusAndActiveTrueOrderByGroupIdAscMajorVersionDescMinorVersionDesc(
                        systemType, MetaStatus.PUBLISHED)
                .stream()
                .map(PageMetaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PageMetaResponse> getByPackage(PackageType packageType) {
        return metaRepository
                .findAllByPackageTypeOrderByGroupIdAscMajorVersionDescMinorVersionDesc(packageType)
                .stream()
                .map(PageMetaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PageMetaResponse> getBySystemAndPackage(SystemType systemType, PackageType packageType) {
        return metaRepository
                .findAllBySystemTypeAndPackageTypeOrderByGroupIdAscMajorVersionDescMinorVersionDesc(
                        systemType, packageType)
                .stream()
                .map(PageMetaResponse::from)
                .toList();
    }

    private PageMeta loadOrThrow(String metaId) {
        return metaRepository.findById(metaId)
                .orElseThrow(() -> new ITGException(
                        "META_NOT_FOUND",
                        "메타를 찾을 수 없습니다: " + metaId,
                        HttpStatus.NOT_FOUND));
    }
}
