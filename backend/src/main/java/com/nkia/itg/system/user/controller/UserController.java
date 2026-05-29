package com.nkia.itg.system.user.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.common.response.PageResponse;
import com.nkia.itg.system.user.domain.UserStatus;
import com.nkia.itg.system.user.dto.PasswordChangeRequest;
import com.nkia.itg.system.user.dto.UserCreateRequest;
import com.nkia.itg.system.user.dto.UserResponse;
import com.nkia.itg.system.user.dto.UserSummary;
import com.nkia.itg.system.user.dto.UserUpdateRequest;
import com.nkia.itg.system.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "System / User — 사용자",
        description = "사용자 CRUD·상태 전이·비밀번호 변경·역할 할당. 비밀번호 해시는 어떤 응답에도 포함되지 않는다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "사용자 검색", description = "부서·역할·상태·키워드로 페이지 검색. USER_READ 또는 ROLE_ADMIN 필요.")
    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ') or hasAuthority('ROLE_ADMIN')")
    public ApiResponse<PageResponse<UserSummary>> search(
            @Parameter(description = "부서 ID", example = "1") @RequestParam(required = false) Long deptId,
            @Parameter(description = "역할 코드", example = "ROLE_USER") @RequestParam(required = false) String role,
            @Parameter(description = "사용자 상태") @RequestParam(required = false) UserStatus status,
            @Parameter(description = "검색 키워드(사용자명·이름·이메일)", example = "샘플") @RequestParam(required = false) String kw,
            @Parameter(description = "페이지(0-base)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(PageResponse.from(userService.search(deptId, role, status, kw, page, size)));
    }

    @Operation(summary = "사용자 단건 조회", description = "USER_READ 또는 ROLE_ADMIN 필요.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ') or hasAuthority('ROLE_ADMIN')")
    public ApiResponse<UserResponse> getById(
            @Parameter(description = "사용자 ID", example = "1") @PathVariable Long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    @Operation(summary = "사용자 생성", description = "USER_ADMIN 필요. 비밀번호는 BCrypt 해시로 저장.")
    @PostMapping
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(userService.create(req), "사용자가 생성되었습니다."));
    }

    @Operation(summary = "사용자 프로필 수정", description = "USER_ADMIN 필요. 상태·비밀번호·역할은 별도 API.")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<UserResponse> update(
            @Parameter(description = "사용자 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest req) {
        return ApiResponse.ok(userService.update(id, req));
    }

    @Operation(summary = "사용자 잠금", description = "USER_ADMIN 필요.")
    @PatchMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<Void> lock(
            @Parameter(description = "사용자 ID", example = "1") @PathVariable Long id) {
        userService.lock(id);
        return ApiResponse.ok(null, "잠금 처리되었습니다.");
    }

    @Operation(summary = "사용자 잠금 해제", description = "USER_ADMIN 필요.")
    @PatchMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<Void> unlock(
            @Parameter(description = "사용자 ID", example = "1") @PathVariable Long id) {
        userService.unlock(id);
        return ApiResponse.ok(null, "잠금이 해제되었습니다.");
    }

    @Operation(summary = "사용자 퇴직 처리", description = "USER_ADMIN 필요.")
    @PatchMapping("/{id}/retire")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<Void> retire(
            @Parameter(description = "사용자 ID", example = "1") @PathVariable Long id) {
        userService.retire(id);
        return ApiResponse.ok(null, "퇴직 처리되었습니다.");
    }

    @Operation(summary = "비밀번호 변경",
            description = "USER_ADMIN 이거나 본인(@selfCheck) 인 경우만 허용. 평문은 BCrypt 해시로 저장.")
    @PatchMapping("/{id}/password")
    @PreAuthorize("hasAuthority('USER_ADMIN') or @selfCheck.isSelf(authentication, #id)")
    public ApiResponse<Void> changePassword(
            @Parameter(description = "사용자 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody PasswordChangeRequest req) {
        userService.changePassword(id, req.newPassword());
        return ApiResponse.ok(null, "비밀번호가 변경되었습니다.");
    }

    @Operation(summary = "역할 부여", description = "USER_ADMIN 필요.")
    @PostMapping("/{id}/roles/{roleCode}")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<Void> assignRole(
            @Parameter(description = "사용자 ID", example = "1") @PathVariable Long id,
            @Parameter(description = "역할 코드", example = "ROLE_IT_SUPPORT") @PathVariable String roleCode) {
        userService.assignRole(id, roleCode);
        return ApiResponse.ok(null, "역할이 부여되었습니다.");
    }

    @Operation(summary = "역할 회수", description = "USER_ADMIN 필요.")
    @DeleteMapping("/{id}/roles/{roleCode}")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<Void> revokeRole(
            @Parameter(description = "사용자 ID", example = "1") @PathVariable Long id,
            @Parameter(description = "역할 코드", example = "ROLE_IT_SUPPORT") @PathVariable String roleCode) {
        userService.revokeRole(id, roleCode);
        return ApiResponse.ok(null, "역할이 회수되었습니다.");
    }
}
