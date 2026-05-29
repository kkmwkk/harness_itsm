package com.nkia.itg.system.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "역할 신규 생성 요청")
public record RoleCreateRequest(
        @Schema(description = "역할 코드", example = "ROLE_IT_SUPPORT")
        @NotBlank @Size(max = 60) String code,

        @Schema(description = "역할명", example = "IT 지원")
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "설명", example = "샘플 역할 설명")
        @Size(max = 255) String description
) {}
