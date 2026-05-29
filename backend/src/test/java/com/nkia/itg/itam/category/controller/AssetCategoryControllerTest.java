package com.nkia.itg.itam.category.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nkia.itg.auth.service.JwtService;
import com.nkia.itg.common.config.SwaggerConfig;
import com.nkia.itg.common.exception.GlobalExceptionHandler;
import com.nkia.itg.common.security.JwtAuthenticationFilter;
import com.nkia.itg.common.security.SecurityConfig;
import com.nkia.itg.itam.category.dto.AssetCategoryCreateRequest;
import com.nkia.itg.itam.category.dto.AssetCategoryTreeNode;
import com.nkia.itg.itam.category.entity.AssetCategory;
import com.nkia.itg.itam.category.service.AssetCategoryService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SecurityConfig 를 import 하여 트리 조회(인증만)·생성/이동(ASSET_ADMIN) 권한 분기를 검증한다.
 */
@WebMvcTest(AssetCategoryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SwaggerConfig.class, JwtAuthenticationFilter.class})
class AssetCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AssetCategoryService assetCategoryService;

    /** SecurityConfig 가 import 하는 JwtAuthenticationFilter 의 의존성 충족용. */
    @MockBean
    private JwtService jwtService;

    private AssetCategory category(String code, String parentCode, String path) {
        return AssetCategory.builder()
                .code(code).label(code + " 라벨").parentCode(parentCode).path(path)
                .formMetaGroupId("itg-asset-hw-laptop").active(true).sortOrder(0).build();
    }

    @Test
    @DisplayName("GET /api/asset-categories/tree — 인증 사용자 200, 트리 반환")
    @WithMockUser(username = "user", authorities = "ASSET_READ")
    void GET_tree_인증_200() throws Exception {
        AssetCategoryTreeNode hw = AssetCategoryTreeNode.of(
                "HW", "하드웨어", null, "/HW/", null, true, 0);
        hw.children().add(AssetCategoryTreeNode.of(
                "HW_LAPTOP", "노트북", "HW", "/HW/HW_LAPTOP/", "itg-asset-hw-laptop", true, 1));
        given(assetCategoryService.getTree()).willReturn(List.of(hw));

        mockMvc.perform(get("/api/asset-categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("HW"))
                .andExpect(jsonPath("$.data[0].children[0].code").value("HW_LAPTOP"));
    }

    @Test
    @DisplayName("GET /api/asset-categories/tree — 익명 401 AUTH_REQUIRED")
    void GET_tree_익명_401() throws Exception {
        mockMvc.perform(get("/api/asset-categories/tree"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("GET /api/asset-categories/{code} — 단건 조회 200")
    @WithMockUser(username = "user", authorities = "ASSET_READ")
    void GET_by_code_200() throws Exception {
        given(assetCategoryService.getByCode("HW_LAPTOP"))
                .willReturn(category("HW_LAPTOP", "HW", "/HW/HW_LAPTOP/"));

        mockMvc.perform(get("/api/asset-categories/{code}", "HW_LAPTOP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("HW_LAPTOP"))
                .andExpect(jsonPath("$.data.parentCode").value("HW"))
                .andExpect(jsonPath("$.data.path").value("/HW/HW_LAPTOP/"));
    }

    @Test
    @DisplayName("POST /api/asset-categories — ASSET_ADMIN 생성 201")
    @WithMockUser(username = "admin", authorities = "ASSET_ADMIN")
    void POST_create_ASSET_ADMIN_201() throws Exception {
        given(assetCategoryService.create(eq("HW_LAPTOP"), eq("노트북"), eq("HW"), any(), eq(1)))
                .willReturn(category("HW_LAPTOP", "HW", "/HW/HW_LAPTOP/"));

        mockMvc.perform(post("/api/asset-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetCategoryCreateRequest(
                                "HW_LAPTOP", "노트북", "HW", "itg-asset-hw-laptop", 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("HW_LAPTOP"));
    }

    @Test
    @DisplayName("POST /api/asset-categories — 권한 없는 사용자 403 FORBIDDEN")
    @WithMockUser(username = "user", authorities = "ASSET_READ")
    void POST_create_권한_없음_403() throws Exception {
        mockMvc.perform(post("/api/asset-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetCategoryCreateRequest(
                                "HW_LAPTOP", "노트북", "HW", "itg-asset-hw-laptop", 1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        then(assetCategoryService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/asset-categories/{code}/move — 자기·자손 이동은 400 INVALID_REQUEST")
    @WithMockUser(username = "admin", authorities = "ASSET_ADMIN")
    void POST_move_자기_자손_400() throws Exception {
        willThrow(new IllegalStateException("분류를 자기 자신으로 이동할 수 없습니다: HW"))
                .given(assetCategoryService).move(eq("HW"), eq("HW"));

        mockMvc.perform(post("/api/asset-categories/{code}/move", "HW")
                        .param("newParentCode", "HW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
}
