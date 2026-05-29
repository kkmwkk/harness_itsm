# Step 3: controllers-and-permissions

## 읽어야 할 파일

- `/CLAUDE.md` 절대 규칙
- `/docs/ARCHITECTURE.md` §7-2 인증·§7-3 사용자·부서·역할·권한·메뉴 API·§8 인증·인가
- `/docs/ADR.md` ADR-009·ADR-011
- `/phases/9-auth-and-users/step0~2.md` — Entity·JwtService·AuthService·UserService 등

## 작업

이 step 의 목적은 **AuthController + 5개 시스템 모듈 Controller + `@PreAuthorize` 권한 가드 + Swagger 어노테이션 + `@WebMvcTest` 검증**이다.

### 1. `AuthController` — `com.nkia.itg.auth.controller`

```java
@Tag(name = "Auth — 인증")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(req)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(req)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(authentication.getName())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // stateless JWT — 서버에서 무효화 X. 클라이언트 측에서 토큰 폐기.
        return ResponseEntity.ok(ApiResponse.ok(null, "로그아웃되었습니다."));
    }
}
```

### 2. `UserController` — `com.nkia.itg.system.user.controller`

```java
@Tag(name = "System / User")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ') or hasAuthority('ROLE_ADMIN')")
    public ApiResponse<PageResponse<UserSummary>> search(
        @RequestParam(required = false) Long deptId,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) UserStatus status,
        @RequestParam(required = false) String kw,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) { ... }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ') or hasAuthority('ROLE_ADMIN')")
    public ApiResponse<UserResponse> getById(@PathVariable Long id) { ... }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserCreateRequest req) { ... }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest req) { ... }

    @PatchMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<Void> lock(@PathVariable Long id) { ... }

    @PatchMapping("/{id}/unlock") @PreAuthorize("hasAuthority('USER_ADMIN')") ...
    @PatchMapping("/{id}/retire") @PreAuthorize("hasAuthority('USER_ADMIN')") ...

    @PatchMapping("/{id}/password")
    @PreAuthorize("hasAuthority('USER_ADMIN') or @selfCheck.isSelf(authentication, #id)")
    public ApiResponse<Void> changePassword(@PathVariable Long id, @Valid @RequestBody PasswordChangeRequest req) { ... }

    @PostMapping("/{id}/roles/{roleCode}")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<Void> assignRole(@PathVariable Long id, @PathVariable String roleCode) { ... }

    @DeleteMapping("/{id}/roles/{roleCode}")
    @PreAuthorize("hasAuthority('USER_ADMIN')")
    public ApiResponse<Void> revokeRole(...) { ... }
}
```

> `@selfCheck.isSelf(authentication, #id)` — 본인 비밀번호 변경 허용용 SpEL. `SelfCheck` 빈을 추가 (간단한 username 비교).

### 3. `DepartmentController` / `RoleController` / `PermissionController` / `MenuController`

같은 패턴. 주요 endpoints:

- `GET /api/departments` (인증만), `GET /api/departments/tree` (인증만)
- `POST/PATCH/DELETE /api/departments` (`ROLE_ADMIN` 또는 `DEPT_ADMIN`)
- `GET /api/roles` (인증만), 쓰기 `ROLE_ADMIN`
- `GET /api/permissions` (`ROLE_ADMIN` 만 — 권한 목록은 민감)
- `GET /api/menu` (인증만) — **현재 사용자의 트리** (`MenuService.getTreeFor(authentication)`)
- `GET /api/menus` (관리자), 쓰기 `ROLE_ADMIN`

### 4. Spring Security 추가 설정 — `@EnableMethodSecurity`

`SecurityConfig` 에 `@EnableMethodSecurity` 추가하여 `@PreAuthorize` 활성화.

### 5. `SelfCheck` 빈 — `com.nkia.itg.common.security.SelfCheck`

```java
@Component("selfCheck")
public class SelfCheck {
    public boolean isSelf(Authentication auth, Long targetUserId, UserRepository userRepo) { ... }
    // 또는 username 비교로 단순화
}
```

### 6. Swagger 어노테이션 — 전체 메서드에 `@Operation` + `@Parameter(example=가상)`

`@SecurityRequirement(name = "bearerAuth")` 를 보호 endpoints 에 명시 (`SwaggerConfig` 에 이미 등록됨).

### 7. `@WebMvcTest` 테스트

#### `AuthControllerTest`
- `POST_login_정상_200`.
- `POST_login_검증_실패_400_VALIDATION_FAILED`.
- `POST_refresh_정상`.
- `GET_me_익명_401_AUTH_REQUIRED`.
- `GET_me_인증_시_200`.

#### `UserControllerTest`
- `GET_users_권한_없음_403_FORBIDDEN` (`@WithMockUser` 권한 없이).
- `GET_users_USER_READ_권한_200`.
- `POST_users_권한_없음_403`.
- `POST_users_USER_ADMIN_권한_201`.
- `PATCH_password_본인_허용`.
- `PATCH_password_타인_USER_ADMIN_없으면_403`.

#### `MenuControllerTest`
- `GET_menu_인증_사용자_권한_필터된_트리_반환`.
- `GET_menu_익명_401`.

> `@WithMockUser(authorities = "USER_READ")` 같은 형태로 권한 검증.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend

# 1) Controller 파일
test -f src/main/java/com/nkia/itg/auth/controller/AuthController.java
test -f src/main/java/com/nkia/itg/system/user/controller/UserController.java
test -f src/main/java/com/nkia/itg/system/dept/controller/DepartmentController.java
test -f src/main/java/com/nkia/itg/system/role/controller/RoleController.java
test -f src/main/java/com/nkia/itg/system/permission/controller/PermissionController.java
test -f src/main/java/com/nkia/itg/system/menu/controller/MenuController.java

# 2) @PreAuthorize 사용
grep -q "@PreAuthorize" src/main/java/com/nkia/itg/system/user/controller/UserController.java

# 3) @EnableMethodSecurity 활성화
grep -q "@EnableMethodSecurity" src/main/java/com/nkia/itg/common/security/SecurityConfig.java

# 4) SelfCheck 빈
test -f src/main/java/com/nkia/itg/common/security/SelfCheck.java

# 5) 단위 테스트
./gradlew test --tests "com.nkia.itg.auth.controller.*"
./gradlew test --tests "com.nkia.itg.system.*.controller.*"
./gradlew test       # 전체 회귀
./gradlew build

# 6) bootRun + Swagger 경로
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q UP
curl -fsS http://localhost:8080/v3/api-docs > /tmp/spec.json
python3 -c "import json; d=json.load(open('/tmp/spec.json')); \
            need={'/api/auth/login','/api/auth/me','/api/users','/api/users/{id}', \
                  '/api/departments','/api/roles','/api/permissions','/api/menu'}; \
            assert need.issubset(d['paths'].keys()), (need - set(d['paths'].keys()))"
kill %1
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - 모든 Controller endpoints 에 `@PreAuthorize` (인증만 요구는 SecurityConfig 의 authenticated() 가 처리, 권한 필요한 건 @PreAuthorize)?
   - login·refresh 만 permitAll, me 는 authenticated()?
   - 401/403 응답이 한글 JSON?
   - `@selfCheck.isSelf()` 가 본인 비번 변경 허용?
   - Swagger 경로 8 개 이상 등록?
3. step 3 업데이트:
   - 성공 → `"summary": "AuthController(login/refresh/me/logout) + 5개 시스템 Controller (User/Dept/Role/Permission/Menu) 권한 가드 (@PreAuthorize + USER_ADMIN/USER_READ/ROLE_ADMIN/MENU_ADMIN 등) + @EnableMethodSecurity + SelfCheck 빈(본인 비번 변경) + Swagger + @WebMvcTest. /v3/api-docs 에 인증/사용자/부서/역할/권한/메뉴 경로 등록 확인."`

## 금지사항

- `@PreAuthorize` 없이 노출되는 보호 endpoint 금지. SecurityConfig authenticated() + 메서드 권한 둘 다.
- 비밀번호 변경 endpoint 가 본인 외 타인의 비번을 USER_ADMIN 없이 허용하면 안 됨.
- `GET /api/users` 가 비밀번호 해시를 응답에 포함 금지.
- `@Schema(example)` 에 실 운영 데이터 금지 — 가상 샘플(`u-sample-001`, `샘플 사용자`).
- 로그아웃 endpoint 가 서버에서 토큰 무효화 시도 금지 (stateless JWT — 클라이언트 토큰 폐기).
- Controller 안에서 BCrypt·JWT 직접 다루기 금지 (Service 위임).
- 프런트엔드 수정 금지.
- 메뉴 트리 응답에 비-활성 메뉴 또는 권한 없는 메뉴 포함 금지.
