package com.nkia.itg.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 로그인 요청. 사용자명/비밀번호. */
public record LoginRequest(
        @Schema(description = "사용자명", example = "admin")
        @NotBlank String username,
        @Schema(description = "비밀번호", example = "샘플-비번")
        @NotBlank String password
) {}
