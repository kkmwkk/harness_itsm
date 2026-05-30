package com.nkia.itg.system.notification.dto;

import com.nkia.itg.system.notification.domain.NotificationType;
import com.nkia.itg.system.notification.entity.Notification;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "알림 1건")
public record NotificationResponse(
        @Schema(description = "알림 ID", example = "1001") Long id,
        @Schema(description = "유형", example = "WORKFLOW_STEP_ASSIGNED") NotificationType type,
        @Schema(description = "제목", example = "새 워크플로우 단계가 배정되었습니다") String title,
        @Schema(description = "본문(plain text)", example = "샘플-티켓 ITSM-00042 의 '1차 검토' 단계") String body,
        @Schema(description = "이동 경로", example = "/itsm/42") String relatedUrl,
        @Schema(description = "읽음 여부", example = "false") boolean read,
        @Schema(description = "읽은 시각") LocalDateTime readAt,
        @Schema(description = "생성 시각") LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getRelatedUrl(),
                n.isRead(),
                n.getReadAt(),
                n.getCreatedAt());
    }
}
