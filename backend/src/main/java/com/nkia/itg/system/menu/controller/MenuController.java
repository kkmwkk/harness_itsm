package com.nkia.itg.system.menu.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.system.menu.dto.MenuCreateRequest;
import com.nkia.itg.system.menu.dto.MenuResponse;
import com.nkia.itg.system.menu.dto.MenuTreeNode;
import com.nkia.itg.system.menu.dto.MenuUpdateRequest;
import com.nkia.itg.system.menu.service.MenuService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System / Menu — 메뉴",
        description = "동적 메뉴 트리. /api/menu 는 현재 사용자 권한으로 필터된 트리, /api/menus 는 관리자용 전체 관리.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @Operation(summary = "내 메뉴 트리",
            description = "현재 인증 사용자의 권한으로 필터된 활성 메뉴 트리. "
                    + "permissionCode 가 null(누구나) 이거나 사용자가 보유한 권한과 매칭되는 메뉴만 포함. 인증만 필요.")
    @GetMapping("/menu")
    public ApiResponse<List<MenuTreeNode>> myMenu(Authentication authentication) {
        List<String> codes = (authentication == null) ? List.of()
                : authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList();
        return ApiResponse.ok(menuService.getTreeFor(codes));
    }

    @Operation(summary = "전체 메뉴 목록 (관리자)", description = "활성·비활성·권한 무관 전체 메뉴. ROLE_ADMIN 또는 MENU_ADMIN 필요.")
    @GetMapping("/menus")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('MENU_ADMIN')")
    public ApiResponse<List<MenuResponse>> findAll() {
        return ApiResponse.ok(menuService.findAll());
    }

    @Operation(summary = "메뉴 단건 조회 (관리자)", description = "ROLE_ADMIN 또는 MENU_ADMIN 필요.")
    @GetMapping("/menus/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('MENU_ADMIN')")
    public ApiResponse<MenuResponse> getById(
            @Parameter(description = "메뉴 ID", example = "1") @PathVariable Long id) {
        return ApiResponse.ok(menuService.getById(id));
    }

    @Operation(summary = "메뉴 생성 (관리자)", description = "ROLE_ADMIN 또는 MENU_ADMIN 필요.")
    @PostMapping("/menus")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('MENU_ADMIN')")
    public ResponseEntity<ApiResponse<MenuResponse>> create(@Valid @RequestBody MenuCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(menuService.create(req), "메뉴가 생성되었습니다."));
    }

    @Operation(summary = "메뉴 수정 (관리자)", description = "ROLE_ADMIN 또는 MENU_ADMIN 필요.")
    @PatchMapping("/menus/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('MENU_ADMIN')")
    public ApiResponse<MenuResponse> update(
            @Parameter(description = "메뉴 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody MenuUpdateRequest req) {
        return ApiResponse.ok(menuService.update(id, req));
    }

    @Operation(summary = "메뉴 트리 이동 (관리자)", description = "ROLE_ADMIN 또는 MENU_ADMIN 필요.")
    @PatchMapping("/menus/{id}/move")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('MENU_ADMIN')")
    public ApiResponse<Void> move(
            @Parameter(description = "메뉴 ID", example = "3") @PathVariable Long id,
            @Parameter(description = "새 상위 메뉴 ID (루트면 생략)", example = "1") @RequestParam(required = false) Long newParentId,
            @Parameter(description = "새 정렬 순서", example = "0") @RequestParam(defaultValue = "0") int newSortOrder) {
        menuService.move(id, newParentId, newSortOrder);
        return ApiResponse.ok(null, "메뉴가 이동되었습니다.");
    }

    @Operation(summary = "메뉴 비활성화 (관리자)", description = "ROLE_ADMIN 또는 MENU_ADMIN 필요.")
    @DeleteMapping("/menus/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('MENU_ADMIN')")
    public ApiResponse<Void> deactivate(
            @Parameter(description = "메뉴 ID", example = "1") @PathVariable Long id) {
        menuService.deactivate(id);
        return ApiResponse.ok(null, "메뉴가 비활성화되었습니다.");
    }
}
