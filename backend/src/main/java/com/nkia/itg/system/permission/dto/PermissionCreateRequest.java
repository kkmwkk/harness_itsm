package com.nkia.itg.system.permission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "권한 신규 생성 요청")
public record PermissionCreateRequest(
        @Schema(description = "권한 코드", example = "TICKET_CREATE")
        @NotBlank @Size(max = 60) String code,

        @Schema(description = "권한명", example = "티켓 생성")
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "설명", example = "샘플 권한 설명")
        @Size(max = 255) String description
) {}
