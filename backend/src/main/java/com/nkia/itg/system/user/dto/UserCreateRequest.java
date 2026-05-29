package com.nkia.itg.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "사용자 신규 생성 요청")
public record UserCreateRequest(
        @Schema(description = "사용자명(로그인 ID)", example = "u-sample-001")
        @NotBlank @Size(max = 60) String username,

        @Schema(description = "비밀번호(평문 — 서버에서 BCrypt 해시)", example = "샘플-비번1234")
        @NotBlank @Size(min = 8, max = 72) String password,

        @Schema(description = "이름", example = "샘플 사용자")
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "이메일", example = "u@example.com")
        @Email @Size(max = 120) String email,

        @Schema(description = "전화번호", example = "010-0000-0000")
        @Size(max = 30) String phone,

        @Schema(description = "부서 ID", example = "1")
        Long departmentId,

        @Schema(description = "부여할 역할 코드 목록", example = "[\"ROLE_USER\"]")
        List<String> roleCodes
) {}
