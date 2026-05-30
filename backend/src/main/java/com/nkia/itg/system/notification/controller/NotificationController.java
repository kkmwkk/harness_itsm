package com.nkia.itg.system.notification.controller;

import com.nkia.itg.common.response.ApiResponse;
import com.nkia.itg.common.response.PageResponse;
import com.nkia.itg.system.notification.dto.NotificationResponse;
import com.nkia.itg.system.notification.service.NotificationService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification — 알림 센터",
        description = "현재 로그인 사용자의 알림 목록·미읽음 개수·읽음 처리. 알림은 워크플로우 단계 진입·"
                + "티켓 상태 변경 등에서 NotificationService 가 자동 생성한다. 실시간 push 없이 폴링으로 조회.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록 (페이지)",
            description = "현재 사용자의 알림을 최신순으로 페이지 반환한다. unreadOnly=true 면 미읽음만.")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @Parameter(description = "미읽음만 조회", example = "false")
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = currentUserId(authentication);
        Page<NotificationResponse> result = notificationService.list(userId, unreadOnly, page, size);
        return ApiResponse.ok(PageResponse.from(result));
    }

    @Operation(summary = "내 미읽음 알림 개수",
            description = "종 아이콘 뱃지용. 현재 사용자의 미읽음 알림 수를 반환한다.")
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(Authentication authentication) {
        return ApiResponse.ok(notificationService.unreadCount(currentUserId(authentication)));
    }

    @Operation(summary = "알림 1건 읽음 처리",
            description = "본인 알림만 읽음 처리한다(소유자 불일치·없음이면 무시).")
    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(
            @Parameter(description = "알림 ID", example = "1001") @PathVariable Long id,
            Authentication authentication) {
        notificationService.markRead(id, currentUserId(authentication));
        return ApiResponse.ok(null, "읽음 처리되었습니다.");
    }

    @Operation(summary = "내 알림 전체 읽음 처리",
            description = "현재 사용자의 미읽음 알림을 모두 읽음 처리한다.")
    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead(Authentication authentication) {
        notificationService.markAllRead(currentUserId(authentication));
        return ApiResponse.ok(null, "모두 읽음 처리되었습니다.");
    }

    /** JWT uid 클레임에서 현재 사용자 ID 추출 (WorkflowInstanceController 와 동일 패턴). */
    private Long currentUserId(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof Claims claims) {
            Object uid = claims.get("uid");
            if (uid instanceof Number number) {
                return number.longValue();
            }
        }
        return null;
    }
}
