package com.nkia.itg.system.user.entity;

import com.nkia.itg.system.role.entity.Role;
import com.nkia.itg.system.user.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 계정. 'user' 는 SQL 예약어이므로 테이블명은 user_account.
 * 도메인 메서드는 단일 행 상태 변화만 책임진다 (검색·트리·트랜잭션 일관성은 Service).
 */
@Entity
@Table(name = "user_account")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username", length = 60, nullable = false)
    private String username;

    /** BCrypt 해시 (최대 60자) — length 72 로 margin 확보. */
    @Column(name = "password_hash", length = 72, nullable = false)
    private String passwordHash;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    /** 부서 FK — NULL 허용 (부서 미배정 사용자 가능). 양방향 회피로 Long 만 보유. */
    @Column(name = "department_id")
    private Long departmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_role",
        joinColumns        = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

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
            this.status = UserStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** ACTIVE → LOCKED. 이미 LOCKED 면 idempotent no-op. RETIRED 면 거부. */
    public void lock() {
        if (this.status == UserStatus.RETIRED) {
            throw new IllegalStateException("RETIRED 사용자는 잠글 수 없습니다.");
        }
        this.status = UserStatus.LOCKED;
    }

    /** LOCKED → ACTIVE. 이미 ACTIVE 면 no-op. RETIRED 면 거부. */
    public void unlock() {
        if (this.status == UserStatus.RETIRED) {
            throw new IllegalStateException("RETIRED 사용자는 잠금 해제할 수 없습니다.");
        }
        this.status = UserStatus.ACTIVE;
    }

    /** 어느 상태든 RETIRED 로 전이. 이미 RETIRED 면 idempotent no-op. */
    public void retire() {
        this.status = UserStatus.RETIRED;
    }

    /** 비밀번호 해시 교체 + passwordChangedAt 갱신. RETIRED 면 거부. */
    public void changePassword(String newPasswordHash) {
        if (this.status == UserStatus.RETIRED) {
            throw new IllegalStateException("RETIRED 사용자는 비밀번호를 변경할 수 없습니다.");
        }
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = LocalDateTime.now();
    }

    /** 로그인 시각 갱신. */
    public void touchLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    /** 역할 부여 (Set 특성으로 중복 무시). */
    public void assignRole(Role role) {
        this.roles.add(role);
    }

    /** 역할 회수. */
    public void removeRole(Role role) {
        this.roles.remove(role);
    }
}
