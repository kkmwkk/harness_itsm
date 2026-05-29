package com.nkia.itg.system.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.fixture.SystemFixtures;
import com.nkia.itg.system.dept.entity.Department;
import com.nkia.itg.system.dept.repository.DepartmentRepository;
import com.nkia.itg.system.role.entity.Role;
import com.nkia.itg.system.role.repository.RoleRepository;
import com.nkia.itg.system.user.domain.UserStatus;
import com.nkia.itg.system.user.dto.UserCreateRequest;
import com.nkia.itg.system.user.dto.UserResponse;
import com.nkia.itg.system.user.dto.UserSummary;
import com.nkia.itg.system.user.dto.UserUpdateRequest;
import com.nkia.itg.system.user.entity.UserAccount;
import com.nkia.itg.system.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("create: username 중복 시 DATA_INTEGRITY 거부")
    void create_username_중복_DATA_INTEGRITY() {
        given(userRepository.existsByUsername("u-sample-001")).willReturn(true);

        assertThatThrownBy(() -> userService.create(new UserCreateRequest(
                "u-sample-001", "샘플-비번1234", "샘플 사용자", null, null, null, null)))
                .isInstanceOf(ITGException.class)
                .satisfies(e -> assertThat(((ITGException) e).getErrorCode()).isEqualTo("DATA_INTEGRITY"));

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("create: 비밀번호는 BCrypt 해시로 저장되고 평문은 노출되지 않는다")
    void create_password_BCrypt_해시_저장_평문_노출_없음() {
        given(userRepository.existsByUsername("u-sample-001")).willReturn(false);
        given(passwordEncoder.encode("샘플-비번1234")).willReturn("$2a$10$hashed-value");
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.create(new UserCreateRequest(
                "u-sample-001", "샘플-비번1234", "샘플 사용자",
                "u@example.com", "010-0000-0000", null, null));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        then(userRepository).should().save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed-value");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("샘플-비번1234");
        // 응답 DTO 에는 비밀번호 관련 필드 자체가 없음 (record 컴포넌트로 보장)
        assertThat(res.username()).isEqualTo("u-sample-001");
        then(passwordEncoder).should().encode("샘플-비번1234");
    }

    @Test
    @DisplayName("create: roleCodes 지정 시 해당 Role 을 조회해 assignRole")
    void create_role_지정_시_assignRole_호출() {
        Role role = SystemFixtures.role(1L, "ROLE_USER", "일반 사용자");
        given(userRepository.existsByUsername("u-sample-002")).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("$2a$10$h");
        given(roleRepository.findByCode("ROLE_USER")).willReturn(Optional.of(role));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.create(new UserCreateRequest(
                "u-sample-002", "샘플-비번1234", "샘플 사용자", null, null, null,
                List.of("ROLE_USER")));

        assertThat(res.roleCodes()).containsExactly("ROLE_USER");
        then(roleRepository).should().findByCode("ROLE_USER");
    }

    @Test
    @DisplayName("update: RETIRED 사용자는 수정 거부 (도메인 가드)")
    void update_RETIRED_거부() {
        UserAccount retired = SystemFixtures.retiredUser(7L, "u-sample-003");
        given(userRepository.findById(7L)).willReturn(Optional.of(retired));

        assertThatThrownBy(() -> userService.update(7L,
                new UserUpdateRequest("새 이름", null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("lock / unlock / retire 상태 전이")
    void lock_unlock_retire_상태_전이() {
        UserAccount u = SystemFixtures.activeUserWithId(3L, "u-sample-004", null);
        given(userRepository.findById(3L)).willReturn(Optional.of(u));

        userService.lock(3L);
        assertThat(u.getStatus()).isEqualTo(UserStatus.LOCKED);

        userService.unlock(3L);
        assertThat(u.getStatus()).isEqualTo(UserStatus.ACTIVE);

        userService.retire(3L);
        assertThat(u.getStatus()).isEqualTo(UserStatus.RETIRED);
    }

    @Test
    @DisplayName("changePassword: 평문을 해시로 변환 후 Entity 도메인 메서드 호출")
    void changePassword_평문_해시_변환_후_Entity_도메인_호출() {
        UserAccount u = SystemFixtures.activeUserWithId(5L, "u-sample-005", null);
        given(userRepository.findById(5L)).willReturn(Optional.of(u));
        given(passwordEncoder.encode("새-비번5678")).willReturn("$2a$10$new-hash");

        userService.changePassword(5L, "새-비번5678");

        assertThat(u.getPasswordHash()).isEqualTo("$2a$10$new-hash");
        assertThat(u.getPasswordChangedAt()).isNotNull();
        then(passwordEncoder).should().encode("새-비번5678");
    }

    @Test
    @DisplayName("search: dept/role/status/keyword 파라미터를 repository 에 그대로 전달")
    void search_dept_role_status_keyword_파라미터_repository_전달() {
        UserAccount u = SystemFixtures.activeUserWithId(9L, "u-sample-006", 2L);
        Page<UserAccount> page = new PageImpl<>(List.of(u));
        given(userRepository.search(eq(2L), eq("ROLE_USER"), eq(UserStatus.ACTIVE),
                eq("샘플"), any(Pageable.class))).willReturn(page);

        Page<UserSummary> result = userService.search(2L, "ROLE_USER", UserStatus.ACTIVE, "샘플", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).username()).isEqualTo("u-sample-006");
        then(userRepository).should().search(eq(2L), eq("ROLE_USER"), eq(UserStatus.ACTIVE),
                eq("샘플"), any(Pageable.class));
    }

    @Test
    @DisplayName("search: 빈 keyword 는 null 로 정규화해 전달")
    void search_빈_keyword_null_정규화() {
        given(userRepository.search(eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                .willReturn(Page.empty());

        userService.search(null, null, null, "   ", 0, 10);

        then(userRepository).should().search(eq(null), eq(null), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("assignRole / revokeRole 매핑 갱신")
    void assignRole_revokeRole_매핑_갱신() {
        UserAccount u = SystemFixtures.activeUserWithId(11L, "u-sample-007", null);
        Role role = SystemFixtures.role(2L, "ROLE_IT_SUPPORT", "IT 지원");
        given(userRepository.findById(11L)).willReturn(Optional.of(u));
        given(roleRepository.findByCode("ROLE_IT_SUPPORT")).willReturn(Optional.of(role));

        userService.assignRole(11L, "ROLE_IT_SUPPORT");
        assertThat(u.getRoles()).contains(role);

        userService.revokeRole(11L, "ROLE_IT_SUPPORT");
        assertThat(u.getRoles()).doesNotContain(role);
    }

    @Test
    @DisplayName("getById: 없는 사용자 USER_NOT_FOUND")
    void getById_없으면_USER_NOT_FOUND() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ITGException.class)
                .satisfies(e -> assertThat(((ITGException) e).getErrorCode()).isEqualTo("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("getById: departmentId 가 있으면 부서명을 조인해 응답에 채운다")
    void getById_부서명_조인() {
        UserAccount u = SystemFixtures.activeUserWithId(12L, "u-sample-008", 4L);
        Department dept = SystemFixtures.rootDept(4L, "DEPT-IT", "샘플 IT팀");
        given(userRepository.findById(12L)).willReturn(Optional.of(u));
        given(departmentRepository.findById(4L)).willReturn(Optional.of(dept));

        UserResponse res = userService.getById(12L);

        assertThat(res.departmentId()).isEqualTo(4L);
        assertThat(res.departmentName()).isEqualTo("샘플 IT팀");
    }

    @Test
    @DisplayName("assignRole: 없는 역할 코드 ROLE_NOT_FOUND")
    void assignRole_없는_역할_ROLE_NOT_FOUND() {
        UserAccount u = SystemFixtures.activeUserWithId(13L, "u-sample-009", null);
        given(userRepository.findById(13L)).willReturn(Optional.of(u));
        given(roleRepository.findByCode("ROLE_NONE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.assignRole(13L, "ROLE_NONE"))
                .isInstanceOf(ITGException.class)
                .satisfies(e -> assertThat(((ITGException) e).getErrorCode()).isEqualTo("ROLE_NOT_FOUND"));
    }
}
