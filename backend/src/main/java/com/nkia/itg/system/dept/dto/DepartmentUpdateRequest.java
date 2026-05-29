package com.nkia.itg.system.dept.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "부서 정보 수정 요청 (트리 이동은 별도 move API)")
public record DepartmentUpdateRequest(
        @Schema(description = "부서명", example = "샘플 IT팀")
        @Size(max = 100) String name,

        @Schema(description = "부서장 사용자 ID (해제는 null)", example = "1")
        Long managerUserId
) {}
