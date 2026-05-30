package com.nkia.itg.system.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nkia.itg.system.notification.domain.NotificationType;
import com.nkia.itg.system.notification.entity.Notification;
import com.nkia.itg.system.notification.repository.NotificationRepository;
import com.nkia.itg.system.user.entity.UserAccount;
import com.nkia.itg.system.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 단위 테스트 (Mockito)")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;

    private NotificationService service() {
        return new NotificationService(notificationRepository, userRepository);
    }

    private Notification notification(Long id, Long userId, LocalDateTime readAt) {
        return Notification.builder()
                .id(id)
                .userId(userId)
                .type(NotificationType.TICKET_STATUS_CHANGED)
                .title("제목")
                .body("본문")
                .relatedUrl("/itsm/1")
                .readAt(readAt)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private UserAccount user(Long id) {
        return UserAccount.builder().id(id).username("u" + id).passwordHash("h").build();
    }

    @Test
    @DisplayName("notifyUser — userId 가 있으면 알림 1건 저장")
    void notifyUser_saves() {
        service().notifyUser(7L, NotificationType.TICKET_STATUS_CHANGED, "t", "b", "/itsm/1");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getTitle()).isEqualTo("t");
    }

    @Test
    @DisplayName("notifyUser — userId 가 null 이면 저장하지 않는다")
    void notifyUser_nullUser_skips() {
        service().notifyUser(null, NotificationType.TICKET_STATUS_CHANGED, "t", "b", null);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("notifyRole — 역할 보유 사용자 수만큼 알림 저장")
    void notifyRole_savesPerUser() {
        given(userRepository.findByRoles_Code("ROLE_IT_SUPPORT"))
                .willReturn(List.of(user(1L), user(2L), user(3L)));

        service().notifyRole("ROLE_IT_SUPPORT",
                NotificationType.WORKFLOW_STEP_ASSIGNED, "t", "b", "/itsm/9");

        verify(notificationRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("notifyRole — roleCode 가 비면 조회·저장하지 않는다")
    void notifyRole_blank_skips() {
        service().notifyRole("  ", NotificationType.WORKFLOW_STEP_ASSIGNED, "t", "b", null);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("unreadCount — repository 카운트를 그대로 반환")
    void unreadCount_delegates() {
        given(notificationRepository.countByUserIdAndReadAtIsNull(5L)).willReturn(4L);
        assertThat(service().unreadCount(5L)).isEqualTo(4L);
    }

    @Test
    @DisplayName("unreadCount — userId 가 null 이면 0")
    void unreadCount_nullUser_zero() {
        assertThat(service().unreadCount(null)).isZero();
    }

    @Test
    @DisplayName("markRead — 본인 알림이면 읽음 처리")
    void markRead_marks() {
        Notification n = notification(10L, 5L, null);
        given(notificationRepository.findByIdAndUserId(10L, 5L)).willReturn(Optional.of(n));

        service().markRead(10L, 5L);

        assertThat(n.isRead()).isTrue();
    }

    @Test
    @DisplayName("markAllRead — 미읽음 알림 전체를 읽음 처리")
    void markAllRead_marksAll() {
        Notification a = notification(1L, 5L, null);
        Notification b = notification(2L, 5L, null);
        given(notificationRepository.findByUserIdAndReadAtIsNull(5L)).willReturn(List.of(a, b));

        service().markAllRead(5L);

        assertThat(a.isRead()).isTrue();
        assertThat(b.isRead()).isTrue();
    }
}
