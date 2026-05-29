package com.nkia.itg.system.role.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nkia.itg.system.permission.entity.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Role 도메인 메서드 단위 테스트 (권한 grant/revoke)")
class RoleTest {

    private Role roleSample() {
        return Role.builder()
                .code("ROLE_SAMPLE")
                .name("샘플 역할")
                .build();
    }

    private Permission permissionSample(String code) {
        return Permission.builder()
                .code(code)
                .name("샘플 권한 " + code)
                .build();
    }

    @Test
    @DisplayName("grant · revoke — 권한 추가·삭제")
    void grant_revoke_권한_추가_삭제() {
        Role role = roleSample();
        Permission perm = permissionSample("TICKET_CREATE");

        role.grant(perm);
        assertThat(role.getPermissions()).containsExactly(perm);

        role.revoke(perm);
        assertThat(role.getPermissions()).isEmpty();
    }

    @Test
    @DisplayName("grant — 중복 부여 시 Set 특성으로 무시")
    void grant_중복_set_특성으로_무시() {
        Role role = roleSample();
        Permission perm = permissionSample("META_PUBLISH");

        role.grant(perm);
        role.grant(perm);

        assertThat(role.getPermissions()).hasSize(1);
    }
}
