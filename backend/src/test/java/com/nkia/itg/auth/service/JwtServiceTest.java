package com.nkia.itg.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.system.permission.entity.Permission;
import com.nkia.itg.system.role.entity.Role;
import com.nkia.itg.system.user.entity.UserAccount;
import io.jsonwebtoken.Claims;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "0123456789-itg-v2-jwt-secret-for-unit-test-please";

    private final JwtService jwtService = new JwtService(SECRET, 15, 7);

    private UserAccount sampleUser() {
        Permission perm = Permission.builder().id(10L).code("META_PUBLISH").name("메타 배포").build();
        Role role = Role.builder().id(1L).code("ROLE_ADMIN").name("관리자")
                .permissions(Set.of(perm)).build();
        return UserAccount.builder()
                .id(42L)
                .username("admin")
                .passwordHash("$2a$10$samplehash")
                .name("샘플-관리자")
                .roles(Set.of(role))
                .build();
    }

    @Test
    @DisplayName("issueAccess → parse 왕복 시 subject/uid/roles 복원")
    void issueAccess_parse_왕복_subject_uid_roles_복원() {
        String token = jwtService.issueAccess(sampleUser());

        Claims claims = jwtService.parse(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(((Number) claims.get("uid")).longValue()).isEqualTo(42L);
        assertThat(claims.get("roles", java.util.List.class)).containsExactly("ROLE_ADMIN");
        assertThat(claims.get("perms", java.util.List.class)).containsExactly("META_PUBLISH");
    }

    @Test
    @DisplayName("issueAccess 토큰은 typ=access")
    void issueAccess_typ_access() {
        Claims claims = jwtService.parse(jwtService.issueAccess(sampleUser())).getPayload();

        assertThat(jwtService.isAccess(claims)).isTrue();
        assertThat(jwtService.isRefresh(claims)).isFalse();
    }

    @Test
    @DisplayName("issueRefresh 토큰은 typ=refresh 이고 권한 claim 불포함")
    void issueRefresh_typ_refresh() {
        Claims claims = jwtService.parse(jwtService.issueRefresh(sampleUser())).getPayload();

        assertThat(jwtService.isRefresh(claims)).isTrue();
        assertThat(jwtService.isAccess(claims)).isFalse();
        assertThat(claims.get("roles")).isNull();
    }

    @Test
    @DisplayName("변조된 토큰은 INVALID_TOKEN 예외")
    void parse_변조된_토큰_INVALID_TOKEN_예외() {
        String token = jwtService.issueAccess(sampleUser());
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("a") ? "bb" : "aa");

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(ITGException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_TOKEN");
    }

    @Test
    @DisplayName("만료된 토큰은 INVALID_TOKEN 예외 (access-ttl 음수 fixture)")
    void parse_만료_토큰_예외() {
        JwtService expiring = new JwtService(SECRET, -1, 7);
        String expired = expiring.issueAccess(sampleUser());

        assertThatThrownBy(() -> expiring.parse(expired))
                .isInstanceOf(ITGException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_TOKEN");
    }

    @Test
    @DisplayName("isAccess / isRefresh 분기")
    void isAccess_isRefresh_분기() {
        Claims access = jwtService.parse(jwtService.issueAccess(sampleUser())).getPayload();
        Claims refresh = jwtService.parse(jwtService.issueRefresh(sampleUser())).getPayload();

        assertThat(jwtService.isAccess(access)).isTrue();
        assertThat(jwtService.isRefresh(refresh)).isTrue();
        assertThat(jwtService.isAccess(refresh)).isFalse();
        assertThat(jwtService.isRefresh(access)).isFalse();
    }
}
