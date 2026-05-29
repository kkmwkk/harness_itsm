package com.nkia.itg.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.nkia.itg.auth.dto.LoginRequest;
import com.nkia.itg.auth.dto.MeResponse;
import com.nkia.itg.auth.dto.RefreshRequest;
import com.nkia.itg.auth.dto.TokenResponse;
import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.system.permission.entity.Permission;
import com.nkia.itg.system.role.entity.Role;
import com.nkia.itg.system.user.domain.UserStatus;
import com.nkia.itg.system.user.entity.UserAccount;
import com.nkia.itg.system.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @InjectMocks
    private AuthService authService;

    private UserAccount user(UserStatus status) {
        Permission perm = Permission.builder().id(10L).code("META_PUBLISH").name("메타 배포").build();
        Role role = Role.builder().id(1L).code("ROLE_ADMIN").name("관리자")
                .permissions(Set.of(perm)).build();
        return UserAccount.builder()
                .id(42L)
                .username("admin")
                .passwordHash("$2a$10$samplehash")
                .name("샘플-관리자")
                .status(status)
                .roles(Set.of(role))
                .build();
    }

    @Test
    @DisplayName("login 성공 시 TokenResponse 반환 + lastLoginAt 갱신 + save")
    void login_성공_시_TokenResponse_반환_lastLoginAt_갱신() {
        UserAccount u = user(UserStatus.ACTIVE);
        given(userRepository.findByUsername("admin")).willReturn(Optional.of(u));
        given(passwordEncoder.matches("샘플-비번", u.getPasswordHash())).willReturn(true);
        given(jwtService.issueAccess(u)).willReturn("access-token");
        given(jwtService.issueRefresh(u)).willReturn("refresh-token");
        given(jwtService.accessTtlSeconds()).willReturn(900L);

        TokenResponse res = authService.login(new LoginRequest("admin", "샘플-비번"));

        assertThat(res.accessToken()).isEqualTo("access-token");
        assertThat(res.refreshToken()).isEqualTo("refresh-token");
        assertThat(res.accessExpiresInSec()).isEqualTo(900L);
        assertThat(res.user().username()).isEqualTo("admin");
        assertThat(res.roles()).containsExactly("ROLE_ADMIN");
        assertThat(res.permissions()).containsExactly("META_PUBLISH");
        assertThat(u.getLastLoginAt()).isNotNull();
        then(userRepository).should().save(u);
    }

    @Test
    @DisplayName("login 사용자 없음 → LOGIN_FAILED 401 + 통합 메시지")
    void login_사용자_없음_LOGIN_FAILED_401_메시지_보안() {
        given(userRepository.findByUsername("nobody")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody", "pw")))
                .isInstanceOf(ITGException.class)
                .hasFieldOrPropertyWithValue("errorCode", "LOGIN_FAILED")
                .hasMessage("아이디 또는 비밀번호가 올바르지 않습니다");
    }

    @Test
    @DisplayName("login 비밀번호 불일치 → LOGIN_FAILED 401")
    void login_비밀번호_불일치_LOGIN_FAILED_401() {
        UserAccount u = user(UserStatus.ACTIVE);
        given(userRepository.findByUsername("admin")).willReturn(Optional.of(u));
        given(passwordEncoder.matches("wrong", u.getPasswordHash())).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong")))
                .isInstanceOf(ITGException.class)
                .hasFieldOrPropertyWithValue("errorCode", "LOGIN_FAILED");
    }

    @Test
    @DisplayName("login LOCKED 사용자 거부 (LOGIN_FAILED)")
    void login_LOCKED_사용자_거부() {
        given(userRepository.findByUsername("admin")).willReturn(Optional.of(user(UserStatus.LOCKED)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "샘플-비번")))
                .isInstanceOf(ITGException.class)
                .hasFieldOrPropertyWithValue("errorCode", "LOGIN_FAILED");
    }

    @Test
    @DisplayName("login RETIRED 사용자 거부 (LOGIN_FAILED)")
    void login_RETIRED_사용자_거부() {
        given(userRepository.findByUsername("admin")).willReturn(Optional.of(user(UserStatus.RETIRED)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "샘플-비번")))
                .isInstanceOf(ITGException.class)
                .hasFieldOrPropertyWithValue("errorCode", "LOGIN_FAILED");
    }

    @Test
    @DisplayName("refresh 정상 → 새 access 발급")
    void refresh_정상_새_access_발급() {
        UserAccount u = user(UserStatus.ACTIVE);
        Claims claims = mock(Claims.class);
        @SuppressWarnings("unchecked")
        Jws<Claims> jws = mock(Jws.class);
        given(claims.getSubject()).willReturn("admin");
        given(jws.getPayload()).willReturn(claims);
        given(jwtService.parse("refresh-token")).willReturn(jws);
        given(jwtService.isRefresh(claims)).willReturn(true);
        given(userRepository.findByUsername("admin")).willReturn(Optional.of(u));
        given(jwtService.issueAccess(u)).willReturn("new-access");
        given(jwtService.issueRefresh(u)).willReturn("new-refresh");
        given(jwtService.accessTtlSeconds()).willReturn(900L);

        TokenResponse res = authService.refresh(new RefreshRequest("refresh-token"));

        assertThat(res.accessToken()).isEqualTo("new-access");
        assertThat(res.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    @DisplayName("refresh 에 access 토큰 사용 시 INVALID_TOKEN 거부")
    void refresh_access_토큰_거부_INVALID_TOKEN() {
        Claims claims = mock(Claims.class);
        @SuppressWarnings("unchecked")
        Jws<Claims> jws = mock(Jws.class);
        given(jws.getPayload()).willReturn(claims);
        given(jwtService.parse("access-token")).willReturn(jws);
        given(jwtService.isRefresh(claims)).willReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("access-token")))
                .isInstanceOf(ITGException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_TOKEN");
        then(jwtService).should(org.mockito.Mockito.never()).issueAccess(any());
    }

    @Test
    @DisplayName("me — username 으로 사용자 조회 roles/perms 포함")
    void me_username_으로_사용자_조회_roles_perms_포함() {
        given(userRepository.findByUsername("admin")).willReturn(Optional.of(user(UserStatus.ACTIVE)));

        MeResponse res = authService.me("admin");

        assertThat(res.user().username()).isEqualTo("admin");
        assertThat(res.roles()).containsExactly("ROLE_ADMIN");
        assertThat(res.permissions()).containsExactly("META_PUBLISH");
    }
}
