package com.nkia.itg.meta.entity;

import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.domain.PackageType;
import com.nkia.itg.meta.domain.SystemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "page_meta")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PageMeta {

    @Id
    @Column(name = "id", length = 100, nullable = false, updatable = false)
    private String id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_type", length = 20, nullable = false)
    private SystemType systemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", length = 10, nullable = false)
    private PackageType packageType;

    @Column(name = "group_id", length = 100, nullable = false)
    private String groupId;

    @Column(name = "major_version", nullable = false)
    private int majorVersion;

    @Column(name = "minor_version", nullable = false)
    private int minorVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "meta_status", length = 20, nullable = false)
    @Builder.Default
    private MetaStatus metaStatus = MetaStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metaJson;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = true, updatable = false)
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
        if (this.metaStatus == null) {
            this.metaStatus = MetaStatus.DRAFT;
        }
    }

    public void publish() {
        if (this.metaStatus != MetaStatus.DRAFT) {
            throw new IllegalStateException("DRAFT 상태만 배포할 수 있습니다. 현재: " + this.metaStatus);
        }
        this.metaStatus = MetaStatus.PUBLISHED;
    }

    public void archive() {
        this.metaStatus = MetaStatus.ARCHIVED;
    }

    /**
     * metaJson 본문을 교체한다. DRAFT 상태에서만 허용한다(ADR-006).
     *
     * <p>PUBLISHED·DEPRECATED·ARCHIVED 메타의 본문을 직접 편집하면 화면 노출 중인 메타가
     * 검토 없이 바뀌므로 거부한다. 편집이 필요하면 새 DRAFT 로 복사 후 편집한다.
     */
    public void replaceBody(Map<String, Object> body) {
        if (this.metaStatus != MetaStatus.DRAFT) {
            throw new IllegalStateException(
                    "DRAFT 상태만 본문을 편집할 수 있습니다. 현재: " + this.metaStatus);
        }
        this.metaJson = body != null ? new HashMap<>(body) : new HashMap<>();
    }

    public PageMeta copyAs(String newId, int newMinorVersion) {
        if (newMinorVersion < 1) {
            throw new IllegalArgumentException("minorVersion 은 1 이상이어야 합니다.");
        }
        Map<String, Object> copiedMetaJson = new HashMap<>(this.metaJson != null ? this.metaJson : Map.of());
        return PageMeta.builder()
                .id(newId)
                .title(this.title)
                .systemType(this.systemType)
                .packageType(this.packageType)
                .groupId(this.groupId)
                .majorVersion(this.majorVersion)
                .minorVersion(newMinorVersion)
                .metaStatus(MetaStatus.DRAFT)
                .metaJson(copiedMetaJson)
                .active(true)
                .build();
    }

    public String versionLabel() {
        return "v" + this.majorVersion + "." + this.minorVersion;
    }
}
