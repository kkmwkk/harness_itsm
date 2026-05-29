package com.nkia.itg.itsm.workflow.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.itsm.workflow.dto.WorkflowDefinitionCreateRequest;
import com.nkia.itg.itsm.workflow.dto.WorkflowDefinitionResponse;
import com.nkia.itg.itsm.workflow.dto.WorkflowDefinitionUpdateRequest;
import com.nkia.itg.itsm.workflow.service.WorkflowDefinitionService;
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

@Tag(name = "ITSM Workflow Definition — 워크플로우 정의",
        description = "자체 단계 엔진(ADR-015) 워크플로우 정의 관리. 조회는 인증만, 생성·수정은 ROLE_ADMIN. "
                + "steps 는 단계 배열 JSONB 이며 저장 전 형식 검증을 거친다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/workflow-definitions")
@RequiredArgsConstructor
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService definitionService;

    @Operation(summary = "워크플로우 정의 목록", description = "전체 워크플로우 정의를 반환한다. 인증만 필요.")
    @GetMapping
    public ApiResponse<List<WorkflowDefinitionResponse>> findAll() {
        return ApiResponse.ok(definitionService.findAll());
    }

    @Operation(summary = "워크플로우 정의 단건 조회", description = "code 로 워크플로우 정의 1건을 조회한다. 인증만 필요.")
    @GetMapping("/{code}")
    public ApiResponse<WorkflowDefinitionResponse> getByCode(
            @Parameter(description = "워크플로우 정의 코드", example = "WF_INCIDENT_STD") @PathVariable String code) {
        return ApiResponse.ok(definitionService.getByCode(code));
    }

    @Operation(summary = "워크플로우 정의 생성 (관리자)",
            description = "ROLE_ADMIN 필요. steps 형식 위반 시 INVALID_WORKFLOW_DEFINITION, code 중복 시 WORKFLOW_DEF_DUPLICATE.")
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<WorkflowDefinitionResponse>> create(
            @Valid @RequestBody WorkflowDefinitionCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(definitionService.create(req), "워크플로우 정의가 생성되었습니다."));
    }

    @Operation(summary = "워크플로우 정의 수정 (관리자)",
            description = "ROLE_ADMIN 필요. code 는 불변. steps 형식 위반 시 INVALID_WORKFLOW_DEFINITION.")
    @PatchMapping("/{code}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ApiResponse<WorkflowDefinitionResponse> update(
            @Parameter(description = "워크플로우 정의 코드", example = "WF_INCIDENT_STD") @PathVariable String code,
            @Valid @RequestBody WorkflowDefinitionUpdateRequest req) {
        return ApiResponse.ok(definitionService.update(code, req));
    }
}
