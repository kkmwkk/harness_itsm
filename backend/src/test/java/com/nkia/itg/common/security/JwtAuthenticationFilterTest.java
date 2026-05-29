package com.nkia.itg.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nkia.itg.auth.service.JwtService;
import com.nkia.itg.system.role.entity.Role;
import com.nkia.itg.system.user.entity.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "0123456789-itg-v2-jwt-secret-for-unit-test-please";

    private final JwtService jwtService = new JwtService(SECRET, 15, 7);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private UserAccount sampleUser() {
        Role role = Role.builder().id(1L).code("ROLE_ADMIN").name("관리자").build();
        return UserAccount.builder()
                .id(42L).username("admin").passwordHash("$2a$10$h").roles(Set.of(role)).build();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰 시 SecurityContext 에 Authentication 설정")
    void 유효_Bearer_토큰_시_SecurityContext_에_Authentication_설정() throws Exception {
        String token = jwtService.issueAccess(sampleUser());
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("admin");
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("Bearer 없으면 익명 그대로 진행")
    void Bearer_없으면_익명_그대로_진행() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull(); // 체인은 그대로 진행
    }

    @Test
    @DisplayName("변조/만료 토큰 시 익명 그대로 진행")
    void 변조_만료_토큰_시_익명_그대로_진행() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer this.is.not-a-valid-token");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
}
