# Step 1: auth-service-jwt

## 읽어야 할 파일

- `/CLAUDE.md` v2.1
- `/docs/PRD.md` §5-6 인증·권한·메뉴
- `/docs/ARCHITECTURE.md` §8 인증·인가 (JWT 흐름·SecurityConfig·메뉴 권한 필터)
- `/docs/ADR.md` ADR-008 (JWT 자체 인증)
- `/phases/9-auth-and-users/step0.md` — Entity 모델
- `/backend/src/main/java/com/nkia/itg/common/security/SecurityConfig.java` (현재 permitAll 상태)
- `/backend/src/main/java/com/nkia/itg/common/security/JwtAuthenticationFilter.java` (현재 stub)
- `/backend/build.gradle` (jjwt 0.12.6 이미 의존성에 포함)

## 작업

이 step 의 목적은 **JWT 발급·검증을 정식 구현하고, AuthService·AuthController·SecurityConfig·JwtAuthenticationFilter 를 동작 가능 상태로 만드는 것**이다.

### 1. `JwtService` — `com.nkia.itg.auth.service.JwtService`

요구사항:
- HS256 (HMAC-SHA256) 서명. secret 은 `application-local.yml` 의 `app.jwt.secret`.
- access token TTL 15분, refresh TTL 7일 — 설정값(`app.jwt.access-ttl-minutes`, `app.jwt.refresh-ttl-days`) 으로 외부화.
- claim:
  - `sub`: username
  - `uid`: user id
  - `roles`: `["ROLE_ADMIN", ...]`
  - `perms`: `["TICKET_CREATE", "META_PUBLISH", ...]` (옵션 — JWT 크기 vs DB 조회 비용 trade-off, 본 phase 는 포함)
  - `typ`: `access` | `refresh`
- 메서드:
  - `String issueAccess(UserAccount user)`
  - `String issueRefresh(UserAccount user)`
  - `Jws<Claims> parse(String token)` — 실패 시 `ITGException("INVALID_TOKEN", ..., HttpStatus.UNAUTHORIZED)`
  - `boolean isAccess(Claims c)`, `isRefresh(Claims c)`

### 2. `AuthService` — `com.nkia.itg.auth.service.AuthService`

- `BCryptPasswordEncoder` 주입 — 비밀번호 검증.
- `UserRepository` (step 0 의 `UserAccount` 용. **본 step 에서 추가 — step 0 의 entity 패키지에 같이 두거나 system/user/repository/UserRepository 로 분리**).

메서드:
- `TokenResponse login(LoginRequest req)`:
  1. `userRepository.findByUsername(req.username())` → 없거나 status≠ACTIVE 면 `ITGException("LOGIN_FAILED", HttpStatus.UNAUTHORIZED)` (사용자 노출 메시지는 "아이디 또는 비밀번호가 올바르지 않습니다" — 보안)
  2. `passwordEncoder.matches(req.password(), user.passwordHash)` → 실패 시 동일 예외.
  3. user.touchLastLogin() + save
  4. `accessToken = jwtService.issueAccess(user)`, `refreshToken = jwtService.issueRefresh(user)`
  5. roles/perms 수집해서 응답.
- `TokenResponse refresh(RefreshRequest req)`:
  1. `jwtService.parse(req.refreshToken())` → typ=refresh 검사 → user 조회 → 새 access token 발급.
- `MeResponse me(String username)`:
  - 현재 인증 사용자 정보·roles·perms 반환.

### 3. DTO Record

```java
public record LoginRequest(
    @Schema(example = "admin") @NotBlank String username,
    @Schema(example = "샘플-비번")  @NotBlank String password
) {}

public record RefreshRequest(
    @NotBlank String refreshToken
) {}

public record TokenResponse(
    String accessToken,
    String refreshToken,
    long   accessExpiresInSec,
    UserSummary user,
    List<String> roles,
    List<String> permissions
) {}

public record UserSummary(
    Long id, String username, String name, String email, String departmentName
) {
    public static UserSummary from(UserAccount u) { ... }
}

public record MeResponse(
    UserSummary user,
    List<String> roles,
    List<String> permissions
) {}
```

### 4. `JwtAuthenticationFilter` (실 구현)

```java
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
                var claims = jwtService.parse(token).getPayload();
                if (jwtService.isAccess(claims)) {
                    var roles = ((List<?>) claims.get("roles", List.class))
                        .stream().map(String::valueOf)
                        .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r))
                        .toList();
                    var auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null, roles);
                    auth.setDetails(claims);                  // perms 등 접근용
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (ITGException ignore) {
                // 토큰 invalid → 익명 사용자로 진행. 보호 경로는 SecurityConfig 가 차단.
            }
        }
        chain.doFilter(req, res);
    }

    private String extractBearer(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        return (h != null && h.startsWith("Bearer ")) ? h.substring(7) : null;
    }
}
```

### 5. `SecurityConfig` 정식 강화

```java
http
    .cors(...)
    .csrf(disable)
    .sessionManagement(STATELESS)
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
        .requestMatchers("/actuator/health").permitAll()
        .anyRequest().authenticated()
    )
    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
    .exceptionHandling(eh -> eh
        .authenticationEntryPoint((req, res, ex) -> {
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("""
                {"success":false,"data":null,"message":"로그인이 필요합니다","errorCode":"AUTH_REQUIRED"}""");
        })
        .accessDeniedHandler((req, res, ex) -> {
            res.setStatus(HttpStatus.FORBIDDEN.value());
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("""
                {"success":false,"data":null,"message":"권한이 부족합니다","errorCode":"FORBIDDEN"}""");
        })
    );
```

> v2.0 의 `/api/meta/**`·`/api/tickets/**`·`/api/assets/**` 의 permitAll 은 **이 step 에서 제거** — 모든 보호 경로가 인증 요구. 다만 backwards compat 위해 일부 GET 은 임시 허용도 검토 가능 (본 step 의 e2e 시나리오에서 토큰 발급 후 호출).

### 6. `application(-local).yml` 추가

```yaml
app:
  jwt:
    # 운영에서는 환경변수·시크릿 매니저에서. 로컬은 32+ 자 fixed for testing.
    secret:               "0123456789-itg-v2-jwt-secret-please-replace-in-prod"
    access-ttl-minutes:   15
    refresh-ttl-days:     7
```

### 7. 단위 테스트

#### `JwtServiceTest`
- `issueAccess_parse_왕복_subject_uid_roles_복원`.
- `issueAccess_typ_access`.
- `issueRefresh_typ_refresh`.
- `parse_변조된_토큰_INVALID_TOKEN_예외`.
- `parse_만료_토큰_예외` — 시계 클럭 mock 또는 ttl=음수 fixture.
- `isAccess_isRefresh_분기`.

#### `AuthServiceTest` (Mockito)
- `login_성공_시_TokenResponse_반환_lastLoginAt_갱신`.
- `login_사용자_없음_LOGIN_FAILED_401_+_메시지_보안`.
- `login_비밀번호_불일치_LOGIN_FAILED_401`.
- `login_LOCKED_사용자_거부`.
- `login_RETIRED_사용자_거부`.
- `refresh_정상_새_access_발급`.
- `refresh_access_토큰_거부_INVALID_TOKEN`.
- `me_username_으로_사용자_조회_roles_perms_포함`.

#### `JwtAuthenticationFilterTest`
- `유효_Bearer_토큰_시_SecurityContext_에_Authentication_설정`.
- `Bearer_없으면_익명_그대로_진행`.
- `만료/변조_토큰_시_익명_그대로_진행`.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend

# 1) 파일
test -f src/main/java/com/nkia/itg/auth/service/JwtService.java
test -f src/main/java/com/nkia/itg/auth/service/AuthService.java
test -f src/main/java/com/nkia/itg/auth/dto/LoginRequest.java
test -f src/main/java/com/nkia/itg/auth/dto/TokenResponse.java
test -f src/main/java/com/nkia/itg/system/user/repository/UserRepository.java

# 2) SecurityConfig 가 더 이상 모든 /api/** permitAll 아님
! grep -q '"/api/meta/\*\*"\\.permitAll' src/main/java/com/nkia/itg/common/security/SecurityConfig.java || \
  grep -q '/api/auth/login' src/main/java/com/nkia/itg/common/security/SecurityConfig.java

# 3) JwtAuthenticationFilter 가 stub 이 아님
! grep -q "TODO(phase-2-auth)" src/main/java/com/nkia/itg/common/security/JwtAuthenticationFilter.java

# 4) application(-local).yml 에 jwt secret
grep -q "app:" src/main/resources/application.yml || grep -q "app:" src/main/resources/application-local.yml
grep -q "jwt:" src/main/resources/application-local.yml

# 5) 단위 테스트
./gradlew test --tests "com.nkia.itg.auth.*"
./gradlew test --tests "com.nkia.itg.common.security.*"
./gradlew test       # 전체 회귀 — 단, 기존 ticket/asset/meta 테스트는 SecurityConfig 변경 영향 받을 수 있음
./gradlew build
```

> 전체 회귀 시 기존 `@WebMvcTest` 들이 SecurityConfig 변경으로 깨질 수 있다. 그 경우 `@AutoConfigureMockMvc(addFilters = false)` 또는 `@WithMockUser` 로 보정. 본 step 에서 회귀 보정 포함.

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - 토큰에 password 포함 안 됨?
   - secret 이 환경변수 또는 yml 외부화? 코드에 하드코딩 없음?
   - login 실패 시 "사용자 없음" 과 "비밀번호 틀림" 이 같은 메시지/코드 반환 (사용자 enumeration 방지)?
   - JwtAuthenticationFilter 가 OncePerRequestFilter 상속·예외를 삼키지 않고 익명으로 진행?
   - `/api/auth/login`·`/api/auth/refresh` 외 모든 `/api/**` 가 authenticated()?
3. step 1 업데이트:
   - 성공 → `"summary": "JwtService(HS256·access 15m/refresh 7d·sub/uid/roles/perms claim) + AuthService(login/refresh/me + BCrypt 비밀번호 검증 + lastLoginAt + LOGIN_FAILED 통합 메시지) + JwtAuthenticationFilter 실 검증 + SecurityConfig 정식(/auth/login/refresh·swagger·actuator 만 permitAll, 나머지 authenticated, 401/403 한글 JSON 응답) + DTO Record + application.yml app.jwt.* + 단위 테스트(JwtService 6 + AuthService 8 + Filter 3)."`

## 금지사항

- JWT secret 을 build.gradle / 소스에 하드코딩 금지. application(-local).yml 또는 환경변수.
- HS512·RS256 등으로 변경 금지 (본 step 은 HS256 단일).
- 로그인 실패 시 "사용자 없음" / "비밀번호 틀림" 분리 메시지 금지 — 사용자 enumeration 공격.
- `/api/auth/login` 이외 경로에 permitAll 추가 금지. 보호 경로 누락 = 보안 결함.
- access·refresh token 둘 다 같은 TTL 로 설정 금지. access 는 짧게.
- `JwtAuthenticationFilter` 가 invalid 토큰 시 즉시 401 throw 하지 마라. 익명으로 통과시키고 SecurityConfig 가 차단.
- 토큰을 응답 본문에만 두지 말고 httpOnly Cookie 도 추가하는 식의 이중 채널 금지 (혼란). 본 phase 는 응답 본문 access/refresh 만. Cookie 는 별도 ADR.
- 프런트엔드 코드 수정 금지 (step 2~4 의 책임 범위 밖).
- meta/ticket/asset 의 운영 코드 수정 금지 — 기존 `@WebMvcTest` 가 깨지면 테스트 슬라이스에서만 `addFilters=false` 로 보정.
