package com.nkia.itg.auth.service;

import com.nkia.itg.common.exception.ITGException;
import com.nkia.itg.system.permission.entity.Permission;
import com.nkia.itg.system.role.entity.Role;
import com.nkia.itg.system.user.entity.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * JWT 발급·검증 (HS256). secret·TTL 은 app.jwt.* 설정에서 외부화한다 (코드 하드코딩 금지).
 * access 토큰에는 roles·perms 를 함께 실어 매 요청 DB 조회를 줄인다 (크기 vs 조회 trade-off).
 */
@Service
public class JwtService {

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMS = "perms";
    private static final String CLAIM_TYP = "typ";
    private static final String TYP_ACCESS = "access";
    private static final String TYP_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-ttl-minutes}") long accessTtlMinutes,
            @Value("${app.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
    }

    /** access 토큰 발급 — sub/uid/roles/perms/typ=access. */
    public String issueAccess(UserAccount user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(CLAIM_UID, user.getId())
                .claim(CLAIM_ROLES, extractRoles(user))
                .claim(CLAIM_PERMS, extractPerms(user))
                .claim(CLAIM_TYP, TYP_ACCESS)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTtl.toMillis()))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** refresh 토큰 발급 — sub/uid/typ=refresh (권한 claim 불포함, 최소화). */
    public String issueRefresh(UserAccount user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(CLAIM_UID, user.getId())
                .claim(CLAIM_TYP, TYP_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTtl.toMillis()))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 서명·만료 검증. 실패(변조·만료·형식 오류) 시 INVALID_TOKEN(401). */
    public Jws<Claims> parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ITGException("INVALID_TOKEN", "유효하지 않은 토큰입니다", HttpStatus.UNAUTHORIZED);
        }
    }

    public boolean isAccess(Claims claims) {
        return TYP_ACCESS.equals(claims.get(CLAIM_TYP, String.class));
    }

    public boolean isRefresh(Claims claims) {
        return TYP_REFRESH.equals(claims.get(CLAIM_TYP, String.class));
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    /** 사용자의 역할 코드 목록 (정렬·중복 제거 없이 안정 출력). 호출 시 트랜잭션 내에서 LAZY 로드. */
    public static List<String> extractRoles(UserAccount user) {
        return user.getRoles().stream()
                .map(Role::getCode)
                .sorted()
                .toList();
    }

    /** 사용자의 권한 코드 목록 (역할이 보유한 권한을 평탄화). */
    public static List<String> extractPerms(UserAccount user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .sorted()
                .toList();
    }
}
