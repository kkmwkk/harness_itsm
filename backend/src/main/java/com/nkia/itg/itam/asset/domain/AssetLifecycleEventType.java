package com.nkia.itg.itam.asset.domain;

/**
 * 자산 이력 이벤트 유형 (PRD §4-3 / ARCHITECTURE §14-3).
 * DB CHECK 제약(chk_ale_event)과 동일 값. 코드 안에서 String 하드코딩 금지 — 본 Enum 사용.
 */
public enum AssetLifecycleEventType {
    ACQUIRED,
    TRANSFERRED,
    REPAIRED,
    DISPOSED,
    RENEWED
}
