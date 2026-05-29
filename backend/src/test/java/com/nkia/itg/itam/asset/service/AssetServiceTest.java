package com.nkia.itg.itam.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.fixture.AssetFixture;
import com.nkia.itg.itam.asset.domain.AssetStatus;
import com.nkia.itg.itam.asset.domain.AssetType;
import com.nkia.itg.itam.asset.dto.AssetAssignRequest;
import com.nkia.itg.itam.asset.dto.AssetCreateRequest;
import com.nkia.itg.itam.asset.dto.AssetResponse;
import com.nkia.itg.itam.asset.dto.AssetStatusChangeRequest;
import com.nkia.itg.itam.asset.dto.AssetSummary;
import com.nkia.itg.itam.asset.dto.AssetUpdateRequest;
import com.nkia.itg.itam.asset.entity.Asset;
import com.nkia.itg.itam.asset.repository.AssetRepository;
import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.dto.PageMetaResponse;
import com.nkia.itg.meta.service.MetaService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    AssetRepository assetRepository;

    @Mock
    MetaService metaService;

    @InjectMocks
    AssetService assetService;

    private AssetCreateRequest createRequest() {
        return new AssetCreateRequest(
                "샘플 자산", AssetType.HARDWARE, "SAMPLE-MODEL", "SN-SAMPLE-1",
                "노트북", "assignee-sample-1", "본사 3층",
                LocalDate.of(2026, 1, 15), "itg-asset");
    }

    private PageMetaResponse publishedMeta(String id) {
        return new PageMetaResponse(
                id, "ITAM 자산원장", SystemType.ITAM, PackageType.PACKAGE,
                "itg-asset", 1, 1, MetaStatus.PUBLISHED,
                Map.of("api", "/api/assets"), true,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private PageMetaResponse deprecatedMeta(String id) {
        return new PageMetaResponse(
                id, "ITAM 자산원장(구버전)", SystemType.ITAM, PackageType.PACKAGE,
                "itg-asset", 1, 1, MetaStatus.DEPRECATED,
                Map.of("api", "/api/assets"), true,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    /** save 가 인자 asset 을 그대로 반환하되 지정한 id 를 부여하도록 stub (IDENTITY 모사). */
    private void stubSaveWithId(long id) {
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> {
            Asset a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", id);
            return a;
        });
    }

    @Test
    @DisplayName("create 시점에 MetaService.getActive 를 호출해 pageMetaIdAtRegistration 을 저장한다")
    void create_시점에_MetaService_getActive_호출_pageMetaIdAtRegistration_저장() {
        when(metaService.getActive("itg-asset")).thenReturn(publishedMeta("itg-asset-v1-1"));
        stubSaveWithId(1L);

        AssetResponse res = assetService.create(createRequest());

        assertThat(res.pageMetaIdAtRegistration()).isEqualTo("itg-asset-v1-1");
        verify(metaService).getActive("itg-asset");
    }

    @Test
    @DisplayName("create 시 assetNo 를 AST-{id5} 패턴으로 자동 부여한다")
    void create_assetNo_AST_5자리_자동_부여() {
        when(metaService.getActive("itg-asset")).thenReturn(publishedMeta("itg-asset-v1-1"));
        stubSaveWithId(42L);

        AssetResponse res = assetService.create(createRequest());

        assertThat(res.assetNo()).isEqualTo("AST-00042");
    }

    @Test
    @DisplayName("create 시 PUBLISHED 메타가 없으면 ITGException 을 그대로 전파하고 save 하지 않는다")
    void create_PUBLISHED_메타_없으면_ITGException_그대로_전파() {
        when(metaService.getActive("itg-asset")).thenThrow(new ITGException(
                "META_NOT_PUBLISHED", "배포된 메타가 없습니다: itg-asset", HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> assetService.create(createRequest()))
                .isInstanceOf(ITGException.class)
                .satisfies(e -> assertThat(((ITGException) e).getErrorCode()).isEqualTo("META_NOT_PUBLISHED"));

        verify(assetRepository, never()).save(any());
    }

    @Test
    @DisplayName("getById 는 자산이 없으면 ASSET_NOT_FOUND(404) 를 던진다")
    void getById_없으면_ASSET_NOT_FOUND_404() {
        when(assetRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetService.getById(999L))
                .isInstanceOf(ITGException.class)
                .satisfies(e -> {
                    assertThat(((ITGException) e).getErrorCode()).isEqualTo("ASSET_NOT_FOUND");
                    assertThat(((ITGException) e).getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    @DisplayName("update 는 RETIRED 자산이면 IllegalStateException 을 던진다")
    void update_RETIRED_불허_IllegalStateException() {
        when(assetRepository.findById(1L)).thenReturn(Optional.of(AssetFixture.retired()));

        assertThatThrownBy(() -> assetService.update(1L,
                new AssetUpdateRequest("변경", null, null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("changeStatus ACTIVE→RETIRED 는 허용되고 disposedAt 이 설정된다")
    void changeStatus_ACTIVE_to_RETIRED_허용_disposedAt_set() {
        when(assetRepository.findById(1L)).thenReturn(Optional.of(AssetFixture.active()));

        AssetResponse res = assetService.changeStatus(1L,
                new AssetStatusChangeRequest(AssetStatus.RETIRED));

        assertThat(res.status()).isEqualTo(AssetStatus.RETIRED);
        assertThat(res.disposedAt()).isNotNull();
    }

    @Test
    @DisplayName("changeStatus RETIRED→ACTIVE 는 거부된다(400)")
    void changeStatus_RETIRED_to_ACTIVE_거부_400() {
        when(assetRepository.findById(1L)).thenReturn(Optional.of(AssetFixture.retired()));

        assertThatThrownBy(() -> assetService.changeStatus(1L,
                new AssetStatusChangeRequest(AssetStatus.ACTIVE)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("assign 은 RETIRED 자산이면 거부된다")
    void assign_RETIRED_불허() {
        when(assetRepository.findById(1L)).thenReturn(Optional.of(AssetFixture.retired()));

        assertThatThrownBy(() -> assetService.assign(1L,
                new AssetAssignRequest("assignee-sample-2")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getRegistrationMeta 는 등록 시점 메타를 DEPRECATED 라도 그대로 반환한다(이력 복원)")
    void getRegistrationMeta_등록_시점의_메타_반환() {
        Asset asset = AssetFixture.active();   // pageMetaIdAtRegistration = "itg-asset-v1-1"
        when(assetRepository.findById(1L)).thenReturn(Optional.of(asset));
        when(metaService.getById("itg-asset-v1-1")).thenReturn(deprecatedMeta("itg-asset-v1-1"));

        PageMetaResponse res = assetService.getRegistrationMeta(1L);

        assertThat(res.id()).isEqualTo("itg-asset-v1-1");
        assertThat(res.metaStatus()).isEqualTo(MetaStatus.DEPRECATED);
        verify(metaService).getById("itg-asset-v1-1");
    }

    @Test
    @DisplayName("search 는 파라미터를 repository 에 그대로 전달한다")
    void search_파라미터_repository_에_전달() {
        when(assetRepository.search(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of()));

        Page<AssetSummary> result =
                assetService.search(AssetStatus.ACTIVE, AssetType.HARDWARE, "assignee-sample-1", 0, 20);

        assertThat(result).isEmpty();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(assetRepository).search(
                eq(AssetStatus.ACTIVE), eq(AssetType.HARDWARE), eq("assignee-sample-1"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }
}
