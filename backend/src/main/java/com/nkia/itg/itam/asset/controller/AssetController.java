package com.nkia.itg.itam.asset.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.common.response.PageResponse;
import com.nkia.itg.itam.asset.domain.AssetStatus;
import com.nkia.itg.itam.asset.domain.AssetType;
import com.nkia.itg.itam.asset.dto.AssetAssignRequest;
import com.nkia.itg.itam.asset.dto.AssetCreateRequest;
import com.nkia.itg.itam.asset.dto.AssetResponse;
import com.nkia.itg.itam.asset.dto.AssetStatusChangeRequest;
import com.nkia.itg.itam.asset.dto.AssetSummary;
import com.nkia.itg.itam.asset.dto.AssetUpdateRequest;
import com.nkia.itg.itam.asset.service.AssetService;
import com.nkia.itg.meta.dto.PageMetaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ITAM Asset — 자산원장 관리",
        description = "ITAM 자산 등록·조회·상태 전이·담당자 할당 API. 물리 삭제는 제공하지 않으며 "
                + "RETIRED·REPLACED 상태 전이로 처분한다. 등록 시점 메타는 이력 복원용으로 보존된다.")
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @Operation(
            summary = "자산 신규 등록",
            description = "신규 자산을 등록한다. 상태는 ACTIVE 로 시작하고 assetNo(AST-{id5}) 가 자동 부여된다. "
                    + "등록 시점에 pageGroupId 의 PUBLISHED 메타 ID 를 캡처해 이력 복원용으로 보존한다. "
                    + "배포된 메타가 없으면 META_NOT_PUBLISHED 로 등록을 거부한다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "배포된 메타 없음 (META_NOT_PUBLISHED)")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<AssetResponse>> create(
            @Valid @RequestBody AssetCreateRequest req
    ) {
        AssetResponse data = assetService.create(req);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(data, "자산이 등록되었습니다."));
    }

    @Operation(
            summary = "자산 목록 검색 (페이지)",
            description = "상태·자산 유형·담당자 ID 로 필터링한 자산 목록을 생성일 내림차순으로 페이지 반환. "
                    + "모든 필터는 선택값이며 미지정 시 전체 대상."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공 (빈 페이지 가능)")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AssetSummary>>> search(
            @Parameter(description = "상태", example = "ACTIVE")
            @RequestParam(required = false) AssetStatus status,
            @Parameter(description = "자산 유형", example = "HARDWARE")
            @RequestParam(required = false) AssetType assetType,
            @Parameter(description = "담당자 ID", example = "assignee-sample-1")
            @RequestParam(required = false) String assigneeId,
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AssetSummary> result = assetService.search(status, assetType, assigneeId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @Operation(
            summary = "자산 단건 조회 (내부 ID)",
            description = "내부 PK 로 자산 1건을 조회한다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "자산 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetResponse>> getById(
            @Parameter(description = "자산 PK", example = "42") @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(assetService.getById(id)));
    }

    @Operation(
            summary = "자산 단건 조회 (자산 번호)",
            description = "표시용 자산 번호(AST-{id5}) 로 자산 1건을 조회한다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "자산 없음")
    })
    @GetMapping("/by-no/{assetNo}")
    public ResponseEntity<ApiResponse<AssetResponse>> getByAssetNo(
            @Parameter(description = "표시용 자산 번호", example = "AST-00042") @PathVariable String assetNo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(assetService.getByAssetNo(assetNo)));
    }

    @Operation(
            summary = "자산 속성 부분 수정",
            description = "자산명·모델·시리얼·분류·위치만 수정한다. 상태·담당자는 별도 엔드포인트로 처리. "
                    + "RETIRED 자산은 수정 불가."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "RETIRED 자산 수정 불가 또는 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "자산 없음")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetResponse>> update(
            @Parameter(description = "자산 PK", example = "42") @PathVariable Long id,
            @Valid @RequestBody AssetUpdateRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(assetService.update(id, req)));
    }

    @Operation(
            summary = "자산 상태 전이",
            description = "상태 전이 매트릭스에 따라 상태를 변경한다 (ACTIVE/STORAGE/RETIRED/REPLACED). "
                    + "RETIRED 는 종료 상태로 이후 전이 불가."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전이 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "허용되지 않은 전이"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "자산 없음")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AssetResponse>> changeStatus(
            @Parameter(description = "자산 PK", example = "42") @PathVariable Long id,
            @Valid @RequestBody AssetStatusChangeRequest req
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(assetService.changeStatus(id, req), "상태가 변경되었습니다.")
        );
    }

    @Operation(
            summary = "자산 담당자 할당/해제",
            description = "담당자 ID 를 변경하거나 null/blank 로 해제한다. RETIRED 자산은 변경 불가."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "할당 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "RETIRED 자산 변경 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "자산 없음")
    })
    @PatchMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<AssetResponse>> assign(
            @Parameter(description = "자산 PK", example = "42") @PathVariable Long id,
            @Valid @RequestBody AssetAssignRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(assetService.assign(id, req)));
    }

    @Operation(
            summary = "자산 등록 시점 메타 조회 (이력 복원)",
            description = "자산 등록 시점에 사용된 PageMeta 를 반환한다. 메타가 현재 DEPRECATED·ARCHIVED 라도 "
                    + "그대로 반환 — 자산 이력 화면 복원에 사용 (PRD §5-2)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "자산 또는 메타 없음")
    })
    @GetMapping("/{id}/registration-meta")
    public ResponseEntity<ApiResponse<PageMetaResponse>> getRegistrationMeta(
            @Parameter(description = "자산 PK", example = "42") @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(assetService.getRegistrationMeta(id)));
    }
}
