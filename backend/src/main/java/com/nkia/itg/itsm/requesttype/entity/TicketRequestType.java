package com.nkia.itg.itsm.requesttype.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 요청 유형 (장애·서비스요청·변경·문제·QnA). code 가 PK.
 * <ul>
 *   <li>formMetaGroupId — 요청 유형별 폼 메타 분기 키 (예: 'itg-ticket-incident').</li>
 *   <li>defaultWorkflowCode — 티켓 생성 시 시작할 워크플로우 정의 (soft FK).</li>
 * </ul>
 */
@Entity
@Table(name = "ticket_request_type")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TicketRequestType {

    @Id
    @Column(name = "code", length = 40, nullable = false, updatable = false)
    private String code;

    @Column(name = "label", length = 80, nullable = false)
    private String label;

    @Column(name = "form_meta_group_id", length = 100)
    private String formMetaGroupId;

    @Column(name = "default_workflow_code", length = 40)
    private String defaultWorkflowCode;

    @Column(name = "sla_minutes_default")
    private Integer slaMinutesDefault;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 라벨·폼 메타·워크플로우·SLA·활성 수정 (code 는 불변). */
    public void update(String label, String formMetaGroupId,
                       String defaultWorkflowCode, Integer slaMinutesDefault, boolean active) {
        this.label = label;
        this.formMetaGroupId = formMetaGroupId;
        this.defaultWorkflowCode = defaultWorkflowCode;
        this.slaMinutesDefault = slaMinutesDefault;
        this.active = active;
    }
}
