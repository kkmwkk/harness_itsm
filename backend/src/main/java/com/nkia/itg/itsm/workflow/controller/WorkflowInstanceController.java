package com.nkia.itg.itsm.workflow.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.itsm.workflow.dto.WorkflowActionRequest;
import com.nkia.itg.itsm.workflow.dto.WorkflowInstanceResponse;
import com.nkia.itg.itsm.workflow.service.WorkflowEngineService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ITSM Workflow Instance — 워크플로우 인스턴스",
        description = "티켓이 거치는 워크플로우 인스턴스 조회·단계 액션. 단계 액션은 인증 사용자의 역할이 단계 "
                + "assignee_role 과 매칭될 때만 실행 가능 — 미매칭·전이 불가 시 INVALID_WORKFLOW_ACTION(400).")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/workflow-instances")
@RequiredArgsConstructor
public class WorkflowInstanceController {

    private final WorkflowEngineService workflowEngineService;

    @Operation(summary = "티켓의 워크플로우 인스턴스 + 단계 이력 조회",
            description = "티켓 ID 로 워크플로우 인스턴스 1건과 단계 실행 이력을 반환한다. 인증만 필요.")
    @GetMapping("/by-ticket/{ticketId}")
    public ApiResponse<WorkflowInstanceResponse> getByTicket(
            @Parameter(description = "티켓 PK", example = "42") @PathVariable Long ticketId) {
        return ApiResponse.ok(workflowEngineService.getByTicket(ticketId));
    }

    @Operation(summary = "워크플로우 단계 액션 실행",
            description = "현재 단계(idx)에 액션을 실행한다. 인증 사용자의 역할을 단계 assignee_role 과 대조해 "
                    + "권한을 검증하며, 허용되지 않은 액션·전이·역할 미보유 시 INVALID_WORKFLOW_ACTION(400).")
    @PostMapping("/{id}/step/{idx}/action")
    public ApiResponse<WorkflowInstanceResponse> executeAction(
            @Parameter(description = "워크플로우 인스턴스 ID", example = "100") @PathVariable Long id,
            @Parameter(description = "현재 단계 인덱스 (0-based)", example = "1") @PathVariable int idx,
            @Valid @RequestBody WorkflowActionRequest req,
            Authentication authentication) {
        Set<String> actorRoles = extractRoles(authentication);
        Long actorUserId = extractUserId(authentication);
        WorkflowInstanceResponse data = workflowEngineService.executeActionAndLoad(
                id, idx, req.action(), actorUserId, actorRoles, req.comment());
        return ApiResponse.ok(data, "단계 액션이 처리되었습니다.");
    }

    /** 인증 사용자의 권한(roles+perms)을 문자열 집합으로 추출 — 단계 역할 검증에 사용. */
    private Set<String> extractRoles(Authentication authentication) {
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    /** JWT uid 클레임에서 실행자 사용자 ID 추출 (없으면 null — 이력에 미기록). */
    private Long extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof Claims claims) {
            Object uid = claims.get("uid");
            if (uid instanceof Number number) {
                return number.longValue();
            }
        }
        return null;
    }
}
