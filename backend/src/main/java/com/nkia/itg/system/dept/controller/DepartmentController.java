package com.nkia.itg.system.dept.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.system.dept.dto.DepartmentCreateRequest;
import com.nkia.itg.system.dept.dto.DepartmentResponse;
import com.nkia.itg.system.dept.dto.DepartmentTreeNode;
import com.nkia.itg.system.dept.dto.DepartmentUpdateRequest;
import com.nkia.itg.system.dept.service.DepartmentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System / Department — 부서",
        description = "부서 트리 조회·CRUD·이동. 조회는 인증만, 변경은 ROLE_ADMIN 또는 DEPT_ADMIN 필요.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "부서 트리 조회", description = "parent_id 기준 조립된 부서 트리. 인증만 필요.")
    @GetMapping
    public ApiResponse<List<DepartmentTreeNode>> tree() {
        return ApiResponse.ok(departmentService.getTree());
    }

    @Operation(summary = "부서 트리 조회 (명시 경로)", description = "GET /api/departments 와 동일한 트리. 인증만 필요.")
    @GetMapping("/tree")
    public ApiResponse<List<DepartmentTreeNode>> treeExplicit() {
        return ApiResponse.ok(departmentService.getTree());
    }

    @Operation(summary = "부서 생성", description = "ROLE_ADMIN 또는 DEPT_ADMIN 필요.")
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('DEPT_ADMIN')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(@Valid @RequestBody DepartmentCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(departmentService.create(req), "부서가 생성되었습니다."));
    }

    @Operation(summary = "부서 수정", description = "ROLE_ADMIN 또는 DEPT_ADMIN 필요.")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('DEPT_ADMIN')")
    public ApiResponse<DepartmentResponse> update(
            @Parameter(description = "부서 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody DepartmentUpdateRequest req) {
        return ApiResponse.ok(departmentService.update(id, req));
    }

    @Operation(summary = "부서 트리 이동", description = "ROLE_ADMIN 또는 DEPT_ADMIN 필요. 자기·자손으로 이동은 거부.")
    @PatchMapping("/{id}/move")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('DEPT_ADMIN')")
    public ApiResponse<Void> move(
            @Parameter(description = "부서 ID", example = "3") @PathVariable Long id,
            @Parameter(description = "새 상위 부서 ID (루트로 이동은 생략)", example = "1")
            @RequestParam(required = false) Long newParentId) {
        departmentService.move(id, newParentId);
        return ApiResponse.ok(null, "부서가 이동되었습니다.");
    }

    @Operation(summary = "부서 비활성화", description = "ROLE_ADMIN 또는 DEPT_ADMIN 필요.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('DEPT_ADMIN')")
    public ApiResponse<Void> deactivate(
            @Parameter(description = "부서 ID", example = "1") @PathVariable Long id) {
        departmentService.deactivate(id);
        return ApiResponse.ok(null, "부서가 비활성화되었습니다.");
    }
}
