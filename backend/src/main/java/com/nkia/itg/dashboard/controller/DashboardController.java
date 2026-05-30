package com.nkia.itg.dashboard.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.dashboard.dto.DashboardSummary;
import com.nkia.itg.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard — 운영 대시보드",
        description = "현재 로그인 사용자 기준으로 티켓·자산·워크플로우 데이터를 집계한 요약 API.")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(
            summary = "대시보드 요약 조회",
            description = "열린 티켓·SLA 임박·내 작업·전체 자산 KPI, 우선순위/상태/분류 분포, 최근 활동, "
                    + "내 워크플로우 단계를 반환한다. 결과는 인증된 현재 사용자 기준이며, ROLE_ADMIN 은 "
                    + "사용자/메뉴/메타 그룹 수 추가 지표(adminStats)를 함께 받는다. 인증 필수."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummary>> summary(Authentication authentication) {
        String username = authentication == null ? null : authentication.getName();
        Set<String> roles = authentication == null
                ? Set.of()
                : authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.summary(username, roles)));
    }
}
