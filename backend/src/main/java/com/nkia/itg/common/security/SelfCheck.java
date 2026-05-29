package com.nkia.itg.common.security;

import com.nkia.itg.system.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * {@code @PreAuthorize} SpEL 용 본인 확인 빈.
 * 예: {@code @PreAuthorize("hasAuthority('USER_ADMIN') or @selfCheck.isSelf(authentication, #id)")}.
 * 대상 사용자 PK 를 username 으로 환원해 현재 인증 주체와 비교한다 (본인 비밀번호 변경 허용용).
 */
@Component("selfCheck")
@RequiredArgsConstructor
public class SelfCheck {

    private final UserRepository userRepository;

    public boolean isSelf(Authentication authentication, Long targetUserId) {
        if (authentication == null || targetUserId == null || authentication.getName() == null) {
            return false;
        }
        return userRepository.findById(targetUserId)
                .map(u -> u.getUsername().equals(authentication.getName()))
                .orElse(false);
    }
}
