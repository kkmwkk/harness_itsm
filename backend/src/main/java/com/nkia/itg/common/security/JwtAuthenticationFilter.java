package com.nkia.itg.common.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationFilter implements Filter {

    // TODO(phase-2): Authorization 헤더에서 Bearer 토큰을 추출하고 서명/만료를 검증한 뒤
    //                SecurityContextHolder 에 Authentication 을 주입한다. 이번 phase 는 패스스루.
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(req, res);
    }
}
