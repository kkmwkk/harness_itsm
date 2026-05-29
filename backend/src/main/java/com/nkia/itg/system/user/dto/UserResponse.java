package com.nkia.itg.system.user.dto;

import com.nkia.itg.system.permission.entity.Permission;
import com.nkia.itg.system.role.entity.Role;
import com.nkia.itg.system.user.domain.UserStatus;
import com.nkia.itg.system.user.entity.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 단건 응답. 비밀번호 해시는 절대 포함하지 않는다.
 * roleCodes·permissionCodes 는 트랜잭션 내 LAZY 로드를 전제로 채운다.
 * departmentName 은 UserAccount 가 departmentId 만 보유하므로 Service 가 조인해 주입한다.
 */
@Schema(description = "사용자 단건 응답")
public record UserResponse(
        Long id,
        String username,
        String name,
        String email,
        String phone,
        UserStatus status,
        Long departmentId,
        String departmentName,
        List<String> roleCodes,
        List<String> permissionCodes,
        LocalDateTime passwordChangedAt,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserResponse from(UserAccount u, String departmentName) {
        List<String> roleCodes = u.getRoles().stream()
                .map(Role::getCode)
                .sorted()
                .toList();
        List<String> permissionCodes = u.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .sorted()
                .toList();
        return new UserResponse(
                u.getId(), u.getUsername(), u.getName(), u.getEmail(), u.getPhone(),
                u.getStatus(), u.getDepartmentId(), departmentName,
                roleCodes, permissionCodes,
                u.getPasswordChangedAt(), u.getLastLoginAt(),
                u.getCreatedAt(), u.getUpdatedAt());
    }
}
