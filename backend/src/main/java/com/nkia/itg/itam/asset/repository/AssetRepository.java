package com.nkia.itg.itam.asset.repository;

import com.nkia.itg.itam.asset.domain.AssetStatus;
import com.nkia.itg.itam.asset.domain.AssetType;
import com.nkia.itg.itam.asset.entity.Asset;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByAssetNo(String assetNo);

    /** 페이지 + 정렬. status·assetType·assigneeId 필터 옵션 (null 이면 무시). */
    @Query("""
            select a from Asset a
             where (:status     is null or a.status     = :status)
               and (:assetType  is null or a.assetType  = :assetType)
               and (:assigneeId is null or a.assigneeId = :assigneeId)
            """)
    Page<Asset> search(
            @Param("status")     AssetStatus status,
            @Param("assetType")  AssetType   assetType,
            @Param("assigneeId") String      assigneeId,
            Pageable             pageable
    );
}
