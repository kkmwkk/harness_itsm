package com.nkia.itg.system.permission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "권한 정보 수정 요청 (코드는 불변)")
public record PermissionUpdateRequest(
        @Schema(description = "권한명", example = "티켓 생성")
        @Size(max = 100) String name,

        @Schema(description = "설명", example = "샘플 권한 설명")
        @Size(max = 255) String description
) {}
