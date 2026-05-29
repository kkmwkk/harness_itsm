package com.nkia.itg.auth.controller;

import com.nkia.itg.auth.dto.LoginRequest;
import com.nkia.itg.auth.dto.MeResponse;
import com.nkia.itg.auth.dto.RefreshRequest;
import com.nkia.itg.auth.dto.TokenResponse;
import com.nkia.itg.auth.service.AuthService;
import com.nkia.itg.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth — 인증",
        description = "JWT 자체 인증. 로그인으로 access/refresh 토큰을 발급하고, refresh 로 access 를 재발급한다.")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "로그인",
            description = "사용자명/비밀번호로 access(15분)·refresh(7일) 토큰을 발급한다. "
                    + "실패 사유는 보안상 구분하지 않는다 (LOGIN_FAILED 통합 응답)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(req), "로그인되었습니다."));
    }

    @Operation(
            summary = "토큰 재발급",
            description = "refresh 토큰으로 새 access·refresh 토큰을 발급한다. access 토큰은 거부된다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 refresh 토큰")
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(req)));
    }

    @Operation(
            summary = "현재 사용자 정보",
            description = "access 토큰으로 인증된 현재 사용자의 정보·역할·권한을 반환한다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(authentication.getName())));
    }

    @Operation(
            summary = "로그아웃",
            description = "stateless JWT 이므로 서버는 토큰을 무효화하지 않는다. "
                    + "클라이언트가 보관 중인 access·refresh 토큰을 폐기하면 된다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 처리")
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(ApiResponse.ok(null, "로그아웃되었습니다."));
    }
}
