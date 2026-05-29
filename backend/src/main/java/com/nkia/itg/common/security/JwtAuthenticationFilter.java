package com.nkia.itg.common.security;

import com.nkia.itg.auth.service.JwtService;
import com.nkia.itg.common.exception.ITGException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 매 요청마다 Authorization: Bearer 토큰을 검증하고 SecurityContext 에 Authentication 을 주입한다.
 * 토큰이 없거나 invalid 면 익명으로 그대로 진행하고, 보호 경로 차단은 SecurityConfig 가 담당한다
 * (여기서 직접 401 을 던지지 않는다 — entry point 일원화).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String token = extractBearer(req);
        if (token != null) {
            try {
                Claims claims = jwtService.parse(token).getPayload();
                if (jwtService.isAccess(claims)) {
                    List<?> rawRoles = claims.get("roles", List.class);
                    List<GrantedAuthority> authorities = (rawRoles == null ? List.of() : rawRoles).stream()
                            .map(String::valueOf)
                            .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r))
                            .toList();
                    var auth = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(), null, authorities);
                    auth.setDetails(claims);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (ITGException ignore) {
                // invalid 토큰 → 익명으로 진행. 보호 경로는 SecurityConfig 가 차단한다.
            }
        }
        chain.doFilter(req, res);
    }

    private String extractBearer(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        return (h != null && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }
}
