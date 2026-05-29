package com.nkia.itg.itsm.workflow.domain;

/**
 * 워크플로우 인스턴스 상태 (자체 단계 엔진 MVP — ADR-015).
 * RUNNING 만 액션 허용. COMPLETED/CANCELED/REJECTED 는 종결 상태로 액션 거부.
 */
public enum WorkflowStatus {
    RUNNING,
    COMPLETED,
    CANCELED,
    REJECTED
}
