package com.nkia.itg.itam.asset.entity;

import com.nkia.itg.itam.asset.domain.AssetStatus;
import com.nkia.itg.itam.asset.domain.AssetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "asset")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 표시용 자산 번호 (AST-{id5} 패턴). 컬럼은 nullable — IDENTITY 전략은 save() 시점에
     * INSERT 를 즉시 실행하므로, INSERT 시점에는 잠시 null 인 채로 들어갔다가 같은 트랜잭션
     * 안에서 assignAssetNo() 호출 + dirty checking 으로 update 된다. UNIQUE 제약은 NULL
     * 허용. 트랜잭션 외부에서 관찰될 때는 항상 채워져 있다 (ticket 패턴 동일).
     */
    @Column(name = "asset_no", length = 32)
    private String assetNo;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", length = 20, nullable = false)
    private AssetType assetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private AssetStatus status = AssetStatus.ACTIVE;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "serial_no", length = 60)
    private String serialNo;

    @Column(name = "category", length = 40)
    private String category;

    /** 자산 분류 코드 (FK → asset_category.code). 분류별 원장 메타 분기 키 (PRD §4-3). */
    @Column(name = "category_code", length = 40)
    private String categoryCode;

    @Column(name = "assignee_id", length = 60)
    private String assigneeId;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "acquired_at")
    private LocalDate acquiredAt;

    @Column(name = "disposed_at")
    private LocalDate disposedAt;

    /**
     * 자산 등록 시점의 메타 ID 보존 (PRD §5-2 활용 사례). NOT NULL · FK → page_meta(id).
     * 등록에 사용한 메타가 이후 DEPRECATED·ARCHIVED 되어도 이 값으로 당시 화면 메타를 복원한다.
     */
    @Column(name = "page_meta_id_at_registration", length = 100, nullable = false)
    private String pageMetaIdAtRegistration;

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
        if (this.status == null) {
            this.status = AssetStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 자산번호 한 번만 부여 (이미 set 되어 있으면 IllegalStateException). */
    public void assignAssetNo(String assetNo) {
        if (this.assetNo != null && !this.assetNo.isBlank()) {
            throw new IllegalStateException("asset_no 는 이미 부여되었습니다: " + this.assetNo);
        }
        this.assetNo = assetNo;
    }

    /**
     * 상태 전이. 허용 매트릭스:
     *   ACTIVE   → STORAGE / RETIRED / REPLACED
     *   STORAGE  → ACTIVE / RETIRED
     *   REPLACED → RETIRED
     *   RETIRED  → 전이 불가 (도메인 예외)
     *
     * RETIRED 전이 시 disposedAt = LocalDate.now() (이미 set 이면 보존).
     */
    public void changeStatus(AssetStatus next) {
        if (this.status == AssetStatus.RETIRED) {
            throw new IllegalStateException(
                "RETIRED 자산은 상태를 변경할 수 없습니다. (자산: " + this.assetNo + ")");
        }
        if (this.status == next) {
            return;  // no-op
        }
        final boolean allowed = switch (this.status) {
            case ACTIVE   -> EnumSet.of(AssetStatus.STORAGE, AssetStatus.RETIRED, AssetStatus.REPLACED).contains(next);
            case STORAGE  -> EnumSet.of(AssetStatus.ACTIVE,  AssetStatus.RETIRED).contains(next);
            case REPLACED -> EnumSet.of(AssetStatus.RETIRED).contains(next);
            case RETIRED  -> false;
        };
        if (!allowed) {
            throw new IllegalStateException(
                "허용되지 않은 상태 전이: " + this.status + " → " + next);
        }
        this.status = next;
        if (next == AssetStatus.RETIRED && this.disposedAt == null) {
            this.disposedAt = LocalDate.now();
        }
    }

    public void assign(String assigneeId) {
        if (this.status == AssetStatus.RETIRED) {
            throw new IllegalStateException("RETIRED 자산은 담당자 할당 불가.");
        }
        this.assigneeId = assigneeId;
    }

    public void updateAttributes(String name, String model, String serialNo,
                                 String category, String location) {
        if (this.status == AssetStatus.RETIRED) {
            throw new IllegalStateException("RETIRED 자산은 속성 변경 불가.");
        }
        if (name      != null) this.name      = name;
        if (model     != null) this.model     = model;
        if (serialNo  != null) this.serialNo  = serialNo;
        if (category  != null) this.category  = category;
        if (location  != null) this.location  = location;
    }
}
