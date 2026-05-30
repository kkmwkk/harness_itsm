package com.nkia.itg.system.notification.service;

import com.nkia.itg.system.notification.domain.NotificationType;
import com.nkia.itg.system.notification.dto.NotificationResponse;
import com.nkia.itg.system.notification.entity.Notification;
import com.nkia.itg.system.notification.repository.NotificationRepository;
import com.nkia.itg.system.user.entity.UserAccount;
import com.nkia.itg.system.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 생성·조회·읽음 처리의 단일 진입점 (PRD §6, 금지사항: 알림 자동 생성은 본 서비스로 통일).
 *
 * <p>생성(notify*)은 다른 도메인 서비스(워크플로우·티켓)의 트랜잭션 안에서 호출된다 — 본업과
 * 같은 트랜잭션으로 묶여 일관되게 커밋/롤백된다. 본문(body)은 항상 plain text 로만 작성한다
 * (raw HTML 금지).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /** 단일 사용자에게 알림 생성. userId 가 null 이면 무시(생성하지 않음). */
    public void notifyUser(
            Long userId, NotificationType type, String title, String body, String relatedUrl) {
        if (userId == null) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .relatedUrl(relatedUrl)
                .build());
    }

    /** 특정 역할 코드를 보유한 모든 사용자에게 동일 알림 생성. roleCode 가 비면 무시. */
    public void notifyRole(
            String roleCode, NotificationType type, String title, String body, String relatedUrl) {
        if (roleCode == null || roleCode.isBlank()) {
            return;
        }
        List<UserAccount> recipients = userRepository.findByRoles_Code(roleCode);
        for (UserAccount u : recipients) {
            notifyUser(u.getId(), type, title, body, relatedUrl);
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        return result.map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        if (userId == null) {
            return 0L;
        }
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    /** 본인 알림만 읽음 처리 (소유자 불일치·없음이면 no-op). */
    public void markRead(Long id, Long userId) {
        if (userId == null) {
            return;
        }
        notificationRepository.findByIdAndUserId(id, userId)
                .ifPresent(Notification::markRead);
    }

    /** 본인의 미읽음 알림 전체 읽음 처리. */
    public void markAllRead(Long userId) {
        if (userId == null) {
            return;
        }
        for (Notification n : notificationRepository.findByUserIdAndReadAtIsNull(userId)) {
            n.markRead();
        }
    }
}
