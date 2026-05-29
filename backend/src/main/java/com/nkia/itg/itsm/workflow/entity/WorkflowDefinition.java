package com.nkia.itg.itsm.workflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 워크플로우 정의 (자체 단계 엔진 MVP — ADR-015). code 가 PK.
 * steps 는 JSONB 단계 배열 (ARCHITECTURE §15-1). PageMeta 의 JSONB 매핑 패턴 재사용 —
 * 단계 정의를 별도 normalize 테이블로 만들지 않는다.
 * <pre>
 * [{ "index":0, "name":"접수", "assignee_role_code":"ROLE_IT_SUPPORT",
 *    "sla_minutes":60, "allowed_actions":["FORWARD","COMPLETE"] }, ...]
 * </pre>
 */
@Entity
@Table(name = "workflow_definition")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowDefinition {

    @Id
    @Column(name = "code", length = 40, nullable = false, updatable = false)
    private String code;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private int version = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "steps", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> steps;

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

    /** 단계 개수. */
    public int stepCount() {
        return steps == null ? 0 : steps.size();
    }

    /** 0-based stepIndex 의 단계 정의 (없으면 null). */
    public Map<String, Object> stepAt(int stepIndex) {
        if (steps == null || stepIndex < 0 || stepIndex >= steps.size()) {
            return null;
        }
        return steps.get(stepIndex);
    }

    /** stepIndex 가 마지막 단계인지. */
    public boolean isLastStep(int stepIndex) {
        return stepCount() > 0 && stepIndex == stepCount() - 1;
    }

    /** 정의·단계 수정 (code 는 불변). steps null 이면 빈 배열로 보존. */
    public void update(String name, int version, List<Map<String, Object>> steps, boolean active) {
        this.name = name;
        this.version = version;
        this.steps = steps != null ? steps : new ArrayList<>();
        this.active = active;
    }
}
