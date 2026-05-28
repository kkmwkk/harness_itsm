package com.nkia.itg.meta.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import com.nkia.itg.meta.dto.PageMetaResponse;
import com.nkia.itg.meta.dto.PageMetaVersionResponse;
import com.nkia.itg.meta.service.MetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Meta - 페이지 메타 관리",
        description = "page_meta 의 조회·버전 전이(publish/archive/copy) API. 신규 메타 생성은 다음 phase 의 admin API 로 별도 제공.")
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final MetaService metaService;

    @Operation(
            summary = "화면 노출용 메타 조회 (PUBLISHED 최신 버전)",
            description = "groupId 기준 PUBLISHED 상태 중 (major, minor) 가 가장 높은 단 1건만 반환. "
                    + "DynamicPage 가 화면 렌더링에 사용하는 핵심 API. DRAFT 메타는 절대 노출되지 않는다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "배포된 버전이 없음")
    })
    @GetMapping("/active/{groupId}")
    public ResponseEntity<ApiResponse<PageMetaResponse>> getActive(
            @Parameter(description = "페이지 그룹 ID", example = "itg-ticket")
            @PathVariable String groupId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(metaService.getActive(groupId)));
    }

    @Operation(
            summary = "그룹 전체 버전 이력 조회",
            description = "동일 groupId 의 모든 버전(DRAFT/PUBLISHED/DEPRECATED/ARCHIVED 포함)을 "
                    + "(major DESC, minor DESC) 순으로 반환. 버전 관리 화면에서 사용."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공 (빈 리스트 가능)")
    })
    @GetMapping("/group/{groupId}/versions")
    public ResponseEntity<ApiResponse<List<PageMetaVersionResponse>>> getVersions(
            @Parameter(description = "페이지 그룹 ID", example = "itg-ticket")
            @PathVariable String groupId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(metaService.getVersions(groupId)));
    }

    @Operation(
            summary = "특정 버전 단건 조회",
            description = "metaId 로 특정 버전 1건을 조회. 자산 등록 당시 메타 복원 등 이력 추적에 사용."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "메타를 찾을 수 없음")
    })
    @GetMapping("/{metaId}")
    public ResponseEntity<ApiResponse<PageMetaResponse>> getById(
            @Parameter(description = "메타 ID ({groupId}-v{major}-{minor})", example = "itg-ticket-v1-2")
            @PathVariable String metaId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(metaService.getById(metaId)));
    }

    @Operation(
            summary = "DRAFT → PUBLISHED 배포",
            description = "DRAFT 메타를 PUBLISHED 로 전환한다. 동일 groupId 의 기존 PUBLISHED 는 "
                    + "자동 DEPRECATED 처리 (Service 로직 + DB 트리거 이중 안전망). DRAFT 가 아니면 400."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "배포 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "DRAFT 상태가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "메타를 찾을 수 없음")
    })
    @PatchMapping("/{metaId}/publish")
    public ResponseEntity<ApiResponse<PageMetaResponse>> publish(
            @Parameter(description = "배포할 DRAFT 메타 ID", example = "itg-ticket-v1-2")
            @PathVariable String metaId
    ) {
        PageMetaResponse data = metaService.publish(metaId);
        return ResponseEntity.ok(ApiResponse.ok(data, "배포되었습니다."));
    }

    @Operation(
            summary = "메타 보관 (ARCHIVED)",
            description = "메타를 ARCHIVED 로 전환한다. 이력 보관 용도. 이미 ARCHIVED 인 경우 그대로 반환."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "보관 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "메타를 찾을 수 없음")
    })
    @PatchMapping("/{metaId}/archive")
    public ResponseEntity<ApiResponse<PageMetaResponse>> archive(
            @Parameter(description = "보관할 메타 ID", example = "itg-ticket-v1-2")
            @PathVariable String metaId
    ) {
        PageMetaResponse data = metaService.archive(metaId);
        return ResponseEntity.ok(ApiResponse.ok(data, "보관 처리되었습니다."));
    }

    @Operation(
            summary = "메타 복사 (새 DRAFT 버전 생성)",
            description = "원본 메타를 복사해 동일 groupId 의 새 DRAFT 를 생성한다. minorVersion 은 기존 최대값 + 1. "
                    + "원본 상태와 무관하게 복사본은 항상 DRAFT."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "새 DRAFT 생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "원본 메타를 찾을 수 없음")
    })
    @PostMapping("/{metaId}/copy")
    public ResponseEntity<ApiResponse<PageMetaResponse>> copy(
            @Parameter(description = "복사 원본 메타 ID", example = "itg-ticket-v1-2")
            @PathVariable String metaId
    ) {
        PageMetaResponse data = metaService.copy(metaId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(data, "새 버전이 생성되었습니다."));
    }

    @Operation(
            summary = "모듈별 화면 노출 메타 목록",
            description = "systemType(ITSM/ITAM/PMS/COMMON/SYSTEM) 에 속한 PUBLISHED 메타 목록을 반환. "
                    + "모듈 단위 배포 현황 파악에 사용."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 systemType")
    })
    @GetMapping("/system/{systemType}/active")
    public ResponseEntity<ApiResponse<List<PageMetaResponse>>> getActiveBySystem(
            @Parameter(description = "시스템 모듈 구분", example = "ITSM")
            @PathVariable SystemType systemType
    ) {
        return ResponseEntity.ok(ApiResponse.ok(metaService.getActiveBySystem(systemType)));
    }

    @Operation(
            summary = "패키지 구분별 메타 목록",
            description = "packageType(PACKAGE/CUSTOM) 에 속한 모든 메타 목록을 반환. "
                    + "본사 기본 메타와 고객사 커스터마이징 분리 운영에 사용."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 packageType")
    })
    @GetMapping("/package/{packageType}")
    public ResponseEntity<ApiResponse<List<PageMetaResponse>>> getByPackage(
            @Parameter(description = "패키지 구분", example = "PACKAGE")
            @PathVariable PackageType packageType
    ) {
        return ResponseEntity.ok(ApiResponse.ok(metaService.getByPackage(packageType)));
    }

    @Operation(
            summary = "모듈 + 패키지 교차 메타 목록",
            description = "systemType 과 packageType 을 동시에 만족하는 메타 목록을 반환. "
                    + "예: ITSM 모듈의 PACKAGE 기준선 메타만 조회."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 systemType 또는 packageType")
    })
    @GetMapping("/system/{systemType}/package/{packageType}")
    public ResponseEntity<ApiResponse<List<PageMetaResponse>>> getBySystemAndPackage(
            @Parameter(description = "시스템 모듈 구분", example = "ITSM")
            @PathVariable SystemType systemType,
            @Parameter(description = "패키지 구분", example = "PACKAGE")
            @PathVariable PackageType packageType
    ) {
        return ResponseEntity.ok(ApiResponse.ok(metaService.getBySystemAndPackage(systemType, packageType)));
    }
}
