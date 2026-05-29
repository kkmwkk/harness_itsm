package com.nkia.itg.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nkia.itg.auth.dto.LoginRequest;
import com.nkia.itg.auth.dto.MeResponse;
import com.nkia.itg.auth.dto.RefreshRequest;
import com.nkia.itg.auth.dto.TokenResponse;
import com.nkia.itg.auth.dto.UserSummary;
import com.nkia.itg.auth.service.AuthService;
import com.nkia.itg.auth.service.JwtService;
import com.nkia.itg.common.config.SwaggerConfig;
import com.nkia.itg.common.exception.GlobalExceptionHandler;
import com.nkia.itg.common.security.JwtAuthenticationFilter;
import com.nkia.itg.common.security.SecurityConfig;
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

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SwaggerConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    /** SecurityConfig 가 import 하는 JwtAuthenticationFilter 의 의존성 충족용. */
    @MockBean
    private JwtService jwtService;

    private TokenResponse sampleToken() {
        return new TokenResponse(
                "sample.access.token", "sample.refresh.token", 900,
                new UserSummary(1L, "admin", "샘플-관리자", "sample@example.com", null),
                List.of("ROLE_ADMIN"), List.of("USER_ADMIN"));
    }

    @Test
    @DisplayName("POST /api/auth/login — 정상 200 과 토큰 반환")
    void POST_login_정상_200() throws Exception {
        given(authService.login(any())).willReturn(sampleToken());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "샘플-비번"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("sample.access.token"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("POST /api/auth/login — 빈 자격증명 400 VALIDATION_FAILED")
    void POST_login_검증_실패_400_VALIDATION_FAILED() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh — 정상 200")
    void POST_refresh_정상() throws Exception {
        given(authService.refresh(any())).willReturn(sampleToken());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("sample.refresh.token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("sample.access.token"));
    }

    @Test
    @DisplayName("GET /api/auth/me — 익명 401 AUTH_REQUIRED")
    void GET_me_익명_401_AUTH_REQUIRED() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("GET /api/auth/me — 인증 시 200 과 사용자 정보")
    @WithMockUser(username = "admin")
    void GET_me_인증_시_200() throws Exception {
        given(authService.me("admin")).willReturn(new MeResponse(
                new UserSummary(1L, "admin", "샘플-관리자", "sample@example.com", "샘플-운영팀"),
                List.of("ROLE_ADMIN"), List.of("USER_ADMIN")));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_ADMIN"));
    }
}
