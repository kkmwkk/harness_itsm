package com.nkia.itg.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 비밀번호 변경 요청 (평문 — 서버에서 BCrypt 해시). */
@Schema(description = "비밀번호 변경 요청")
public record PasswordChangeRequest(
        @Schema(description = "새 비밀번호(평문)", example = "샘플-새비번5678")
        @NotBlank @Size(min = 8, max = 72) String newPassword
) {}
