package com.nkia.itg.meta.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.dto.PageMetaCreateRequest;
import com.nkia.itg.meta.dto.PageMetaResponse;
import com.nkia.itg.meta.dto.PageMetaVersionResponse;
import com.nkia.itg.meta.entity.PageMeta;
import com.nkia.itg.meta.repository.MetaRepository;
import com.nkia.itg.meta.service.MetaValidationService.ValidationIssue;
import com.nkia.itg.meta.service.MetaValidationService.ValidationResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MetaService {

    private final MetaRepository metaRepository;
    private final MetaValidationService metaValidationService;

    /**
     * 신규 메타를 DRAFT 로 생성한다(No-code 편집기 기반).
     *
     * <p>클라이언트가 어떤 metaStatus 를 보내든 항상 DRAFT 로 강제한다(ADR-006).
     * 저장 전 {@link MetaValidationService} 로 형식을 검증하며, 동일 groupId 의 동일
     * (major, minor) 는 DB UNIQUE 제약에 의해 DataIntegrityViolation 으로 거부된다.
     */
    public PageMetaResponse create(PageMetaCreateRequest req) {
        // 클라이언트가 metaStatus 를 생략하거나 다른 값을 보내도 항상 DRAFT 기준으로 검증·저장한다.
        PageMetaCreateRequest normalized = new PageMetaCreateRequest(
                req.id(), req.title(), req.systemType(), req.packageType(), req.groupId(),
                req.majorVersion(), req.minorVersion(), MetaStatus.DRAFT, req.resolvedMetaJson());

        ValidationResult result = metaValidationService.validate(normalized);
        if (!result.valid()) {
            String detail = result.issues().stream()
                    .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                    .map(i -> i.path() + ": " + i.message())
                    .collect(Collectors.joining("; "));
            throw new ITGException(
                    "META_VALIDATION_FAILED",
                    "메타 검증에 실패했습니다: " + detail,
                    HttpStatus.BAD_REQUEST);
        }

        PageMeta entity = PageMeta.builder()
                .id(req.id())
                .title(req.title())
                .systemType(req.systemType())
                .packageType(req.packageType())
                .groupId(req.groupId())
                .majorVersion(req.majorVersion())
                .minorVersion(req.minorVersion())
                .metaStatus(MetaStatus.DRAFT)   // 항상 DRAFT 강제 (ADR-006)
                .metaJson(req.resolvedMetaJson())
                .active(true)
                .build();

        return PageMetaResponse.from(metaRepository.save(entity));
    }

    /**
     * DRAFT 메타의 metaJson 본문을 교체한다. PUBLISHED·DEPRECATED·ARCHIVED 는 거부한다.
     */
    public PageMetaResponse updateBody(String metaId, Map<String, Object> body) {
        PageMeta target = loadOrThrow(metaId);
        target.replaceBody(body);   // DRAFT 가 아니면 IllegalStateException
        return PageMetaResponse.from(metaRepository.save(target));
    }

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
