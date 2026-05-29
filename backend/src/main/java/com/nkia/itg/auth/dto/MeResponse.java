package com.nkia.itg.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 현재 인증 사용자 정보 (GET /api/auth/me). */
public record MeResponse(
        UserSummary user,
        @Schema(description = "역할 코드 목록", example = "[\"ROLE_ADMIN\"]") List<String> roles,
        @Schema(description = "권한 코드 목록", example = "[\"META_PUBLISH\"]") List<String> permissions
) {}
