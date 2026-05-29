package com.nkia.itg.system.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * 동적 메뉴 트리. parent_id 자기참조.
 * group_id 는 PageMeta group 키와 연결되어 DynamicPage 진입 (page_meta.id 가 아니므로 비-FK).
 * permission_code 는 NULL(누구나 노출) 또는 permission.code 매칭.
 */
@Entity
@Table(name = "menu")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", length = 60, nullable = false)
    private String code;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "label", length = 100, nullable = false)
    private String label;

    /** lucide 아이콘명 (예: 'BoxesIcon'). */
    @Column(name = "icon", length = 60)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "route", length = 200)
    private String route;

    /** PageMeta group 키 (비-FK, NULL 허용). */
    @Column(name = "group_id", length = 100)
    private String groupId;

    /** 권한 코드 (NULL = 누구나, 값 있으면 그 권한 필요). */
    @Column(name = "permission_code", length = 60)
    private String permissionCode;

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

    /** 메뉴 표시 속성 수정 (코드·트리 위치는 불변 — 이동은 moveTo). */
    public void update(String label, String icon, String route, String groupId, String permissionCode) {
        this.label = label;
        this.icon = icon;
        this.route = route;
        this.groupId = groupId;
        this.permissionCode = permissionCode;
    }

    /** 권한 코드 설정 (NULL 허용 — 누구나 노출로 전환). */
    public void setPermission(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    /** 트리 위치 이동 (단일 행만 — 트리 정합성은 Service). */
    public void moveTo(Long newParentId, int newSortOrder) {
        this.parentId = newParentId;
        this.sortOrder = newSortOrder;
    }

    public void deactivate() {
        this.active = false;
    }
}
