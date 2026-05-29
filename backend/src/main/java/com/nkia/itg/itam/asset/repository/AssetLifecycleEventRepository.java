package com.nkia.itg.itam.asset.repository;

import com.nkia.itg.itam.asset.entity.AssetLifecycleEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetLifecycleEventRepository extends JpaRepository<AssetLifecycleEvent, Long> {

    /** 자산별 이력 — 이벤트 일자 내림차순. */
    List<AssetLifecycleEvent> findByAssetIdOrderByEventDateDescIdDesc(Long assetId);
}
