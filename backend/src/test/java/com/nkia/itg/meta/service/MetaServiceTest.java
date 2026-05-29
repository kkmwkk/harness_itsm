package com.nkia.itg.meta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.fixture.MetaFixture;
import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.dto.PageMetaCreateRequest;
import com.nkia.itg.meta.dto.PageMetaResponse;
import com.nkia.itg.meta.dto.PageMetaVersionResponse;
import com.nkia.itg.meta.entity.PageMeta;
import com.nkia.itg.meta.repository.MetaRepository;
import com.nkia.itg.meta.service.MetaValidationService.ValidationResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@DisplayName("MetaService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class MetaServiceTest {

    @Mock
    private MetaRepository metaRepository;

    @Mock
    private MetaValidationService metaValidationService;

    @InjectMocks
    private MetaService metaService;

    private PageMetaCreateRequest createRequest(MetaStatus requestedStatus) {
        return new PageMetaCreateRequest(
                "itg-test-v1-1", "테스트",
                SystemType.COMMON, PackageType.PACKAGE, "itg-test",
                1, 1, requestedStatus,
                Map.of("api", "/api/test",
                        "grid", Map.of("columns", List.of()),
                        "form", Map.of("layout", "two-column", "fields", List.of())));
    }

    @Test
    @DisplayName("create — 클라이언트가 PUBLISHED 를 보내도 항상 DRAFT 로 강제 저장")
    void create_metaStatus_DRAFT_강제() {
        // given — 클라이언트는 PUBLISHED 를 요청하지만 무시되어야 한다
        when(metaValidationService.validate(any(PageMetaCreateRequest.class)))
                .thenReturn(new ValidationResult(true, List.of()));
        when(metaRepository.save(any(PageMeta.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        PageMetaResponse response = metaService.create(createRequest(MetaStatus.PUBLISHED));

        // then
        assertThat(response.metaStatus()).isEqualTo(MetaStatus.DRAFT);
        assertThat(response.id()).isEqualTo("itg-test-v1-1");

        ArgumentCaptor<PageMeta> captor = ArgumentCaptor.forClass(PageMeta.class);
        verify(metaRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getMetaStatus()).isEqualTo(MetaStatus.DRAFT);
    }

    @Test
    @DisplayName("create — 검증 실패 시 META_VALIDATION_FAILED 400, 저장하지 않음")
    void create_검증_실패_시_거부() {
        // given
        when(metaValidationService.validate(any(PageMetaCreateRequest.class)))
                .thenReturn(new ValidationResult(false, List.of(
                        new MetaValidationService.ValidationIssue(
                                MetaValidationService.ValidationIssue.Severity.ERROR,
                                "systemType", "INVALID_SYSTEM_TYPE", "systemType 오류"))));

        // when & then
        assertThatThrownBy(() -> metaService.create(createRequest(MetaStatus.DRAFT)))
                .isInstanceOf(ITGException.class)
                .satisfies(ex -> {
                    ITGException itg = (ITGException) ex;
                    assertThat(itg.getErrorCode()).isEqualTo("META_VALIDATION_FAILED");
                    assertThat(itg.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verify(metaRepository, never()).save(any(PageMeta.class));
    }

    @Test
    @DisplayName("updateBody — DRAFT 메타의 본문을 교체한다")
    void updateBody_DRAFT_본문_교체() {
        // given
        PageMeta draft = MetaFixture.draft("itg-ticket", 1, 2);
        when(metaRepository.findById("itg-ticket-v1-2")).thenReturn(Optional.of(draft));
        when(metaRepository.save(any(PageMeta.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        PageMetaResponse response = metaService.updateBody(
                "itg-ticket-v1-2", Map.of("api", "/api/changed"));

        // then
        assertThat(response.metaStatus()).isEqualTo(MetaStatus.DRAFT);
        assertThat(response.metaJson()).containsEntry("api", "/api/changed");
        verify(metaRepository, times(1)).save(draft);
    }

    @Test
    @DisplayName("updateBody — DRAFT 가 아닌 메타는 IllegalStateException 으로 거부")
    void updateBody_DRAFT_아닌_메타_거부() {
        // given
        PageMeta published = MetaFixture.published("itg-ticket", 1, 1);
        when(metaRepository.findById("itg-ticket-v1-1")).thenReturn(Optional.of(published));

        // when & then
        assertThatThrownBy(() -> metaService.updateBody("itg-ticket-v1-1", Map.of("api", "/api/x")))
                .isInstanceOf(IllegalStateException.class);
        verify(metaRepository, never()).save(any(PageMeta.class));
    }

    @Test
    @DisplayName("publish — 성공 시 기존 PUBLISHED 를 DEPRECATED 처리하고 본인은 PUBLISHED")
    void publish_성공_시_기존_PUBLISHED_를_DEPRECATED_처리하고_본인은_PUBLISHED() {
        // given
        PageMeta draft = MetaFixture.draft("itg-ticket", 1, 2);
        when(metaRepository.findById("itg-ticket-v1-2")).thenReturn(Optional.of(draft));
        when(metaRepository.deprecatePublished("itg-ticket", "itg-ticket-v1-2")).thenReturn(1);
        when(metaRepository.save(any(PageMeta.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        PageMetaResponse response = metaService.publish("itg-ticket-v1-2");

        // then
        assertThat(response.metaStatus()).isEqualTo(MetaStatus.PUBLISHED);
        assertThat(response.id()).isEqualTo("itg-ticket-v1-2");
        verify(metaRepository, times(1)).deprecatePublished("itg-ticket", "itg-ticket-v1-2");
        verify(metaRepository, times(1)).save(draft);
    }

    @Test
    @DisplayName("publish — DRAFT 가 아닌 메타는 ITGException INVALID_STATUS 400")
    void publish_DRAFT_가_아닌_메타는_ITGException_INVALID_STATUS_400() {
        // given
        PageMeta published = MetaFixture.published("itg-ticket", 1, 1);
        when(metaRepository.findById("itg-ticket-v1-1")).thenReturn(Optional.of(published));

        // when & then
        assertThatThrownBy(() -> metaService.publish("itg-ticket-v1-1"))
                .isInstanceOf(ITGException.class)
                .satisfies(ex -> {
                    ITGException itg = (ITGException) ex;
                    assertThat(itg.getErrorCode()).isEqualTo("INVALID_STATUS");
                    assertThat(itg.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verify(metaRepository, never()).deprecatePublished(anyString(), anyString());
        verify(metaRepository, never()).save(any(PageMeta.class));
    }

    @Test
    @DisplayName("publish — 존재하지 않는 메타는 ITGException META_NOT_FOUND 404")
    void publish_존재하지_않는_메타는_ITGException_META_NOT_FOUND_404() {
        // given
        when(metaRepository.findById("missing")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> metaService.publish("missing"))
                .isInstanceOf(ITGException.class)
                .satisfies(ex -> {
                    ITGException itg = (ITGException) ex;
                    assertThat(itg.getErrorCode()).isEqualTo("META_NOT_FOUND");
                    assertThat(itg.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        verify(metaRepository, never()).deprecatePublished(anyString(), anyString());
        verify(metaRepository, never()).save(any(PageMeta.class));
    }

    @Test
    @DisplayName("copy — 새 minor 는 기존 최대 minor + 1 이고 항상 DRAFT")
    void copy_새_minor_는_기존_최대_minor_플러스_1_이고_DRAFT() {
        // given
        PageMeta origin = MetaFixture.published("grp", 1, 2);
        when(metaRepository.findById("grp-v1-2")).thenReturn(Optional.of(origin));
        when(metaRepository.findMaxMinorVersion("grp", 1)).thenReturn(Optional.of(2));
        when(metaRepository.save(any(PageMeta.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        PageMetaResponse response = metaService.copy("grp-v1-2");

        // then
        assertThat(response.id()).isEqualTo("grp-v1-3");
        assertThat(response.minorVersion()).isEqualTo(3);
        assertThat(response.majorVersion()).isEqualTo(1);
        assertThat(response.groupId()).isEqualTo("grp");
        assertThat(response.metaStatus()).isEqualTo(MetaStatus.DRAFT);
    }

    @Test
    @DisplayName("copy — 동일 major 의 메타가 없으면 새 minor 는 1")
    void copy_매핑이_없으면_minor_는_1() {
        // given
        PageMeta origin = MetaFixture.draft("grp", 1, 1);
        when(metaRepository.findById("grp-v1-1")).thenReturn(Optional.of(origin));
        when(metaRepository.findMaxMinorVersion("grp", 1)).thenReturn(Optional.empty());
        when(metaRepository.save(any(PageMeta.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        PageMetaResponse response = metaService.copy("grp-v1-1");

        // then
        assertThat(response.id()).isEqualTo("grp-v1-1");
        assertThat(response.minorVersion()).isEqualTo(1);
        assertThat(response.metaStatus()).isEqualTo(MetaStatus.DRAFT);
    }

    @Test
    @DisplayName("copy — 원본이 존재하지 않으면 META_NOT_FOUND")
    void copy_원본_없으면_META_NOT_FOUND() {
        // given
        when(metaRepository.findById("missing")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> metaService.copy("missing"))
                .isInstanceOf(ITGException.class)
                .satisfies(ex -> {
                    ITGException itg = (ITGException) ex;
                    assertThat(itg.getErrorCode()).isEqualTo("META_NOT_FOUND");
                    assertThat(itg.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
        verify(metaRepository, never()).save(any(PageMeta.class));
    }

    @Test
    @DisplayName("getActive — 그룹의 PUBLISHED 가 없으면 META_NOT_PUBLISHED 404")
    void getActive_그룹의_PUBLISHED_가_없으면_META_NOT_PUBLISHED_404() {
        // given
        when(metaRepository
                .findTopByGroupIdAndMetaStatusAndActiveTrueOrderByMajorVersionDescMinorVersionDesc(
                        "itg-ticket", MetaStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> metaService.getActive("itg-ticket"))
                .isInstanceOf(ITGException.class)
                .satisfies(ex -> {
                    ITGException itg = (ITGException) ex;
                    assertThat(itg.getErrorCode()).isEqualTo("META_NOT_PUBLISHED");
                    assertThat(itg.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    @DisplayName("getActive — 정상 경로에서 PUBLISHED 최신 1건을 응답 DTO 로 반환")
    void getActive_정상_경로() {
        // given
        PageMeta latest = MetaFixture.published("itg-ticket", 1, 3);
        when(metaRepository
                .findTopByGroupIdAndMetaStatusAndActiveTrueOrderByMajorVersionDescMinorVersionDesc(
                        "itg-ticket", MetaStatus.PUBLISHED))
                .thenReturn(Optional.of(latest));

        // when
        PageMetaResponse response = metaService.getActive("itg-ticket");

        // then
        assertThat(response.id()).isEqualTo("itg-ticket-v1-3");
        assertThat(response.groupId()).isEqualTo("itg-ticket");
        assertThat(response.metaStatus()).isEqualTo(MetaStatus.PUBLISHED);
        assertThat(response.majorVersion()).isEqualTo(1);
        assertThat(response.minorVersion()).isEqualTo(3);
        assertThat(response.versionLabel()).isEqualTo("v1.3");
    }

    @Test
    @DisplayName("getVersions — 정렬된 결과를 경량 DTO 로 변환하며 metaJson 필드를 포함하지 않는다")
    void getVersions_정렬_및_경량_DTO_변환() {
        // given
        PageMeta v13 = MetaFixture.draft("itg-ticket", 1, 3);
        PageMeta v12 = MetaFixture.published("itg-ticket", 1, 2);
        PageMeta v11 = MetaFixture.archived("itg-ticket", 1, 1);
        when(metaRepository.findAllByGroupIdOrderByMajorVersionDescMinorVersionDesc("itg-ticket"))
                .thenReturn(List.of(v13, v12, v11));

        // when
        List<PageMetaVersionResponse> versions = metaService.getVersions("itg-ticket");

        // then
        assertThat(versions).hasSize(3);
        assertThat(versions).extracting(PageMetaVersionResponse::id)
                .containsExactly("itg-ticket-v1-3", "itg-ticket-v1-2", "itg-ticket-v1-1");
        assertThat(versions).extracting(PageMetaVersionResponse::metaStatus)
                .containsExactly(MetaStatus.DRAFT, MetaStatus.PUBLISHED, MetaStatus.ARCHIVED);
        assertThat(PageMetaVersionResponse.class.getRecordComponents())
                .extracting(rc -> rc.getName())
                .doesNotContain("metaJson");
    }

    @Test
    @DisplayName("archive — 어느 상태든 ARCHIVED 로 변환되며 deprecatePublished 는 호출되지 않는다")
    void archive_어떤_상태든_ARCHIVED_변환() {
        // given
        PageMeta draft = MetaFixture.draft("g", 1, 1);
        PageMeta published = MetaFixture.published("g", 1, 2);
        PageMeta deprecated = MetaFixture.baseBuilder("g", 1, 3)
                .metaStatus(MetaStatus.DEPRECATED)
                .build();
        when(metaRepository.findById("g-v1-1")).thenReturn(Optional.of(draft));
        when(metaRepository.findById("g-v1-2")).thenReturn(Optional.of(published));
        when(metaRepository.findById("g-v1-3")).thenReturn(Optional.of(deprecated));
        when(metaRepository.save(any(PageMeta.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        PageMetaResponse r1 = metaService.archive("g-v1-1");
        PageMetaResponse r2 = metaService.archive("g-v1-2");
        PageMetaResponse r3 = metaService.archive("g-v1-3");

        // then
        assertThat(r1.metaStatus()).isEqualTo(MetaStatus.ARCHIVED);
        assertThat(r2.metaStatus()).isEqualTo(MetaStatus.ARCHIVED);
        assertThat(r3.metaStatus()).isEqualTo(MetaStatus.ARCHIVED);
        verify(metaRepository, never()).deprecatePublished(anyString(), anyString());
    }

    @Test
    @DisplayName("getActiveBySystem / getByPackage / getBySystemAndPackage — 결과를 PageMetaResponse 리스트로 변환")
    void getActiveBySystem_과_getByPackage_와_getBySystemAndPackage_변환() {
        // given
        PageMeta itsmA = MetaFixture.published("itg-ticket", 1, 1);
        PageMeta itsmB = MetaFixture.published("itg-change", 1, 1);
        when(metaRepository
                .findAllBySystemTypeAndMetaStatusAndActiveTrueOrderByGroupIdAscMajorVersionDescMinorVersionDesc(
                        SystemType.ITSM, MetaStatus.PUBLISHED))
                .thenReturn(List.of(itsmA, itsmB));

        PageMeta pkgA = MetaFixture.draft("itg-ticket", 1, 2);
        when(metaRepository
                .findAllByPackageTypeOrderByGroupIdAscMajorVersionDescMinorVersionDesc(PackageType.PACKAGE))
                .thenReturn(List.of(pkgA));

        PageMeta both = MetaFixture.baseBuilder("itg-asset", 1, 1)
                .systemType(SystemType.ITAM)
                .metaStatus(MetaStatus.PUBLISHED)
                .build();
        when(metaRepository
                .findAllBySystemTypeAndPackageTypeOrderByGroupIdAscMajorVersionDescMinorVersionDesc(
                        eq(SystemType.ITAM), eq(PackageType.PACKAGE)))
                .thenReturn(List.of(both));

        // when
        List<PageMetaResponse> active = metaService.getActiveBySystem(SystemType.ITSM);
        List<PageMetaResponse> byPackage = metaService.getByPackage(PackageType.PACKAGE);
        List<PageMetaResponse> combo = metaService.getBySystemAndPackage(SystemType.ITAM, PackageType.PACKAGE);

        // then
        assertThat(active).extracting(PageMetaResponse::id)
                .containsExactly("itg-ticket-v1-1", "itg-change-v1-1");
        assertThat(byPackage).extracting(PageMetaResponse::id)
                .containsExactly("itg-ticket-v1-2");
        assertThat(combo).hasSize(1);
        assertThat(combo.get(0).id()).isEqualTo("itg-asset-v1-1");
        assertThat(combo.get(0).systemType()).isEqualTo(SystemType.ITAM);
    }
}
