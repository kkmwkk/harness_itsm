package com.nkia.itg.auth.dto;

import com.nkia.itg.system.user.entity.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 인증 응답에 실리는 사용자 요약. 비밀번호 해시 등 민감 정보는 절대 포함하지 않는다.
 * departmentName 은 UserAccount 가 departmentId 만 보유하므로 본 팩토리에서는 채우지 않는다
 * (부서명 조인은 사용자 조회 API 의 책임 — 별도 step).
 */
public record UserSummary(
        @Schema(description = "사용자 PK", example = "1") Long id,
        @Schema(description = "사용자명", example = "admin") String username,
        @Schema(description = "이름", example = "샘플-관리자") String name,
        @Schema(description = "이메일", example = "sample@example.com") String email,
        @Schema(description = "부서명", example = "샘플-운영팀") String departmentName
) {
    public static UserSummary from(UserAccount u) {
        return new UserSummary(u.getId(), u.getUsername(), u.getName(), u.getEmail(), null);
    }
}
