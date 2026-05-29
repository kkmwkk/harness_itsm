package com.nkia.itg.system.role.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.system.role.dto.RoleCreateRequest;
import com.nkia.itg.system.role.dto.RoleResponse;
import com.nkia.itg.system.role.dto.RoleUpdateRequest;
import com.nkia.itg.system.role.service.RoleService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System / Role — 역할",
        description = "역할 CRUD·권한 부여/회수. 조회는 인증만, 변경은 ROLE_ADMIN 필요.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "역할 목록 조회", description = "인증만 필요.")
    @GetMapping
    public ApiResponse<List<RoleResponse>> findAll() {
        return ApiResponse.ok(roleService.findAll());
    }

    @Operation(summary = "역할 단건 조회", description = "인증만 필요.")
    @GetMapping("/{id}")
    public ApiResponse<RoleResponse> getById(
            @Parameter(description = "역할 ID", example = "1") @PathVariable Long id) {
        return ApiResponse.ok(roleService.getById(id));
    }

    @Operation(summary = "역할 생성", description = "ROLE_ADMIN 필요.")
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody RoleCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(roleService.create(req), "역할이 생성되었습니다."));
    }

    @Operation(summary = "역할 수정", description = "ROLE_ADMIN 필요. 코드는 불변.")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<RoleResponse> update(
            @Parameter(description = "역할 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest req) {
        return ApiResponse.ok(roleService.update(id, req));
    }

    @Operation(summary = "역할에 권한 부여", description = "ROLE_ADMIN 필요.")
    @PostMapping("/{roleCode}/permissions/{permissionCode}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<Void> grantPermission(
            @Parameter(description = "역할 코드", example = "ROLE_IT_SUPPORT") @PathVariable String roleCode,
            @Parameter(description = "권한 코드", example = "TICKET_CREATE") @PathVariable String permissionCode) {
        roleService.grantPermission(roleCode, permissionCode);
        return ApiResponse.ok(null, "권한이 부여되었습니다.");
    }

    @Operation(summary = "역할에서 권한 회수", description = "ROLE_ADMIN 필요.")
    @DeleteMapping("/{roleCode}/permissions/{permissionCode}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<Void> revokePermission(
            @Parameter(description = "역할 코드", example = "ROLE_IT_SUPPORT") @PathVariable String roleCode,
            @Parameter(description = "권한 코드", example = "TICKET_CREATE") @PathVariable String permissionCode) {
        roleService.revokePermission(roleCode, permissionCode);
        return ApiResponse.ok(null, "권한이 회수되었습니다.");
    }
}
