# Step 0: auth-schema-and-domain

## 읽어야 할 파일

- `/CLAUDE.md` — v2.1 비전·M7
- `/docs/PRD.md` §4-1 인증·사용자 도메인 모델
- `/docs/ARCHITECTURE.md` §14-1 인증·메뉴 ERD, §8 인증·인가
- `/docs/ADR.md` ADR-008 (JWT 자체 인증)
- `/sql/init/01_schema.sql` (page_meta 의 `fn_touch_updated_at()` 재사용)
- `/backend/src/main/java/com/nkia/itg/itsm/ticket/entity/Ticket.java` (Entity 스타일)

## 작업

이 step 의 목적은 **User/Department/Role/Permission/Menu + 매핑 테이블 (user_role/role_permission) 의 DB 스키마와 JPA Entity 를 만들고, 단위 테스트로 도메인 메서드를 보증하는 것**이다.

### 1. DB 스키마 — `sql/init/10_auth.sql`

요구사항 (PRD §4-1):
- `user_account` (예약어 회피로 `user` 대신) — id, username UNIQUE, password_hash, name, email, phone, department_id (FK), status (ACTIVE/LOCKED/RETIRED), password_changed_at, last_login_at, created_at, updated_at.
- `department` — id, code UNIQUE, name, parent_id (FK 자기참조), path (예: `1/3/12`), manager_user_id (FK → user_account, NULL), active.
- `role` — id, code UNIQUE, name, description.
- `permission` — id, code UNIQUE, name, description.
- `user_role` — user_id, role_id (PK 복합), granted_at, granted_by.
- `role_permission` — role_id, permission_id (PK 복합).
- `menu` — id, code UNIQUE, parent_id (FK 자기참조), label, icon, sort_order, route, group_id (PageMeta 그룹 매핑, NULL 허용), permission_code (FK → permission.code, NULL 허용), active.

CHECK 제약·인덱스:
- `user_account.status` CHECK (`ACTIVE`/`LOCKED`/`RETIRED`)
- 인덱스: `idx_user_username`(UNIQUE), `idx_user_dept`, `idx_user_status`, `idx_dept_parent`, `idx_dept_path`, `idx_menu_parent`, `idx_menu_sort`, `idx_role_perm_role`, `idx_user_role_user`.
- 트리거: 모든 테이블에 `trg_*_touch_updated_at` (01_schema 의 `fn_touch_updated_at` 재사용).

멱등성: 모든 DDL `IF NOT EXISTS`. `DROP TRIGGER IF EXISTS`.

### 2. 패키지 구조 — `com.nkia.itg.system`

```
backend/src/main/java/com/nkia/itg/system/
├── user/
│   ├── domain/UserStatus.java          enum { ACTIVE, LOCKED, RETIRED }
│   └── entity/UserAccount.java
├── dept/entity/Department.java
├── role/entity/Role.java
├── permission/entity/Permission.java
├── menu/entity/Menu.java
└── (매핑 테이블은 별도 Entity 없이 Role/Permission 의 @ManyToMany)
```

### 3. `UserAccount` Entity 핵심

- PK: `Long id` BIGSERIAL.
- `username String UNIQUE NOT NULL`.
- `passwordHash String NOT NULL` — `length=72` (BCrypt 최대 60 + margin).
- `name`·`email`·`phone` nullable.
- `department_id` FK NULL 허용 (department 가 없는 사용자도 가능).
- `status UserStatus` (`@Enumerated.STRING`, default `ACTIVE`).
- `passwordChangedAt`·`lastLoginAt`·`createdAt`·`updatedAt`.
- 도메인 메서드:
  - `lock()` — status=LOCKED. ACTIVE 에서만 가능.
  - `unlock()` — LOCKED → ACTIVE.
  - `retire()` — 모든 상태에서 RETIRED. 한 번 RETIRED 면 다른 전이 불가.
  - `changePassword(newHash)` — passwordHash 갱신 + passwordChangedAt=now. RETIRED 면 거부.
  - `touchLastLogin()` — lastLoginAt=now.

### 4. `Department` Entity 핵심

- `code String UNIQUE`, `name String`.
- `parentId Long NULL`, `path String` (예: `/1/3/12/`).
- `managerUserId Long NULL` (FK 양방향 회피 — Long 만).
- `active boolean default true`.
- 도메인 메서드:
  - `assignManager(userId)`·`removeManager()`.
  - `deactivate()`/`activate()`.

> 트리 path 자동 계산은 Service 책임 (도메인 메서드는 단일 행만).

### 5. `Role` ↔ `Permission` 매핑

JPA `@ManyToMany` 사용 — 매핑 테이블(`role_permission`) 은 별도 Entity 없이.

`Role` 안:
```java
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(name = "role_permission",
    joinColumns        = @JoinColumn(name = "role_id"),
    inverseJoinColumns = @JoinColumn(name = "permission_id"))
private Set<Permission> permissions = new HashSet<>();

public void grant(Permission p)  { permissions.add(p); }
public void revoke(Permission p) { permissions.remove(p); }
```

`UserAccount` ↔ `Role` 도 동일 패턴 (`user_role` 매핑).

### 6. `Menu` Entity

- `code UNIQUE`, `parentId NULL`, `label`, `icon` (lucide 아이콘명 — 예: `BoxesIcon`), `sortOrder int`, `route String`, `groupId String NULL` (PageMeta 와 연결), `permissionCode String NULL` (FK → permission.code), `active boolean`.
- 도메인 메서드: `setPermission(code)`, `moveTo(newParentId, newSortOrder)`, `deactivate()`.

### 7. 단위 테스트

각 Entity 의 도메인 메서드 핵심 케이스 (한글 메서드명 허용):

#### `UserAccountTest`
1. `lock_ACTIVE_에서_LOCKED_허용`.
2. `lock_LOCKED_재호출_시_idempotent_또는_예외` — 정책: idempotent (no-op).
3. `lock_RETIRED_에서_거부`.
4. `unlock_LOCKED_에서_ACTIVE_허용`.
5. `retire_어느_상태든_RETIRED_로_전이`.
6. `retire_RETIRED_재호출_시_idempotent`.
7. `changePassword_RETIRED_거부`.
8. `changePassword_정상_시_passwordChangedAt_set`.
9. `touchLastLogin_lastLoginAt_set`.

#### `DepartmentTest`
1. `assignManager·removeManager·deactivate·activate` 기본.

#### `RoleTest`
1. `grant·revoke_권한_추가_삭제`.
2. `grant_중복_set_특성으로_무시`.

#### `MenuTest`
1. `setPermission`·`moveTo`·`deactivate`.

> Entity 도메인 메서드는 단일 행 상태 변화만. 트리 path·트랜잭션 일관성·검색은 Service(step 2) 책임.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM

# 1) SQL 파일
test -f sql/init/10_auth.sql
grep -q "CREATE TABLE IF NOT EXISTS user_account"      sql/init/10_auth.sql
grep -q "CREATE TABLE IF NOT EXISTS department"        sql/init/10_auth.sql
grep -q "CREATE TABLE IF NOT EXISTS role"              sql/init/10_auth.sql
grep -q "CREATE TABLE IF NOT EXISTS permission"        sql/init/10_auth.sql
grep -q "CREATE TABLE IF NOT EXISTS user_role"         sql/init/10_auth.sql
grep -q "CREATE TABLE IF NOT EXISTS role_permission"   sql/init/10_auth.sql
grep -q "CREATE TABLE IF NOT EXISTS menu"              sql/init/10_auth.sql

# 2) Entity 파일
cd backend
test -f src/main/java/com/nkia/itg/system/user/domain/UserStatus.java
test -f src/main/java/com/nkia/itg/system/user/entity/UserAccount.java
test -f src/main/java/com/nkia/itg/system/dept/entity/Department.java
test -f src/main/java/com/nkia/itg/system/role/entity/Role.java
test -f src/main/java/com/nkia/itg/system/permission/entity/Permission.java
test -f src/main/java/com/nkia/itg/system/menu/entity/Menu.java

# 3) 단위 테스트
./gradlew test --tests "com.nkia.itg.system.*.*Test"
./gradlew build

# 4) DB 적용 + 부팅 validate
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/10_auth.sql
docker exec -i itg-postgres psql -U itg -d itgdb -c "\d user_account" | grep -q username

cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q UP
kill %1
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - 테이블명이 SQL 예약어를 피했는가? (`user` → `user_account`)
   - 매핑 테이블이 별도 Entity 없이 `@ManyToMany` 로 표현되는가?
   - `passwordHash` 컬럼 길이가 BCrypt 호환(60+)?
   - `Menu.groupId` 가 PageMeta 의 group_id 와 join 가능 (NULL 허용 + 비-FK)?
   - `Menu.permissionCode` 가 NULL 또는 permission.code 와 매칭?
   - `ddl-auto: validate` 부팅 성공?
3. step 0 업데이트:
   - 성공 → `"summary": "sql/init/10_auth.sql (user_account/department/role/permission/user_role/role_permission/menu + 인덱스 + updated_at 트리거) + 시스템 모듈 패키지(system.user/dept/role/permission/menu) + Entity 5종 + UserStatus Enum + 도메인 메서드(lock/unlock/retire/changePassword/touchLastLogin / grant/revoke / setPermission/moveTo) + @ManyToMany 매핑(user_role/role_permission). 단위 테스트 ~14 케이스. validate 부팅 통과."`

## 금지사항

- 테이블명에 SQL 예약어(`user`·`order`·`group`) 사용 금지. `user_account` 같은 명확한 이름.
- Entity 에 `@Setter` 추가 금지. 도메인 메서드만.
- 매핑 테이블(`user_role`·`role_permission`) 을 별도 Entity 로 만들지 마라 (cascade 복잡성 회피). `@ManyToMany` + `@JoinTable`.
- 비밀번호 해시 컬럼을 평문 비밀번호 길이(20~) 로 설정 금지. BCrypt 최소 60자.
- `Department` 의 path 자동 계산을 트리거로 구현 금지 (트리거 의존성 회피). Service 책임.
- `Menu.groupId` 를 page_meta(id) 의 FK 로 설정 금지 (group_id 는 page_meta.id 가 아니라 group 키). 비-FK.
- 사용자/메뉴 시드를 이 step 에서 추가하지 마라 (step 4 의 책임).
- 인증 흐름(JWT) 코드를 이 step 에서 만들지 마라 (step 1).
- 기존 ticket·asset·meta 모듈 수정 금지.
