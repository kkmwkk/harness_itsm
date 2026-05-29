package com.nkia.itg.itam.category.entity;

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
 * 자산 분류 트리 (code 식별, parent_code 자기참조 + path('/HW/HW_LAPTOP/')). Department 패턴 재사용 —
 * path 자동 계산과 트리 정합성은 Service 책임 (DB 트리거 의존 없음). code 가 PK 이므로 생성 시점에
 * 바로 path 계산이 가능하다.
 * <p>{@link #moveTo(AssetCategory)} 는 자기 자신·자손으로의 이동을 거부해 순환 트리를 원천 차단한다.
 */
@Entity
@Table(name = "asset_category")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AssetCategory {

    @Id
    @Column(name = "code", length = 40, nullable = false, updatable = false)
    private String code;

    @Column(name = "label", length = 80, nullable = false)
    private String label;

    @Column(name = "parent_code", length = 40)
    private String parentCode;

    @Column(name = "path", length = 255, nullable = false)
    private String path;

    @Column(name = "form_meta_group_id", length = 100)
    private String formMetaGroupId;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

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

    /** 라벨·폼 메타 그룹·정렬 순서 수정 (code 는 불변). */
    public void update(String label, String formMetaGroupId, int sortOrder) {
        this.label = label;
        this.formMetaGroupId = formMetaGroupId;
        this.sortOrder = sortOrder;
    }

    /**
     * 트리 path 갱신 — 생성 직후, move 시 자손 재계산에 쓴다.
     * path 문자열 계산 자체는 Service 가 수행한다 (DB 트리거 금지).
     */
    public void assignPath(String path) {
        this.path = path;
    }

    /**
     * 상위 분류 재지정. 자기 자신·자손으로의 이동은 거부한다(순환 방지). path 재계산은
     * Service 가 assignPath 로 별도 수행한다. newParent 가 null 이면 루트로 이동.
     */
    public void moveTo(AssetCategory newParent) {
        if (newParent != null) {
            if (newParent.code.equals(this.code)) {
                throw new IllegalStateException("분류를 자기 자신으로 이동할 수 없습니다: " + this.code);
            }
            if (this.path != null && newParent.path != null
                    && newParent.path.startsWith(this.path)) {
                throw new IllegalStateException("분류를 자기 자손으로 이동할 수 없습니다: " + this.code);
            }
        }
        this.parentCode = (newParent == null) ? null : newParent.code;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
