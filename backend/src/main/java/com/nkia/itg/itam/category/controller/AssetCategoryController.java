package com.nkia.itg.itam.category.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.itam.category.dto.AssetCategoryCreateRequest;
import com.nkia.itg.itam.category.dto.AssetCategoryResponse;
import com.nkia.itg.itam.category.dto.AssetCategoryTreeNode;
import com.nkia.itg.itam.category.dto.AssetCategoryUpdateRequest;
import com.nkia.itg.itam.category.service.AssetCategoryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ITAM AssetCategory — 자산 분류",
        description = "자산 분류 트리(code 식별, parent_code 자기참조). 조회는 인증만, 생성·수정·이동은 ASSET_ADMIN. "
                + "자기·자손으로의 이동은 INVALID_REQUEST(400) 로 거부된다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/asset-categories")
@RequiredArgsConstructor
public class AssetCategoryController {

    private final AssetCategoryService assetCategoryService;

    @Operation(summary = "자산 분류 트리 조회", description = "parent_code 기준으로 조립한 자산 분류 트리를 반환한다. 인증만 필요.")
    @GetMapping("/tree")
    public ApiResponse<List<AssetCategoryTreeNode>> getTree() {
        return ApiResponse.ok(assetCategoryService.getTree());
    }

    @Operation(summary = "자산 분류 단건 조회", description = "code 로 자산 분류 1건을 조회한다. 인증만 필요.")
    @GetMapping("/{code}")
    public ApiResponse<AssetCategoryResponse> getByCode(
            @Parameter(description = "분류 코드", example = "HW_LAPTOP") @PathVariable String code) {
        return ApiResponse.ok(AssetCategoryResponse.from(assetCategoryService.getByCode(code)));
    }

    @Operation(summary = "자산 분류 생성 (관리자)",
            description = "ASSET_ADMIN 필요. code 중복 시 ASSET_CATEGORY_DUPLICATE. path 는 서버가 자동 계산한다.")
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ASSET_ADMIN')")
    public ResponseEntity<ApiResponse<AssetCategoryResponse>> create(
            @Valid @RequestBody AssetCategoryCreateRequest req) {
        AssetCategoryResponse data = AssetCategoryResponse.from(assetCategoryService.create(
                req.code(), req.label(), req.parentCode(), req.formMetaGroupId(), req.sortOrder()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(data, "자산 분류가 생성되었습니다."));
    }

    @Operation(summary = "자산 분류 수정 (관리자)", description = "ASSET_ADMIN 필요. code·트리 위치는 불변.")
    @PatchMapping("/{code}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ASSET_ADMIN')")
    public ApiResponse<AssetCategoryResponse> update(
            @Parameter(description = "분류 코드", example = "HW_LAPTOP") @PathVariable String code,
            @Valid @RequestBody AssetCategoryUpdateRequest req) {
        AssetCategoryResponse data = AssetCategoryResponse.from(assetCategoryService.update(
                code, req.label(), req.formMetaGroupId(), req.sortOrder()));
        return ApiResponse.ok(data);
    }

    @Operation(summary = "자산 분류 트리 이동 (관리자)",
            description = "ASSET_ADMIN 필요. 자기·자손으로의 이동은 INVALID_REQUEST(400) 로 거부한다.")
    @PostMapping("/{code}/move")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ASSET_ADMIN')")
    public ApiResponse<Void> move(
            @Parameter(description = "분류 코드", example = "HW_LAPTOP") @PathVariable String code,
            @Parameter(description = "새 상위 분류 코드 (루트면 생략)", example = "HW") @RequestParam(required = false) String newParentCode) {
        assetCategoryService.move(code, newParentCode);
        return ApiResponse.ok(null, "자산 분류가 이동되었습니다.");
    }
}
