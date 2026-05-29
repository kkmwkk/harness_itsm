package com.nkia.itg.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Schema(description = "사용자 정보 수정 요청 (프로필 필드만 — 상태·비밀번호·역할은 별도 API)")
public record UserUpdateRequest(
        @Schema(description = "이름", example = "샘플 사용자")
        @Size(max = 100) String name,

        @Schema(description = "이메일", example = "u@example.com")
        @Email @Size(max = 120) String email,

        @Schema(description = "전화번호", example = "010-0000-0000")
        @Size(max = 30) String phone,

        @Schema(description = "부서 ID", example = "1")
        Long departmentId
) {}
