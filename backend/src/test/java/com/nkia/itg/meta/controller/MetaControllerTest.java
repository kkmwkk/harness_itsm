package com.nkia.itg.meta.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nkia.itg.common.config.SwaggerConfig;
import com.nkia.itg.common.exception.GlobalExceptionHandler;
import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.common.security.JwtAuthenticationFilter;
import com.nkia.itg.common.security.SecurityConfig;
import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.dto.PageMetaResponse;
import com.nkia.itg.meta.dto.PageMetaVersionResponse;
import com.nkia.itg.meta.service.MetaService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MetaController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SwaggerConfig.class, JwtAuthenticationFilter.class})
class MetaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetaService metaService;

    private PageMetaResponse publishedResponse(String groupId, int major, int minor) {
        return new PageMetaResponse(
                "%s-v%d-%d".formatted(groupId, major, minor),
                "샘플 페이지 " + groupId,
                SystemType.ITSM,
                PackageType.PACKAGE,
                groupId,
                major,
                minor,
                MetaStatus.PUBLISHED,
                Map.of("api", "/api/tickets"),
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private PageMetaResponse draftResponse(String groupId, int major, int minor) {
        return new PageMetaResponse(
                "%s-v%d-%d".formatted(groupId, major, minor),
                "샘플 페이지 " + groupId,
                SystemType.ITSM,
                PackageType.PACKAGE,
                groupId,
                major,
                minor,
                MetaStatus.DRAFT,
                Map.of(),
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("GET /api/meta/active/{groupId} — PUBLISHED 있으면 200 과 PageMetaResponse 반환")
    void GET_active_그룹의_PUBLISHED_있으면_200_과_PageMetaResponse_반환() throws Exception {
        given(metaService.getActive("itg-ticket")).willReturn(publishedResponse("itg-ticket", 1, 2));

        mockMvc.perform(get("/api/meta/active/{groupId}", "itg-ticket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("itg-ticket-v1-2"))
                .andExpect(jsonPath("$.data.metaStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.groupId").value("itg-ticket"))
                .andExpect(jsonPath("$.errorCode").doesNotExist());

        then(metaService).should().getActive("itg-ticket");
    }

    @Test
    @DisplayName("GET /api/meta/active/{groupId} — PUBLISHED 없으면 404 와 META_NOT_PUBLISHED")
    void GET_active_그룹에_PUBLISHED_없으면_404_와_META_NOT_PUBLISHED() throws Exception {
        given(metaService.getActive("itg-empty"))
                .willThrow(new ITGException("META_NOT_PUBLISHED", "배포된 메타가 없습니다: itg-empty", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/meta/active/{groupId}", "itg-empty"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("META_NOT_PUBLISHED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("PATCH /api/meta/{metaId}/publish — 정상 200 과 메시지 '배포되었습니다.'")
    void PATCH_publish_정상_200_과_메시지_배포되었습니다() throws Exception {
        given(metaService.publish("itg-ticket-v1-2")).willReturn(publishedResponse("itg-ticket", 1, 2));

        mockMvc.perform(patch("/api/meta/{metaId}/publish", "itg-ticket-v1-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("배포되었습니다."))
                .andExpect(jsonPath("$.data.metaStatus").value("PUBLISHED"));

        then(metaService).should().publish("itg-ticket-v1-2");
    }

    @Test
    @DisplayName("PATCH /api/meta/{metaId}/publish — DRAFT 아닌 메타는 400 INVALID_STATUS")
    void PATCH_publish_DRAFT_아닌_메타_400_INVALID_STATUS() throws Exception {
        given(metaService.publish("itg-ticket-v1-2"))
                .willThrow(new ITGException("INVALID_STATUS",
                        "DRAFT 상태만 배포 가능합니다. 현재 상태: PUBLISHED",
                        HttpStatus.BAD_REQUEST));

        mockMvc.perform(patch("/api/meta/{metaId}/publish", "itg-ticket-v1-2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS"));
    }

    @Test
    @DisplayName("POST /api/meta/{metaId}/copy — 정상 201 과 새 DRAFT 응답")
    void POST_copy_정상_201_과_새_DRAFT_응답() throws Exception {
        given(metaService.copy("itg-ticket-v1-2")).willReturn(draftResponse("itg-ticket", 1, 3));

        mockMvc.perform(post("/api/meta/{metaId}/copy", "itg-ticket-v1-2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("새 버전이 생성되었습니다."))
                .andExpect(jsonPath("$.data.id").value("itg-ticket-v1-3"))
                .andExpect(jsonPath("$.data.metaStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.minorVersion").value(3));

        then(metaService).should().copy("itg-ticket-v1-2");
    }

    @Test
    @DisplayName("GET /api/meta/group/{groupId}/versions — 정렬된 리스트 반환")
    void GET_group_versions_정렬된_리스트_반환() throws Exception {
        List<PageMetaVersionResponse> versions = List.of(
                new PageMetaVersionResponse("itg-ticket-v1-3", "itg-ticket", 1, 3, MetaStatus.DRAFT,
                        LocalDateTime.now(), LocalDateTime.now()),
                new PageMetaVersionResponse("itg-ticket-v1-2", "itg-ticket", 1, 2, MetaStatus.PUBLISHED,
                        LocalDateTime.now(), LocalDateTime.now()),
                new PageMetaVersionResponse("itg-ticket-v1-1", "itg-ticket", 1, 1, MetaStatus.DEPRECATED,
                        LocalDateTime.now(), LocalDateTime.now())
        );
        given(metaService.getVersions("itg-ticket")).willReturn(versions);

        mockMvc.perform(get("/api/meta/group/{groupId}/versions", "itg-ticket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value("itg-ticket-v1-3"))
                .andExpect(jsonPath("$.data[0].metaStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data[2].metaStatus").value("DEPRECATED"));
    }

    @Test
    @DisplayName("GET /api/meta/system/{systemType}/active — ITSM PUBLISHED 만 반환")
    void GET_system_active_ITSM_PUBLISHED_만_반환() throws Exception {
        given(metaService.getActiveBySystem(SystemType.ITSM))
                .willReturn(List.of(
                        publishedResponse("itg-ticket", 1, 2),
                        publishedResponse("itg-change", 2, 0)
                ));

        mockMvc.perform(get("/api/meta/system/{systemType}/active", "ITSM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].systemType").value("ITSM"))
                .andExpect(jsonPath("$.data[0].metaStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data[1].systemType").value("ITSM"));

        then(metaService).should().getActiveBySystem(SystemType.ITSM);
    }

    @Test
    @DisplayName("GET /api/meta/package/{packageType} — PACKAGE 목록 반환")
    void GET_package_PACKAGE_목록_반환() throws Exception {
        given(metaService.getByPackage(PackageType.PACKAGE))
                .willReturn(List.of(publishedResponse("itg-ticket", 1, 2)));

        mockMvc.perform(get("/api/meta/package/{packageType}", "PACKAGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].packageType").value("PACKAGE"));

        then(metaService).should().getByPackage(PackageType.PACKAGE);
    }

    @Test
    @DisplayName("PATCH /api/meta/{metaId}/archive — 정상 200 과 메시지 '보관 처리되었습니다.'")
    void PATCH_archive_정상_200_과_메시지_보관_처리되었습니다() throws Exception {
        PageMetaResponse archived = new PageMetaResponse(
                "itg-ticket-v1-2", "샘플 페이지 itg-ticket",
                SystemType.ITSM, PackageType.PACKAGE, "itg-ticket", 1, 2,
                MetaStatus.ARCHIVED, Map.of(), true,
                LocalDateTime.now(), LocalDateTime.now()
        );
        given(metaService.archive("itg-ticket-v1-2")).willReturn(archived);

        mockMvc.perform(patch("/api/meta/{metaId}/archive", "itg-ticket-v1-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("보관 처리되었습니다."))
                .andExpect(jsonPath("$.data.metaStatus").value("ARCHIVED"));

        then(metaService).should().archive("itg-ticket-v1-2");
    }

    @Test
    @DisplayName("GET /api/meta/{metaId} — 없으면 404 META_NOT_FOUND")
    void GET_meta_by_id_없으면_404_META_NOT_FOUND() throws Exception {
        given(metaService.getById("itg-missing-v1-1"))
                .willThrow(new ITGException("META_NOT_FOUND",
                        "메타를 찾을 수 없습니다: itg-missing-v1-1",
                        HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/meta/{metaId}", "itg-missing-v1-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("META_NOT_FOUND"));

        then(metaService).should(never()).getActive(any());
        then(metaService).should().getById(eq("itg-missing-v1-1"));
    }
}
