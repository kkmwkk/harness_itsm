package com.nkia.itg.itam.asset.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.itam.asset.domain.AssetStatus;
import com.nkia.itg.itam.asset.domain.AssetType;
import com.nkia.itg.itam.asset.dto.AssetAssignRequest;
import com.nkia.itg.itam.asset.dto.AssetCreateRequest;
import com.nkia.itg.itam.asset.dto.AssetResponse;
import com.nkia.itg.itam.asset.dto.AssetStatusChangeRequest;
import com.nkia.itg.itam.asset.dto.AssetSummary;
import com.nkia.itg.itam.asset.dto.AssetUpdateRequest;
import com.nkia.itg.itam.asset.domain.AssetLifecycleEventType;
import com.nkia.itg.itam.asset.entity.Asset;
import com.nkia.itg.itam.asset.entity.AssetLifecycleEvent;
import com.nkia.itg.itam.asset.repository.AssetRepository;
import com.nkia.itg.itam.category.repository.AssetCategoryRepository;
import com.nkia.itg.meta.dto.PageMetaResponse;
import com.nkia.itg.meta.service.MetaService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final MetaService metaService;
    private final AssetCategoryRepository assetCategoryRepository;
    private final AssetLifecycleService assetLifecycleService;

    /**
     * 신규 등록. 등록 시점의 PUBLISHED 메타 id 를 캡처하여 pageMetaIdAtRegistration 에 보존한다
     * (PRD §5-2 이력 복원). 배포된 메타가 없으면 MetaService.getActive 가 META_NOT_PUBLISHED 를
     * 던지며, 자산 생성도 그대로 거부된다. asset_no 는 AST-{id5} 패턴으로 save 후 dirty checking 부여.
     */
    public AssetResponse create(AssetCreateRequest req) {
        PageMetaResponse activeMeta = metaService.getActive(req.pageGroupId());
        validateCategoryCode(req.categoryCode());

        Asset asset = Asset.builder()
                .assetNo(null)
                .name(req.name())
                .assetType(req.assetType())
                .status(AssetStatus.ACTIVE)
                .model(req.model())
                .serialNo(req.serialNo())
                .category(req.category())
                .categoryCode(req.categoryCode())
                .assigneeId(req.assigneeId())
                .location(req.location())
                .acquiredAt(req.acquiredAt())
                .pageMetaIdAtRegistration(activeMeta.id())
                .build();

        Asset saved = assetRepository.save(asset);
        saved.assignAssetNo("AST-" + String.format("%05d", saved.getId()));
        return AssetResponse.from(saved);
    }

    /** categoryCode 가 주어지면 asset_category 에 존재하는지 검증한다 (null·blank 면 검증 생략). */
    private void validateCategoryCode(String categoryCode) {
        if (categoryCode != null && !categoryCode.isBlank()
                && !assetCategoryRepository.existsById(categoryCode)) {
            throw new ITGException("ASSET_CATEGORY_NOT_FOUND",
                    "자산 분류를 찾을 수 없습니다: " + categoryCode, HttpStatus.NOT_FOUND);
        }
    }

    /**
     * 자산 이력 이벤트 기록 — AssetLifecycleService 위임. 자산 존재 검증 후 위임한다.
     * event_type 은 {@link AssetLifecycleEventType} Enum 으로만 받는다 (String 하드코딩 금지).
     */
    public AssetLifecycleEvent recordLifecycleEvent(Long assetId, AssetLifecycleEventType eventType,
                                                    Long byUserId, Map<String, Object> payload) {
        loadOrThrow(assetId);
        return assetLifecycleService.record(assetId, eventType, byUserId, payload);
    }

    /** 자산 이력 이벤트 목록 (이벤트 일자 내림차순). 자산 존재 검증 후 위임 조회. */
    @Transactional(readOnly = true)
    public List<AssetLifecycleEvent> listLifecycleEvents(Long assetId) {
        loadOrThrow(assetId);
        return assetLifecycleService.findByAsset(assetId);
    }

    @Transactional(readOnly = true)
    public AssetResponse getById(Long id) {
        return AssetResponse.from(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public AssetResponse getByAssetNo(String assetNo) {
        Asset asset = assetRepository.findByAssetNo(assetNo)
                .orElseThrow(() -> new ITGException(
                        "ASSET_NOT_FOUND",
                        "자산을 찾을 수 없습니다: " + assetNo,
                        HttpStatus.NOT_FOUND));
        return AssetResponse.from(asset);
    }

    @Transactional(readOnly = true)
    public Page<AssetSummary> search(
            AssetStatus status, AssetType assetType, String assigneeId,
            int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return assetRepository.search(status, assetType, assigneeId, pageable)
                .map(AssetSummary::from);
    }

    /** 속성 수정 (부분). RETIRED 면 도메인 예외 → 400. */
    public AssetResponse update(Long id, AssetUpdateRequest req) {
        Asset asset = loadOrThrow(id);
        asset.updateAttributes(req.name(), req.model(), req.serialNo(), req.category(), req.location());
        return AssetResponse.from(asset);
    }

    /** 상태 전이 (도메인 메서드 호출). 매트릭스 위반 시 IllegalStateException → 400. */
    public AssetResponse changeStatus(Long id, AssetStatusChangeRequest req) {
        Asset asset = loadOrThrow(id);
        asset.changeStatus(req.next());
        return AssetResponse.from(asset);
    }

    /** 담당자 할당(또는 해제). RETIRED 면 도메인 예외 → 400. */
    public AssetResponse assign(Long id, AssetAssignRequest req) {
        Asset asset = loadOrThrow(id);
        asset.assign(req.assigneeId());
        return AssetResponse.from(asset);
    }

    /**
     * 이력 복원용: 등록 시점의 메타를 반환한다. 상태 필터링을 하지 않으므로 DEPRECATED·ARCHIVED
     * 라도 그대로 반환된다 — 과거 등록 건의 화면을 당시 메타로 복원하기 위함 (PRD §5-2).
     */
    @Transactional(readOnly = true)
    public PageMetaResponse getRegistrationMeta(Long assetId) {
        Asset asset = loadOrThrow(assetId);
        return metaService.getById(asset.getPageMetaIdAtRegistration());
    }

    private Asset loadOrThrow(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ITGException(
                        "ASSET_NOT_FOUND",
                        "자산을 찾을 수 없습니다: " + id,
                        HttpStatus.NOT_FOUND));
    }
}
