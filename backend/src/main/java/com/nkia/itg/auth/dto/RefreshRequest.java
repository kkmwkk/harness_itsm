package com.nkia.itg.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** refresh 토큰으로 새 access 토큰을 발급받는 요청. */
public record RefreshRequest(
        @Schema(description = "refresh 토큰", example = "eyJhbGciOiJIUzI1NiJ9...")
        @NotBlank String refreshToken
) {}
