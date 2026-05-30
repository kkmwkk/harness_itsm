package com.nkia.itg.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nkia.itg.common.exception.GlobalExceptionHandler;
import com.nkia.itg.dashboard.dto.AdminStats;
import com.nkia.itg.dashboard.dto.CountByKey;
import com.nkia.itg.dashboard.dto.DashboardSummary;
import com.nkia.itg.dashboard.service.DashboardService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    /** @Component 인 JwtAuthenticationFilter 슬라이스 로드 시 의존성 충족용. */
    @MockBean
    private com.nkia.itg.auth.service.JwtService jwtService;

    private DashboardSummary sample(AdminStats adminStats) {
        return new DashboardSummary(
                2L, 127L, 1L, 2L,
                List.of(new CountByKey("HIGH", 2L)),
                List.of(new CountByKey("OPEN", 2L)),
                List.of(new CountByKey("HW_LAPTOP", 5L)),
                List.of(0L, 1L, 2L),
                List.of(),
                List.of(),
                adminStats);
    }

    @Test
    @DisplayName("GET /api/dashboard/summary — 200 과 KPI 반환")
    void GET_summary_200() throws Exception {
        given(dashboardService.summary(any(), any())).willReturn(sample(null));

        mockMvc.perform(get("/api/dashboard/summary")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "it-support", null, List.of(new SimpleGrantedAuthority("ROLE_IT_SUPPORT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.openTickets").value(2))
                .andExpect(jsonPath("$.data.totalAssets").value(127))
                .andExpect(jsonPath("$.data.adminStats").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/dashboard/summary — 인증 principal 의 username·roles 가 Service 로 전달")
    void GET_summary_principal_전달() throws Exception {
        given(dashboardService.summary(eq("admin"), any())).willReturn(
                sample(new AdminStats(4L, 12L, 9L)));

        mockMvc.perform(get("/api/dashboard/summary")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminStats.userCount").value(4))
                .andExpect(jsonPath("$.data.adminStats.metaGroupCount").value(9));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> rolesCaptor = ArgumentCaptor.forClass(Set.class);
        then(dashboardService).should().summary(eq("admin"), rolesCaptor.capture());
        assertThat(rolesCaptor.getValue()).contains("ROLE_ADMIN");
    }
}
