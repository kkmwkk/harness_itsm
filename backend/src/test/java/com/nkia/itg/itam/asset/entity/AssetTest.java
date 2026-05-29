package com.nkia.itg.itam.asset.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nkia.itg.itam.asset.domain.AssetStatus;
import com.nkia.itg.itam.asset.domain.AssetType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Asset 도메인 메서드 단위 테스트 (상태 전이 매트릭스)")
class AssetTest {

    private Asset assetWith(AssetStatus status) {
        return Asset.builder()
                .assetNo("AST-SAMPLE-1")
                .name("샘플 자산")
                .assetType(AssetType.HARDWARE)
                .status(status)
                .model("샘플-모델-X")
                .serialNo("SAMPLE-SN-0001")
                .category("노트북")
                .assigneeId("assignee-sample-1")
                .location("샘플 본사 3층")
                .pageMetaIdAtRegistration("itg-asset-v1-1")
                .build();
    }

    @Test
    @DisplayName("changeStatus — ACTIVE 에서 STORAGE 허용")
    void changeStatus_ACTIVE_to_STORAGE_허용() {
        // given
        Asset asset = assetWith(AssetStatus.ACTIVE);

        // when
        asset.changeStatus(AssetStatus.STORAGE);

        // then
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.STORAGE);
        assertThat(asset.getDisposedAt()).isNull();
    }

    @Test
    @DisplayName("changeStatus — ACTIVE 에서 RETIRED 허용, disposedAt set")
    void changeStatus_ACTIVE_to_RETIRED_허용_disposedAt_set() {
        // given
        Asset asset = assetWith(AssetStatus.ACTIVE);

        // when
        asset.changeStatus(AssetStatus.RETIRED);

        // then
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.RETIRED);
        assertThat(asset.getDisposedAt()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("changeStatus — STORAGE 에서 ACTIVE 허용")
    void changeStatus_STORAGE_to_ACTIVE_허용() {
        // given
        Asset asset = assetWith(AssetStatus.STORAGE);

        // when
        asset.changeStatus(AssetStatus.ACTIVE);

        // then
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ACTIVE);
    }

    @Test
    @DisplayName("changeStatus — REPLACED 에서 RETIRED 허용")
    void changeStatus_REPLACED_to_RETIRED_허용() {
        // given
        Asset asset = assetWith(AssetStatus.REPLACED);

        // when
        asset.changeStatus(AssetStatus.RETIRED);

        // then
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.RETIRED);
        assertThat(asset.getDisposedAt()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("changeStatus — RETIRED 에서 어떤 전이도 불허 (IllegalStateException)")
    void changeStatus_RETIRED_불허_IllegalStateException() {
        // given
        Asset asset = assetWith(AssetStatus.RETIRED);

        // when & then
        for (AssetStatus next : AssetStatus.values()) {
            assertThatThrownBy(() -> asset.changeStatus(next))
                    .as("RETIRED → %s", next)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RETIRED 자산은 상태를 변경할 수 없습니다");
        }
    }

    @Test
    @DisplayName("changeStatus — 매트릭스 외 전이 불허 (ACTIVE→REPLACED 허용, STORAGE→REPLACED 불허)")
    void changeStatus_매트릭스_외_전이_불허() {
        // given — ACTIVE → REPLACED 는 허용
        Asset active = assetWith(AssetStatus.ACTIVE);

        // when
        active.changeStatus(AssetStatus.REPLACED);

        // then
        assertThat(active.getStatus()).isEqualTo(AssetStatus.REPLACED);

        // given — STORAGE → REPLACED 는 불허
        Asset storage = assetWith(AssetStatus.STORAGE);

        // when & then
        assertThatThrownBy(() -> storage.changeStatus(AssetStatus.REPLACED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("허용되지 않은 상태 전이");
    }

    @Test
    @DisplayName("assignAssetNo — 이미 set 되어 있으면 예외")
    void assignAssetNo_이미_set_되어_있으면_예외() {
        // given
        Asset asset = assetWith(AssetStatus.ACTIVE);

        // when & then
        assertThatThrownBy(() -> asset.assignAssetNo("AST-SAMPLE-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("asset_no 는 이미 부여되었습니다");
    }

    @Test
    @DisplayName("assign — RETIRED 불허")
    void assign_RETIRED_불허() {
        // given
        Asset asset = assetWith(AssetStatus.RETIRED);

        // when & then
        assertThatThrownBy(() -> asset.assign("assignee-sample-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETIRED 자산은 담당자 할당 불가");
    }

    @Test
    @DisplayName("updateAttributes — RETIRED 불허")
    void updateAttributes_RETIRED_불허() {
        // given
        Asset asset = assetWith(AssetStatus.RETIRED);

        // when & then
        assertThatThrownBy(() -> asset.updateAttributes("새 이름", null, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETIRED 자산은 속성 변경 불가");
    }

    @Test
    @DisplayName("updateAttributes — 부분 업데이트, null 은 변경 없음")
    void updateAttributes_부분_업데이트_null_은_변경_없음() {
        // given
        Asset asset = assetWith(AssetStatus.ACTIVE);

        // when — name 만 변경, 나머지는 null 로 유지
        asset.updateAttributes("변경된 이름", null, null, null, null);

        // then
        assertThat(asset.getName()).isEqualTo("변경된 이름");
        assertThat(asset.getModel()).isEqualTo("샘플-모델-X");
        assertThat(asset.getSerialNo()).isEqualTo("SAMPLE-SN-0001");
        assertThat(asset.getCategory()).isEqualTo("노트북");
        assertThat(asset.getLocation()).isEqualTo("샘플 본사 3층");
    }

    @Test
    @DisplayName("상태 전이 시 disposedAt 이미 set 이면 보존")
    void 상태_전이_시_disposedAt_이미_set_이면_보존() {
        // given — disposedAt 이 이미 설정된 ACTIVE 자산
        LocalDate preset = LocalDate.of(2020, 1, 1);
        Asset asset = Asset.builder()
                .assetNo("AST-SAMPLE-3")
                .name("샘플 자산")
                .assetType(AssetType.HARDWARE)
                .status(AssetStatus.ACTIVE)
                .disposedAt(preset)
                .pageMetaIdAtRegistration("itg-asset-v1-1")
                .build();

        // when
        asset.changeStatus(AssetStatus.RETIRED);

        // then — RETIRED 전이에도 기존 disposedAt 보존
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.RETIRED);
        assertThat(asset.getDisposedAt()).isEqualTo(preset);
    }
}
