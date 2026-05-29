package com.nkia.itg.itam.asset.service;

import com.nkia.itg.itam.asset.domain.AssetLifecycleEventType;
import com.nkia.itg.itam.asset.entity.AssetLifecycleEvent;
import com.nkia.itg.itam.asset.repository.AssetLifecycleEventRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자산 이력 이벤트 기록·조회. event_type 은 {@link AssetLifecycleEventType} Enum 으로만 받는다
 * (String 하드코딩 금지). 자산 존재 검증은 호출측(AssetService)이 책임진다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AssetLifecycleService {

    private final AssetLifecycleEventRepository assetLifecycleEventRepository;

    /** 이력 이벤트 한 건 기록. eventDate 미지정 시 오늘로 기록. */
    public AssetLifecycleEvent record(Long assetId, AssetLifecycleEventType eventType,
                                      Long byUserId, Map<String, Object> payload) {
        AssetLifecycleEvent event = AssetLifecycleEvent.builder()
                .assetId(assetId)
                .eventType(eventType)
                .eventDate(LocalDate.now())
                .byUserId(byUserId)
                .payload(payload)
                .build();
        return assetLifecycleEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<AssetLifecycleEvent> findByAsset(Long assetId) {
        return assetLifecycleEventRepository.findByAssetIdOrderByEventDateDescIdDesc(assetId);
    }
}
