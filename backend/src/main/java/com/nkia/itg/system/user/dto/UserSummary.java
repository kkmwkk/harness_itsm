package com.nkia.itg.system.user.dto;

import com.nkia.itg.system.role.entity.Role;
import com.nkia.itg.system.user.domain.UserStatus;
import com.nkia.itg.system.user.entity.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 목록(검색) 행 요약. 비밀번호 해시·권한 평탄화는 포함하지 않는다.
 */
@Schema(description = "사용자 목록 행 요약")
public record UserSummary(
        Long id,
        String username,
        String name,
        String email,
        UserStatus status,
        Long departmentId,
        List<String> roleCodes,
        LocalDateTime lastLoginAt
) {
    public static UserSummary from(UserAccount u) {
        List<String> roleCodes = u.getRoles().stream()
                .map(Role::getCode)
                .sorted()
                .toList();
        return new UserSummary(
                u.getId(), u.getUsername(), u.getName(), u.getEmail(),
                u.getStatus(), u.getDepartmentId(), roleCodes, u.getLastLoginAt());
    }
}
