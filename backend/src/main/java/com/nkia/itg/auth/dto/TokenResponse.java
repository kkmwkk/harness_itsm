package com.nkia.itg.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 로그인·refresh 성공 응답. access/refresh 토큰 + 사용자·역할·권한. */
public record TokenResponse(
        @Schema(description = "access 토큰 (Bearer)") String accessToken,
        @Schema(description = "refresh 토큰") String refreshToken,
        @Schema(description = "access 토큰 만료까지 남은 초", example = "900") long accessExpiresInSec,
        UserSummary user,
        @Schema(description = "역할 코드 목록", example = "[\"ROLE_ADMIN\"]") List<String> roles,
        @Schema(description = "권한 코드 목록", example = "[\"META_PUBLISH\"]") List<String> permissions
) {}
