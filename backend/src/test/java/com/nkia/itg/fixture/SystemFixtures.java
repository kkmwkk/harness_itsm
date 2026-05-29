package com.nkia.itg.fixture;

import com.nkia.itg.system.dept.entity.Department;
import com.nkia.itg.system.menu.entity.Menu;
import com.nkia.itg.system.permission.entity.Permission;
import com.nkia.itg.system.role.entity.Role;
import com.nkia.itg.system.user.domain.UserStatus;
import com.nkia.itg.system.user.entity.UserAccount;
import java.util.HashSet;

/**
 * 시스템(인증·사용자·부서·역할·권한·메뉴) 도메인 목 데이터 중앙 관리 (ADR-012).
 * 테스트 데이터는 모두 가상 샘플 — 실 운영 데이터 금지.
 */
public final class SystemFixtures {

    private SystemFixtures() {
    }

    /** id 미부여(저장 전) ACTIVE 사용자. password_hash 는 BCrypt 형태의 가상 해시. */
    public static UserAccount activeUser(String username, Long deptId) {
        return UserAccount.builder()
                .username(username)
                .passwordHash("$2a$10$samplehashsamplehashsamplehashsamplehashsa")
                .name("샘플 사용자")
                .email(username + "@example.com")
                .phone("010-0000-0000")
                .departmentId(deptId)
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>())
                .build();
    }

    /** id 부여된 ACTIVE 사용자 (Mock 반환용). */
    public static UserAccount activeUserWithId(Long id, String username, Long deptId) {
        return UserAccount.builder()
                .id(id)
                .username(username)
                .passwordHash("$2a$10$samplehashsamplehashsamplehashsamplehashsa")
                .name("샘플 사용자")
                .email(username + "@example.com")
                .phone("010-0000-0000")
                .departmentId(deptId)
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>())
                .build();
    }

    public static UserAccount lockedUser(String username) {
        return UserAccount.builder()
                .username(username)
                .passwordHash("$2a$10$samplehashsamplehashsamplehashsamplehashsa")
                .name("샘플 잠긴 사용자")
                .status(UserStatus.LOCKED)
                .roles(new HashSet<>())
                .build();
    }

    public static UserAccount retiredUser(Long id, String username) {
        return UserAccount.builder()
                .id(id)
                .username(username)
                .passwordHash("$2a$10$samplehashsamplehashsamplehashsamplehashsa")
                .name("샘플 퇴직 사용자")
                .status(UserStatus.RETIRED)
                .roles(new HashSet<>())
                .build();
    }

    /** 루트 부서 — path 는 '/{id}/'. */
    public static Department rootDept(Long id, String code, String name) {
        return Department.builder()
                .id(id)
                .code(code)
                .name(name)
                .parentId(null)
                .path("/" + id + "/")
                .active(true)
                .build();
    }

    /** 자식 부서 — path 는 '{parent.path}{id}/'. */
    public static Department childDept(Department parent, Long id, String code, String name) {
        return Department.builder()
                .id(id)
                .code(code)
                .name(name)
                .parentId(parent.getId())
                .path(parent.getPath() + id + "/")
                .active(true)
                .build();
    }

    public static Role role(Long id, String code, String name) {
        return Role.builder()
                .id(id)
                .code(code)
                .name(name)
                .permissions(new HashSet<>())
                .build();
    }

    public static Permission permission(Long id, String code, String name) {
        return Permission.builder()
                .id(id)
                .code(code)
                .name(name)
                .build();
    }

    public static Menu menu(
            Long id, String code, String label, String route, String permCode) {
        return Menu.builder()
                .id(id)
                .code(code)
                .label(label)
                .route(route)
                .permissionCode(permCode)
                .sortOrder(0)
                .active(true)
                .build();
    }

    public static Menu childMenu(
            Long id, Menu parent, String code, String label, String route, String permCode) {
        return Menu.builder()
                .id(id)
                .code(code)
                .label(label)
                .route(route)
                .permissionCode(permCode)
                .parentId(parent.getId())
                .sortOrder(0)
                .active(true)
                .build();
    }
}
