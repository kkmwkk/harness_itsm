package com.nkia.itg.system.permission.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.system.permission.dto.PermissionCreateRequest;
import com.nkia.itg.system.permission.dto.PermissionResponse;
import com.nkia.itg.system.permission.dto.PermissionUpdateRequest;
import com.nkia.itg.system.permission.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System / Permission — 권한",
        description = "권한 CRUD. 권한 목록은 민감 정보이므로 조회·변경 모두 ROLE_ADMIN 필요.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "권한 목록 조회", description = "ROLE_ADMIN 필요 (권한 목록은 민감).")
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<List<PermissionResponse>> findAll() {
        return ApiResponse.ok(permissionService.findAll());
    }

    @Operation(summary = "권한 단건 조회", description = "ROLE_ADMIN 필요.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<PermissionResponse> getById(
            @Parameter(description = "권한 ID", example = "1") @PathVariable Long id) {
        return ApiResponse.ok(permissionService.getById(id));
    }

    @Operation(summary = "권한 생성", description = "ROLE_ADMIN 필요.")
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<PermissionResponse>> create(@Valid @RequestBody PermissionCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(permissionService.create(req), "권한이 생성되었습니다."));
    }

    @Operation(summary = "권한 수정", description = "ROLE_ADMIN 필요. 코드는 불변.")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<PermissionResponse> update(
            @Parameter(description = "권한 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody PermissionUpdateRequest req) {
        return ApiResponse.ok(permissionService.update(id, req));
    }
}
