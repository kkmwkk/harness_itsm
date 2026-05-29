package com.nkia.itg.fixture;

import com.nkia.itg.itam.asset.domain.AssetStatus;
import com.nkia.itg.itam.asset.domain.AssetType;
import com.nkia.itg.itam.asset.entity.Asset;
import java.time.LocalDate;

public final class AssetFixture {

    private AssetFixture() {
    }

    public static Asset.AssetBuilder baseBuilder() {
        return Asset.builder()
                .assetNo("AST-99999")
                .name("샘플 자산")
                .assetType(AssetType.HARDWARE)
                .status(AssetStatus.ACTIVE)
                .model("SAMPLE-MODEL")
                .serialNo("SN-SAMPLE-1")
                .category("노트북")
                .assigneeId("assignee-sample-1")
                .location("본사 3층")
                .acquiredAt(LocalDate.of(2026, 1, 15))
                .pageMetaIdAtRegistration("itg-asset-v1-1");
    }

    public static Asset active() {
        return baseBuilder().status(AssetStatus.ACTIVE).build();
    }

    public static Asset storage() {
        return baseBuilder().status(AssetStatus.STORAGE).build();
    }

    public static Asset replaced() {
        return baseBuilder().status(AssetStatus.REPLACED).build();
    }

    /** ACTIVE → RETIRED 도메인 전이로 disposedAt 까지 자동 set. */
    public static Asset retired() {
        Asset a = active();
        a.changeStatus(AssetStatus.RETIRED);
        return a;
    }
}
