package com.nkia.itg.auth.service;

import com.nkia.itg.auth.dto.LoginRequest;
import com.nkia.itg.auth.dto.MeResponse;
import com.nkia.itg.auth.dto.RefreshRequest;
import com.nkia.itg.auth.dto.TokenResponse;
import com.nkia.itg.auth.dto.UserSummary;
import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.system.user.domain.UserStatus;
import com.nkia.itg.system.user.entity.UserAccount;
import com.nkia.itg.system.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 흐름 — 로그인·토큰 재발급·현재 사용자 조회.
 * 로그인 실패 사유(사용자 없음/비밀번호 불일치/비활성)는 모두 동일 메시지·코드로 반환해
 * 사용자 enumeration 공격을 차단한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    /** 보안: 실패 사유를 구분하지 않는 통합 메시지. */
    private static final String LOGIN_FAILED_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public TokenResponse login(LoginRequest req) {
        UserAccount user = userRepository.findByUsername(req.username())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(this::loginFailed);

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw loginFailed();
        }

        user.touchLastLogin();
        userRepository.save(user);

        return toTokenResponse(user);
    }

    public TokenResponse refresh(RefreshRequest req) {
        Claims claims = jwtService.parse(req.refreshToken()).getPayload();
        if (!jwtService.isRefresh(claims)) {
            throw new ITGException("INVALID_TOKEN", "유효하지 않은 토큰입니다", HttpStatus.UNAUTHORIZED);
        }
        UserAccount user = loadActiveByUsername(claims.getSubject());
        return toTokenResponse(user);
    }

    @Transactional(readOnly = true)
    public MeResponse me(String username) {
        UserAccount user = loadActiveByUsername(username);
        return new MeResponse(
                UserSummary.from(user),
                JwtService.extractRoles(user),
                JwtService.extractPerms(user));
    }

    private TokenResponse toTokenResponse(UserAccount user) {
        return new TokenResponse(
                jwtService.issueAccess(user),
                jwtService.issueRefresh(user),
                jwtService.accessTtlSeconds(),
                UserSummary.from(user),
                JwtService.extractRoles(user),
                JwtService.extractPerms(user));
    }

    private UserAccount loadActiveByUsername(String username) {
        return userRepository.findByUsername(username)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ITGException(
                        "AUTH_REQUIRED", "로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
    }

    private ITGException loginFailed() {
        return new ITGException("LOGIN_FAILED", LOGIN_FAILED_MESSAGE, HttpStatus.UNAUTHORIZED);
    }
}
