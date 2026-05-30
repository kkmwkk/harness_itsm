package com.nkia.itg.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 운영 대시보드 요약 (현재 사용자 기준). 기존 ticket·asset·workflow 데이터를 집계한다.
 * adminStats 는 ROLE_ADMIN 에게만 채워지고 그 외에는 null.
 */
@Schema(description = "대시보드 요약 (현재 사용자 기준 집계)")
public record DashboardSummary(
        @Schema(description = "열린 티켓 수 (CLOSED 제외)", example = "27") long openTickets,
        @Schema(description = "전체 자산 수", example = "127") long totalAssets,
        @Schema(description = "SLA 초과 미완료 단계 수", example = "3") long slaBreachCount,
        @Schema(description = "내게 배정된 열린 티켓 수", example = "8") long myOpenTickets,
        @Schema(description = "우선순위별 티켓 분포") List<CountByKey> ticketsByPriority,
        @Schema(description = "상태별 티켓 분포") List<CountByKey> ticketsByStatus,
        @Schema(description = "분류별 자산 분포") List<CountByKey> assetsByCategory,
        @Schema(description = "최근 8주 자산 라이프사이클 이벤트 추세(스파크라인)") List<Long> lifecycleTrend,
        @Schema(description = "최근 활동 피드") List<RecentActivity> recentActivities,
        @Schema(description = "내 워크플로우 처리 대기 단계") List<MyWorkflowStep> myWorkflowSteps,
        @Schema(description = "관리자 전용 지표 (비관리자는 null)") AdminStats adminStats
) {
}
