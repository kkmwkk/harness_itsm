package com.nkia.itg.system.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nkia.itg.system.user.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserAccount 도메인 메서드 단위 테스트 (상태 전이)")
class UserAccountTest {

    private UserAccount userWith(UserStatus status) {
        return UserAccount.builder()
                .username("sample-user")
                .passwordHash("$2a$10$samplehashsamplehashsamplehashsamplehashsamplehashsamp")
                .name("샘플 사용자")
                .status(status)
                .build();
    }

    @Test
    @DisplayName("lock — ACTIVE 에서 LOCKED 허용")
    void lock_ACTIVE_에서_LOCKED_허용() {
        UserAccount user = userWith(UserStatus.ACTIVE);

        user.lock();

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    @DisplayName("lock — LOCKED 재호출 시 idempotent (no-op)")
    void lock_LOCKED_재호출_시_idempotent() {
        UserAccount user = userWith(UserStatus.LOCKED);

        user.lock();

        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    @Test
    @DisplayName("lock — RETIRED 에서 거부")
    void lock_RETIRED_에서_거부() {
        UserAccount user = userWith(UserStatus.RETIRED);

        assertThatThrownBy(user::lock)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETIRED 사용자는 잠글 수 없습니다");
    }

    @Test
    @DisplayName("unlock — LOCKED 에서 ACTIVE 허용")
    void unlock_LOCKED_에서_ACTIVE_허용() {
        UserAccount user = userWith(UserStatus.LOCKED);

        user.unlock();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("retire — 어느 상태든 RETIRED 로 전이")
    void retire_어느_상태든_RETIRED_로_전이() {
        for (UserStatus status : UserStatus.values()) {
            UserAccount user = userWith(status);

            user.retire();

            assertThat(user.getStatus()).isEqualTo(UserStatus.RETIRED);
        }
    }

    @Test
    @DisplayName("retire — RETIRED 재호출 시 idempotent")
    void retire_RETIRED_재호출_시_idempotent() {
        UserAccount user = userWith(UserStatus.RETIRED);

        user.retire();

        assertThat(user.getStatus()).isEqualTo(UserStatus.RETIRED);
    }

    @Test
    @DisplayName("changePassword — RETIRED 거부")
    void changePassword_RETIRED_거부() {
        UserAccount user = userWith(UserStatus.RETIRED);

        assertThatThrownBy(() -> user.changePassword("$2a$10$newhash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETIRED 사용자는 비밀번호를 변경할 수 없습니다");
    }

    @Test
    @DisplayName("changePassword — 정상 시 passwordChangedAt set")
    void changePassword_정상_시_passwordChangedAt_set() {
        UserAccount user = userWith(UserStatus.ACTIVE);

        user.changePassword("$2a$10$newhashnewhashnewhashnewhashnewhashnewhashnewhashne");

        assertThat(user.getPasswordHash())
                .isEqualTo("$2a$10$newhashnewhashnewhashnewhashnewhashnewhashnewhashne");
        assertThat(user.getPasswordChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("touchLastLogin — lastLoginAt set")
    void touchLastLogin_lastLoginAt_set() {
        UserAccount user = userWith(UserStatus.ACTIVE);

        user.touchLastLogin();

        assertThat(user.getLastLoginAt()).isNotNull();
    }
}
