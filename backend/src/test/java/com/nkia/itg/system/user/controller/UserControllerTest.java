package com.nkia.itg.system.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nkia.itg.auth.service.JwtService;
import com.nkia.itg.common.config.SwaggerConfig;
import com.nkia.itg.common.exception.GlobalExceptionHandler;
import com.nkia.itg.common.security.JwtAuthenticationFilter;
import com.nkia.itg.common.security.SecurityConfig;
import com.nkia.itg.common.security.SelfCheck;
import com.nkia.itg.fixture.SystemFixtures;
import com.nkia.itg.system.user.domain.UserStatus;
import com.nkia.itg.system.user.dto.PasswordChangeRequest;
import com.nkia.itg.system.user.dto.UserCreateRequest;
import com.nkia.itg.system.user.dto.UserResponse;
import com.nkia.itg.system.user.dto.UserSummary;
import com.nkia.itg.system.user.repository.UserRepository;
import com.nkia.itg.system.user.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SwaggerConfig.class,
        JwtAuthenticationFilter.class, SelfCheck.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    /** SecurityConfig 가 import 하는 JwtAuthenticationFilter 의 의존성 충족용. */
    @MockBean
    private JwtService jwtService;

    /** SelfCheck 빈의 의존성 — 본인 확인 시 username 환원에 사용. */
    @MockBean
    private UserRepository userRepository;

    private UserResponse sampleUserResponse() {
        return new UserResponse(
                1L, "u-sample-001", "샘플 사용자", "u@example.com", "010-0000-0000",
                UserStatus.ACTIVE, 1L, "샘플 IT팀",
                List.of("ROLE_USER"), List.of(),
                LocalDateTime.now(), null, LocalDateTime.now(), LocalDateTime.now());
    }

    private String validCreateBody() throws Exception {
        return objectMapper.writeValueAsString(new UserCreateRequest(
                "u-sample-001", "샘플-비번1234", "샘플 사용자", "u@example.com",
                "010-0000-0000", 1L, List.of("ROLE_USER")));
    }

    @Test
    @DisplayName("GET /api/users — 권한 없으면 403 FORBIDDEN")
    @WithMockUser(username = "nobody")
    void GET_users_권한_없음_403_FORBIDDEN() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/users — USER_READ 권한이면 200")
    @WithMockUser(username = "reader", authorities = "USER_READ")
    void GET_users_USER_READ_권한_200() throws Exception {
        Page<UserSummary> page = new PageImpl<>(List.of(new UserSummary(
                1L, "u-sample-001", "샘플 사용자", "u@example.com",
                UserStatus.ACTIVE, 1L, List.of("ROLE_USER"), null)));
        given(userService.search(any(), any(), any(), any(), anyInt(), anyInt())).willReturn(page);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].username").value("u-sample-001"));
    }

    @Test
    @DisplayName("POST /api/users — 권한 없으면 403")
    @WithMockUser(username = "reader", authorities = "USER_READ")
    void POST_users_권한_없음_403() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /api/users — USER_ADMIN 권한이면 201")
    @WithMockUser(username = "admin", authorities = "USER_ADMIN")
    void POST_users_USER_ADMIN_권한_201() throws Exception {
        given(userService.create(any())).willReturn(sampleUserResponse());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("u-sample-001"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("PATCH /api/users/{id}/password — 본인이면 USER_ADMIN 없어도 허용 200")
    @WithMockUser(username = "alice")
    void PATCH_password_본인_허용() throws Exception {
        given(userRepository.findById(5L))
                .willReturn(Optional.of(SystemFixtures.activeUserWithId(5L, "alice", null)));

        mockMvc.perform(patch("/api/users/{id}/password", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordChangeRequest("샘플-새비번5678"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /api/users/{id}/password — 타인은 USER_ADMIN 없으면 403")
    @WithMockUser(username = "bob")
    void PATCH_password_타인_USER_ADMIN_없으면_403() throws Exception {
        given(userRepository.findById(5L))
                .willReturn(Optional.of(SystemFixtures.activeUserWithId(5L, "alice", null)));

        mockMvc.perform(patch("/api/users/{id}/password", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordChangeRequest("샘플-새비번5678"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
