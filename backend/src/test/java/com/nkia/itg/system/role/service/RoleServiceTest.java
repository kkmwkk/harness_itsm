package com.nkia.itg.system.role.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.fixture.SystemFixtures;
import com.nkia.itg.system.permission.entity.Permission;
import com.nkia.itg.system.permission.repository.PermissionRepository;
import com.nkia.itg.system.role.dto.RoleCreateRequest;
import com.nkia.itg.system.role.dto.RoleResponse;
import com.nkia.itg.system.role.dto.RoleUpdateRequest;
import com.nkia.itg.system.role.entity.Role;
import com.nkia.itg.system.role.repository.RoleRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @InjectMocks
    private RoleService roleService;

    @Test
    @DisplayName("create: 신규 역할 저장")
    void create_역할_저장() {
        given(roleRepository.existsByCode("ROLE_IT_SUPPORT")).willReturn(false);
        given(roleRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        RoleResponse res = roleService.create(
                new RoleCreateRequest("ROLE_IT_SUPPORT", "IT 지원", "샘플 설명"));

        assertThat(res.code()).isEqualTo("ROLE_IT_SUPPORT");
        assertThat(res.name()).isEqualTo("IT 지원");
    }

    @Test
    @DisplayName("create: 코드 중복 DATA_INTEGRITY")
    void create_코드_중복_거부() {
        given(roleRepository.existsByCode("ROLE_ADMIN")).willReturn(true);

        assertThatThrownBy(() -> roleService.create(
                new RoleCreateRequest("ROLE_ADMIN", "관리자", null)))
                .isInstanceOf(ITGException.class)
                .satisfies(e -> assertThat(((ITGException) e).getErrorCode()).isEqualTo("DATA_INTEGRITY"));
        then(roleRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("update: 이름·설명 수정")
    void update_이름_설명_수정() {
        Role role = SystemFixtures.role(1L, "ROLE_USER", "일반");
        given(roleRepository.findById(1L)).willReturn(Optional.of(role));

        RoleResponse res = roleService.update(1L, new RoleUpdateRequest("일반 사용자", "갱신 설명"));

        assertThat(res.name()).isEqualTo("일반 사용자");
        assertThat(res.description()).isEqualTo("갱신 설명");
    }

    @Test
    @DisplayName("grantPermission: 역할에 권한 부여")
    void grantPermission_권한_부여() {
        Role role = SystemFixtures.role(1L, "ROLE_USER", "일반");
        Permission perm = SystemFixtures.permission(10L, "TICKET_CREATE", "티켓 생성");
        given(roleRepository.findByCode("ROLE_USER")).willReturn(Optional.of(role));
        given(permissionRepository.findByCode("TICKET_CREATE")).willReturn(Optional.of(perm));

        roleService.grantPermission("ROLE_USER", "TICKET_CREATE");

        assertThat(role.getPermissions()).contains(perm);
    }

    @Test
    @DisplayName("revokePermission: 역할에서 권한 회수")
    void revokePermission_권한_회수() {
        Role role = SystemFixtures.role(1L, "ROLE_USER", "일반");
        Permission perm = SystemFixtures.permission(10L, "TICKET_CREATE", "티켓 생성");
        role.grant(perm);
        given(roleRepository.findByCode("ROLE_USER")).willReturn(Optional.of(role));
        given(permissionRepository.findByCode("TICKET_CREATE")).willReturn(Optional.of(perm));

        roleService.revokePermission("ROLE_USER", "TICKET_CREATE");

        assertThat(role.getPermissions()).doesNotContain(perm);
    }

    @Test
    @DisplayName("grantPermission: 없는 권한 PERMISSION_NOT_FOUND")
    void grantPermission_없는_권한_거부() {
        Role role = SystemFixtures.role(1L, "ROLE_USER", "일반");
        given(roleRepository.findByCode("ROLE_USER")).willReturn(Optional.of(role));
        given(permissionRepository.findByCode("NONE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.grantPermission("ROLE_USER", "NONE"))
                .isInstanceOf(ITGException.class)
                .satisfies(e -> assertThat(((ITGException) e).getErrorCode())
                        .isEqualTo("PERMISSION_NOT_FOUND"));
    }
}
