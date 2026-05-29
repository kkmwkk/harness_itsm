# Step 2: user-dept-role-perm-crud

## 읽어야 할 파일

- `/CLAUDE.md` v2.1·절대 규칙
- `/docs/ARCHITECTURE.md` §7-3 사용자·부서·역할·권한·메뉴 API
- `/docs/ADR.md` ADR-009 ApiResponse·ADR-012 Fixture
- `/phases/9-auth-and-users/step0.md`·`step1.md` — Entity·JwtService·AuthService
- `/backend/src/main/java/com/nkia/itg/itsm/ticket/service/TicketService.java` (Service 스타일 참고)

## 작업

이 step 의 목적은 **사용자·부서·역할·권한·메뉴의 Repository + Service + DTO + Mockito 단위 테스트를 구축**하는 것이다. Controller·@PreAuthorize·Swagger 는 step 3.

### 1. Repository — `com.nkia.itg.system.{user|dept|role|permission|menu}.repository`

```java
public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    @Query("...")
    Page<UserAccount> search(@Param("dept") Long deptId, @Param("role") String roleCode,
                              @Param("status") UserStatus status, @Param("kw") String keyword,
                              Pageable pageable);
    boolean existsByUsername(String username);
}

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByCode(String code);
    List<Department> findAllByOrderByPathAsc();           // 트리 정렬
}

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);
}

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
}

public interface MenuRepository extends JpaRepository<Menu, Long> {
    List<Menu> findAllByActiveTrueOrderBySortOrderAsc();
}
```

### 2. Service — `com.nkia.itg.system.{...}.service`

각 Service `@Transactional` + `@Transactional(readOnly=true)` 분리.

#### `UserService` (가장 풍부)
```java
UserResponse create(UserCreateRequest req);      // username 중복·비번 BCrypt 해시
UserResponse getById(Long id);
UserResponse update(Long id, UserUpdateRequest req);
void         lock(Long id) / unlock(Long id) / retire(Long id);
void         changePassword(Long id, String newPlain);    // BCrypt + Entity#changePassword
Page<UserSummary> search(Long deptId, String roleCode, UserStatus status, String kw, int page, int size);
void         assignRole(Long userId, String roleCode);
void         revokeRole(Long userId, String roleCode);
```

> 비밀번호는 항상 `BCryptPasswordEncoder` 로 해시 후 저장. 평문은 절대 DB 에 들어가지 않는다.

#### `DepartmentService`
```java
DepartmentResponse create(DepartmentCreateRequest req);   // path 자동 계산
DepartmentResponse update(Long id, DepartmentUpdateRequest req);
void move(Long id, Long newParentId);                     // 트리 이동 + path 재계산 (자기/자손에 이동 금지)
void deactivate(Long id);
List<DepartmentTreeNode> getTree();                       // path 정렬 → 트리 조립
```

> 트리 path 계산: `parent.path + "/" + id` (예: `/1/3/12/`). 자기·자손으로 이동 금지(순환 방지) — IllegalStateException.

#### `RoleService` / `PermissionService` (단순)
- CRUD + `RoleService.grantPermission(roleCode, permissionCode)`·`revokePermission(...)`.

#### `MenuService`
- CRUD + `move(id, newParentId, newSortOrder)`.
- `getTreeFor(currentUser)`:
  1. 모든 active 메뉴 로드
  2. `permissionCode` null 또는 사용자가 그 permission 보유한 것만 필터
  3. parent_id 트리 조립
  4. 반환

### 3. DTO Record

각 모듈마다 Create/Update/Response/Summary 분리. 예시:

```java
public record UserCreateRequest(
    @Schema(example = "u-sample-001") @NotBlank @Size(max=80) String username,
    @Schema(example = "샘플-비번")       @NotBlank @Size(min=8, max=72) String password,
    @Schema(example = "샘플 사용자")    @NotBlank String name,
    @Schema(example = "u@example.com")   String email,
    @Schema(example = "010-0000-0000")   String phone,
    @Schema(example = "1")               Long departmentId,
    @Schema(example = "[\"ROLE_USER\"]") List<String> roleCodes
) {}

public record UserUpdateRequest(
    String name, String email, String phone, Long departmentId
) {}

public record UserResponse(
    Long id, String username, String name, String email, String phone,
    UserStatus status, Long departmentId, String departmentName,
    List<String> roleCodes, List<String> permissionCodes,
    LocalDateTime passwordChangedAt, LocalDateTime lastLoginAt,
    LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static UserResponse from(UserAccount u) { ... }
}

public record DepartmentCreateRequest(
    @NotBlank String code, @NotBlank String name, Long parentId
) {}

public record DepartmentTreeNode(
    Long id, String code, String name, Long parentId, String path,
    int sort, boolean active, List<DepartmentTreeNode> children
) {}
```

Role/Permission/Menu 도 같은 패턴.

### 4. Fixture — `src/test/java/com/nkia/itg/fixture/SystemFixtures.java`

```java
public final class SystemFixtures {
    public static UserAccount activeUser(String username, Long deptId) { ... }
    public static UserAccount lockedUser(String username) { ... }
    public static Department  rootDept(String code, String name) { ... }       // path="/{id}/"
    public static Department  childDept(Department parent, String code, String name) { ... }
    public static Role        role(String code, String name) { ... }
    public static Permission  permission(String code, String name) { ... }
    public static Menu        menu(String code, String label, String route, String permCode) { ... }
}
```

### 5. 단위 테스트 (Mockito)

#### `UserServiceTest` (~12 케이스)
- `create_username_중복_DATA_INTEGRITY` (Mock `existsByUsername` → true).
- `create_password_BCrypt_해시_저장_평문_노출_없음`.
- `create_role_지정_시_assignRole_호출`.
- `update_RETIRED_거부` (UserAccount 의 `lock/retire` 도메인 메서드 일관성).
- `lock_unlock_retire_상태_전이`.
- `changePassword_평문_해시_변환_후_Entity_도메인_호출`.
- `search_dept_role_status_keyword_파라미터_repository_전달`.
- `assignRole_revokeRole_매핑_갱신`.

#### `DepartmentServiceTest`
- `create_root_path_/{id}/_자동_계산`.
- `create_child_부모_path_+_id_연결`.
- `move_자기_자손_이동_거부_IllegalStateException`.
- `move_정상_시_자손_path_재계산`.
- `getTree_path_정렬로_트리_조립`.

#### `RoleServiceTest` · `PermissionServiceTest`
- 기본 CRUD + `grantPermission`/`revokePermission`.

#### `MenuServiceTest`
- `move`·`getTreeFor` (permissionCode null + 사용자 보유 권한 매칭).
- `getTreeFor_권한_없으면_그_메뉴_제외`.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend

# 1) 파일 존재
test -f src/main/java/com/nkia/itg/system/user/repository/UserRepository.java
test -f src/main/java/com/nkia/itg/system/user/service/UserService.java
test -f src/main/java/com/nkia/itg/system/dept/service/DepartmentService.java
test -f src/main/java/com/nkia/itg/system/role/service/RoleService.java
test -f src/main/java/com/nkia/itg/system/permission/service/PermissionService.java
test -f src/main/java/com/nkia/itg/system/menu/service/MenuService.java
test -f src/test/java/com/nkia/itg/fixture/SystemFixtures.java

# 2) BCrypt 사용 확인 (UserService 가 BCryptPasswordEncoder 주입)
grep -q "BCryptPasswordEncoder\|PasswordEncoder" src/main/java/com/nkia/itg/system/user/service/UserService.java

# 3) 단위 테스트
./gradlew test --tests "com.nkia.itg.system.*.service.*Test"
./gradlew test
./gradlew build
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - Service `@Transactional` 클래스 레벨 + 조회 readOnly?
   - 비밀번호가 BCrypt 해시로만 저장 (테스트에서 검증)?
   - Department 트리 path 계산이 Service 책임이고 트리거 의존 없음?
   - Department.move 가 자기·자손 이동 금지 가드 보유?
   - Menu.getTreeFor 가 permissionCode 필터 적용?
   - 모든 도메인 메서드 호출이 Entity 위임 (Service 가 필드 직접 set 금지)?
3. step 2 업데이트:
   - 성공 → `"summary": "UserRepository/Service (BCrypt + 검색 페이지·역할 할당) + DepartmentService (트리 path 자동·move 가드) + RoleService·PermissionService (CRUD + grant/revoke) + MenuService (getTreeFor 권한 필터·move) + DTO Record + SystemFixtures + Mockito 단위 테스트 ~30 케이스."`

## 금지사항

- 평문 비밀번호 DB 저장 금지. 항상 BCrypt 해시.
- 비밀번호를 Response DTO 에 포함 금지.
- Service 안에 SecurityContext 접근(`SecurityContextHolder.getContext()`) 직접 금지 — 사용자 정보는 메서드 인자로 받는다 (Controller 가 추출).
- Department 트리 path 를 DB 트리거로 자동 계산하지 마라.
- Department.move 가 자기·자손 이동 허용하면 안 됨 (순환 트리).
- Menu.getTreeFor 가 비-활성 메뉴 노출 금지.
- 메타·ticket·asset 모듈 수정 금지.
- 프런트엔드 수정 금지.
- 테스트 데이터에 실 운영 데이터 금지. 가상 샘플(`u-sample-*`, `샘플-비번`).
