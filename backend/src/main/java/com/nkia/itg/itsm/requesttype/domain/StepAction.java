package com.nkia.itg.itsm.requesttype.domain;

/**
 * 워크플로우 단계 액션 (ARCHITECTURE §15-2 전이 매트릭스).
 * <ul>
 *   <li>APPROVE / FORWARD / COMPLETE — 다음 단계로 전이 (마지막 단계면 종결).</li>
 *   <li>REJECT — 인스턴스 REJECTED 종결.</li>
 *   <li>CONFIRM — 인스턴스 COMPLETED 종결 + Ticket CLOSED.</li>
 *   <li>REOPEN — 첫 단계로 재오픈.</li>
 * </ul>
 */
public enum StepAction {
    APPROVE,
    REJECT,
    FORWARD,
    COMPLETE,
    CONFIRM,
    REOPEN
}
