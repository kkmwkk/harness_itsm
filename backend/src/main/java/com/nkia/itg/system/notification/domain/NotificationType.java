package com.nkia.itg.system.notification.domain;

/**
 * 알림 유형. 자동 생성 트리거별로 구분한다 (PRD §6 알림 컴포넌트).
 * 새 트리거 추가 시 본 enum 에만 값을 더한다 — 알림 생성은 NotificationService 로 통일한다.
 */
public enum NotificationType {
    /** 워크플로우 단계 진입 시 해당 단계 담당 역할 사용자에게. */
    WORKFLOW_STEP_ASSIGNED,
    /** 본인 티켓 상태 변경 시 요청자에게. */
    TICKET_STATUS_CHANGED
}
