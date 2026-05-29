package com.nkia.itg.system.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "역할 정보 수정 요청 (코드는 불변)")
public record RoleUpdateRequest(
        @Schema(description = "역할명", example = "IT 지원")
        @Size(max = 100) String name,

        @Schema(description = "설명", example = "샘플 역할 설명")
        @Size(max = 255) String description
) {}
