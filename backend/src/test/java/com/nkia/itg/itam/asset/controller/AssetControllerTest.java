package com.nkia.itg.itam.asset.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nkia.itg.auth.service.JwtService;
import com.nkia.itg.common.exception.GlobalExceptionHandler;
import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.common.security.JwtAuthenticationFilter;
import com.nkia.itg.common.security.SecurityConfig;
import com.nkia.itg.itam.asset.domain.AssetStatus;
import com.nkia.itg.itam.asset.domain.AssetType;
import com.nkia.itg.itam.asset.dto.AssetAssignRequest;
import com.nkia.itg.itam.asset.dto.AssetCreateRequest;
import com.nkia.itg.itam.asset.dto.AssetResponse;
import com.nkia.itg.itam.asset.dto.AssetStatusChangeRequest;
import com.nkia.itg.itam.asset.dto.AssetSummary;
import com.nkia.itg.itam.asset.dto.AssetUpdateRequest;
import com.nkia.itg.itam.asset.service.AssetService;
import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.dto.PageMetaResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SecurityConfig 를 함께 import 하고 필터를 비활성화하지 않는다 — 인증된(@WithMockUser) 요청이
 * 시큐리티 필터 체인을 통과해 컨트롤러까지 도달함을 검증한다 (JWT 인증 강화 후 회귀 검증).
 */
@WebMvcTest(AssetController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class})
@WithMockUser
class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AssetService assetService;

    /** SecurityConfig 가 import 하는 JwtAuthenticationFilter 의 의존성 충족용. */
    @MockBean
    private JwtService jwtService;

    private AssetResponse sampleResponse(Long id, AssetStatus status, String assigneeId) {
        return new AssetResponse(
                id,
                "AST-%05d".formatted(id),
                "샘플 자산",
                AssetType.HARDWARE,
                status,
                "SAMPLE-MODEL",
                "SN-SAMPLE-1",
                "노트북",
                assigneeId,
                "본사 3층",
                LocalDate.of(2026, 1, 15),
                null,
                "itg-asset-v1-1",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private PageMetaResponse sampleMeta(MetaStatus metaStatus) {
        return new PageMetaResponse(
                "itg-asset-v1-1",
                "ITAM 자산원장",
                SystemType.ITAM,
                PackageType.PACKAGE,
                "itg-asset",
                1,
                1,
                metaStatus,
                Map.of("api", "/api/assets"),
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("POST /api/assets — 201 과 AssetResponse 반환")
    void POST_create_201() throws Exception {
        AssetCreateRequest req = new AssetCreateRequest(
                "샘플 자산", AssetType.HARDWARE, "SAMPLE-MODEL", "SN-SAMPLE-1",
                "노트북", "assignee-sample-1", "본사 3층", LocalDate.of(2026, 1, 15), "itg-asset", null);
        given(assetService.create(any())).willReturn(sampleResponse(42L, AssetStatus.ACTIVE, "assignee-sample-1"));

        mockMvc.perform(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.assetNo").value("AST-00042"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.errorCode").doesNotExist());

        then(assetService).should().create(any());
    }

    @Test
    @DisplayName("POST /api/assets — name 누락 시 400 VALIDATION_FAILED")
    void POST_create_name_누락_400() throws Exception {
        AssetCreateRequest req = new AssetCreateRequest(
                "  ", AssetType.HARDWARE, null, null, null, null, null, null, "itg-asset", null);

        mockMvc.perform(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        then(assetService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/assets — pageGroupId 누락 시 400 VALIDATION_FAILED")
    void POST_create_pageGroupId_누락_400() throws Exception {
        AssetCreateRequest req = new AssetCreateRequest(
                "샘플 자산", AssetType.HARDWARE, null, null, null, null, null, null, "  ", null);

        mockMvc.perform(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        then(assetService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("GET /api/assets — 200 PageResponse 평탄화")
    void GET_search_200_PageResponse() throws Exception {
        Page<AssetSummary> empty = new PageImpl<>(List.of(), PageRequest.of(2, 50), 0);
        given(assetService.search(eq(AssetStatus.ACTIVE), eq(AssetType.HARDWARE), eq("assignee-sample-1"), eq(2), eq(50)))
                .willReturn(empty);

        mockMvc.perform(get("/api/assets")
                        .param("status", "ACTIVE")
                        .param("assetType", "HARDWARE")
                        .param("assigneeId", "assignee-sample-1")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.number").value(2))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        then(assetService).should()
                .search(eq(AssetStatus.ACTIVE), eq(AssetType.HARDWARE), eq("assignee-sample-1"), eq(2), eq(50));
    }

    @Test
    @DisplayName("GET /api/assets/{id} — 없으면 404 ASSET_NOT_FOUND")
    void GET_by_id_없으면_404() throws Exception {
        given(assetService.getById(999L))
                .willThrow(new ITGException("ASSET_NOT_FOUND", "자산을 찾을 수 없습니다: 999", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/assets/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ASSET_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("PATCH /api/assets/{id}/status — 허용되지 않은 전이는 400 INVALID_REQUEST")
    void PATCH_status_허용되지_않은_전이_400_INVALID_REQUEST() throws Exception {
        given(assetService.changeStatus(eq(42L), any()))
                .willThrow(new IllegalStateException("허용되지 않은 상태 전이입니다: RETIRED → ACTIVE"));

        mockMvc.perform(patch("/api/assets/{id}/status", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetStatusChangeRequest(AssetStatus.ACTIVE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /api/assets/{id}/status — RETIRED 자산 전이 시도는 400")
    void PATCH_status_RETIRED_400() throws Exception {
        given(assetService.changeStatus(eq(42L), any()))
                .willThrow(new IllegalStateException("RETIRED 자산은 상태를 변경할 수 없습니다."));

        mockMvc.perform(patch("/api/assets/{id}/status", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetStatusChangeRequest(AssetStatus.STORAGE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("PATCH /api/assets/{id}/assign — RETIRED 자산 할당 시도는 400")
    void PATCH_assign_RETIRED_400() throws Exception {
        given(assetService.assign(eq(42L), any()))
                .willThrow(new IllegalStateException("RETIRED 자산은 담당자를 변경할 수 없습니다."));

        mockMvc.perform(patch("/api/assets/{id}/assign", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetAssignRequest("assignee-sample-2"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("GET /api/assets/{id}/registration-meta — DEPRECATED 메타도 정상 반환 (이력 복원)")
    void GET_registration_meta_DEPRECATED_메타도_정상_반환() throws Exception {
        given(assetService.getRegistrationMeta(42L)).willReturn(sampleMeta(MetaStatus.DEPRECATED));

        mockMvc.perform(get("/api/assets/{id}/registration-meta", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("itg-asset-v1-1"))
                .andExpect(jsonPath("$.data.metaStatus").value("DEPRECATED"))
                .andExpect(jsonPath("$.data.groupId").value("itg-asset"));

        then(assetService).should().getRegistrationMeta(42L);
    }

    @Test
    @DisplayName("GET /api/assets/by-no/{assetNo} — 200")
    void GET_by_no_200() throws Exception {
        given(assetService.getByAssetNo("AST-00042"))
                .willReturn(sampleResponse(42L, AssetStatus.ACTIVE, "assignee-sample-1"));

        mockMvc.perform(get("/api/assets/by-no/{assetNo}", "AST-00042"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetNo").value("AST-00042"));

        then(assetService).should().getByAssetNo("AST-00042");
    }

    @Test
    @DisplayName("PATCH /api/assets/{id} — 본문 부분 업데이트 200")
    void PATCH_update_200_부분_업데이트() throws Exception {
        AssetResponse updated = new AssetResponse(
                42L, "AST-00042", "수정된 샘플 자산", AssetType.HARDWARE, AssetStatus.ACTIVE,
                "SAMPLE-MODEL-2", "SN-SAMPLE-1", "데스크탑", "assignee-sample-1", "본사 4층",
                LocalDate.of(2026, 1, 15), null, "itg-asset-v1-1", LocalDateTime.now(), LocalDateTime.now());
        given(assetService.update(eq(42L), any())).willReturn(updated);

        mockMvc.perform(patch("/api/assets/{id}", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AssetUpdateRequest("수정된 샘플 자산", "SAMPLE-MODEL-2", null, "데스크탑", "본사 4층"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("수정된 샘플 자산"))
                .andExpect(jsonPath("$.data.category").value("데스크탑"));

        then(assetService).should().update(eq(42L), any());
    }

    @Test
    @DisplayName("POST /api/assets — 배포 메타 없으면 404 META_NOT_PUBLISHED (도메인 정합성 거부)")
    void POST_create_META_NOT_PUBLISHED_400_또는_404() throws Exception {
        AssetCreateRequest req = new AssetCreateRequest(
                "샘플 자산", AssetType.HARDWARE, null, null, null, null, null, null, "itg-asset", null);
        given(assetService.create(any()))
                .willThrow(new ITGException("META_NOT_PUBLISHED", "배포된 메타가 없습니다: itg-asset", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("META_NOT_PUBLISHED"));
    }
}
