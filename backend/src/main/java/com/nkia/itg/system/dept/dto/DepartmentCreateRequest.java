package com.nkia.itg.system.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "부서 신규 생성 요청")
public record DepartmentCreateRequest(
        @Schema(description = "부서 코드", example = "DEPT-SAMPLE-IT")
        @NotBlank @Size(max = 60) String code,

        @Schema(description = "부서명", example = "샘플 IT팀")
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "상위 부서 ID (루트면 null)", example = "1")
        Long parentId
) {}
