# 인증·사용자·권한·메뉴 e2e 보고서 — Phase 9 (auth-and-users)

> Phase 9 (9-auth-and-users) Step 4 — `e2e-with-admin-seed`
> 작성일: 2026-05-29 · 대상: M7 인증·사용자·부서·역할·권한·메뉴
> PRD §9 M7 마일스톤 / ARCHITECTURE §7-2~7-3·§8 인증·인가 / ADR-008·ADR-011·ADR-020

본 보고서는 **초기 admin 사용자 + 기본 역할·권한·부서·메뉴를 시드**(`sql/init/14_auth_seed.sql`)하고,
**cURL 시나리오 15 단계로 JWT 인증·권한 분기·메뉴 권한 필터·본인 비번 변경·토큰 재발급·기존 모듈 회귀를
end-to-end 검증**한 결과를 기록한다. 이로써 PRD §9 M7 마일스톤("인증·사용자/부서/역할/권한/메뉴")의
핵심 약속을 검증한다.

---

## 1. 환경

| 항목 | 값 |
|------|-----|
| 작업 디렉토리 | `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM` |
| 브랜치 | `feat-9-auth-and-users` |
| Backend | Spring Boot 3.5.0 · Java 21 · Gradle (`local` 프로파일) |
| DB | PostgreSQL 16 · Docker 컨테이너 `itg-postgres` (`localhost:5432`, DB `itgdb`) |
| 헬스체크 | `GET /actuator/health` → `{"status":"UP"}` |
| 인증 | JWT (HS256, access 15m / refresh 7d) — ADR-008 자체 발급 |
| 시드 적용 | `docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/14_auth_seed.sql` |
| e2e 실행 | `bash scripts/e2e_phase9.sh` (멱등 — 시작·종료 시 user-sample-1 비번 시드값 복원) |

---

## 2. 시드 (`sql/init/14_auth_seed.sql`)

모든 INSERT 는 `ON CONFLICT DO NOTHING` — 멱등하다. 비밀번호는 BCrypt(`$2a$`, cost 10) 해시로만
저장하며(ADR-011), 평문은 가상 샘플(`*-sample-1234`)이다.

### 2-1. 권한 (Permission) — 13종

`USER_READ`, `USER_ADMIN`, `DEPT_READ`, `DEPT_ADMIN`, `ROLE_ADMIN`, `MENU_ADMIN`,
`META_READ`, `META_PUBLISH`, `TICKET_READ`, `TICKET_CREATE`, `TICKET_APPROVE_L1`,
`ASSET_READ`, `ASSET_ADMIN`.

> `ROLE_ADMIN` 은 역할 코드이자 권한 코드로 동시에 쓰인다. `menu.permission_code` 가
> `fk_menu_permission` 으로 `permission(code)` 를 참조하고, 메뉴 트리(시스템 관리·역할)의 권한 키가
> `ROLE_ADMIN` 이므로 권한 목록에 포함된다(스펙 권한 13종 중 하나). `ROLE_ADMIN` 역할은 "모든 권한"
> 을 보유하므로 이 권한도 자연히 가지며 메뉴 필터·`@PreAuthorize` 와 일관적이다.

### 2-2. 역할 (Role) — 3종

| 역할 | 권한 |
|------|------|
| `ROLE_ADMIN` | 모든 권한 (13종) |
| `ROLE_IT_SUPPORT` | `USER_READ` + `TICKET_READ` + `TICKET_CREATE` + `TICKET_APPROVE_L1` + `ASSET_READ` |
| `ROLE_USER` | `TICKET_READ` + `TICKET_CREATE` + `ASSET_READ` |

### 2-3. 부서 (Department) — 3개

| id | code | name | path |
|----|------|------|------|
| 1 | ROOT | 전사 | `/1/` |
| 2 | IT | IT부문 | `/1/2/` |
| 3 | OPS | 운영부문 | `/1/3/` |

### 2-4. 사용자 (user_account) — 3명

| id | username | 평문(테스트용) | 역할 | 부서 |
|----|----------|----------------|------|------|
| 1 | `admin` | `admin-sample-1234` | ROLE_ADMIN | IT |
| 2 | `it-support` | `it-sample-1234` | ROLE_IT_SUPPORT | IT |
| 3 | `user-sample-1` | `user-sample-1234` | ROLE_USER | 운영 |

### 2-5. 메뉴 트리 (menu) — 13 노드

```
대시보드        /                    (NULL — 모두 보임)
ITSM           /itsm                (TICKET_READ, group=itg-ticket)
 └ 티켓 등록    /itsm                (TICKET_CREATE, group=itg-ticket)
ITAM           /itam                (ASSET_READ, group=itg-asset)
PMS            /pms                 (NULL)
공통            /common              (NULL)
시스템 관리      /system              (ROLE_ADMIN)
 ├ 사용자       /system/users        (USER_READ)
 ├ 부서         /system/depts        (DEPT_READ)
 ├ 역할         /system/roles        (ROLE_ADMIN)
 ├ 메뉴         /system/menus        (MENU_ADMIN)
 ├ 메타 관리     /system/meta         (META_READ)
 └ 메타 편집기   /system/meta-editor  (META_PUBLISH)   [M9 대비]
```

---

## 3. 시나리오 결과 — 15 단계 (+헬스) 전부 PASS

| # | 시나리오 | 기대 | 결과 |
|---|----------|------|------|
| 0 | `GET /actuator/health` | `UP` | ✅ PASS |
| 1 | 익명 `GET /api/users` | 401 (AUTH_REQUIRED) | ✅ PASS |
| 2 | 잘못된 비번 로그인 | 401 (LOGIN_FAILED) | ✅ PASS |
| 3 | `admin` 로그인 | 200 + accessToken | ✅ PASS |
| 4 | `GET /api/auth/me` (admin) | roles⊇ROLE_ADMIN, perms⊇USER_ADMIN | ✅ PASS |
| 5 | admin `GET /api/users` | 200 + totalElements≥3 (=3) | ✅ PASS |
| 6 | `user-sample-1` 로그인 | 200 + accessToken | ✅ PASS |
| 7 | user `GET /api/users` | 403 (FORBIDDEN — USER_READ 없음) | ✅ PASS |
| 8 | `GET /api/auth/me` (user) | roles=[ROLE_USER] | ✅ PASS |
| 9 | admin `GET /api/menu` | 시스템 관리 + 사용자 노출 | ✅ PASS |
| 10 | user `GET /api/menu` | 시스템 관리 미노출 + ITSM 노출 | ✅ PASS |
| 11 | user 본인(id=3) 비번 변경 `PATCH /api/users/3/password` | 200 | ✅ PASS |
| 12 | user 타인(id=1) 비번 변경 시도 | 403 (SelfCheck) | ✅ PASS |
| 13 | `POST /api/auth/refresh` (refresh 토큰) | 200 + 새 accessToken | ✅ PASS |
| 14 | admin `GET /api/tickets` | 200 (회귀) | ✅ PASS |
| 15 | admin `GET /api/meta/active/itg-ticket` | 200 (회귀) | ✅ PASS |

```
RESULT: PASS=16 FAIL=0
```

---

## 4. 핵심 검증 사실

- **JWT 발급·검증·refresh 정상**: 로그인이 access(15분)·refresh(7일) 토큰을 발급하고, `Authorization: Bearer`
  로 보호 자원에 접근하며, refresh 토큰으로 새 access 토큰을 재발급한다 (시나리오 3·4·13).
- **401 AUTH_REQUIRED · 403 FORBIDDEN · 한글 메시지**: 익명 접근은 401, 권한 부족은 403 으로 분리되며
  본문 메시지는 한글이다 (`로그인이 필요합니다` / `아이디 또는 비밀번호가 올바르지 않습니다`). `errorCode` 는
  분기·로깅용이고 화면 노출 메시지는 카탈로그 매핑 대상(ADR-020).
- **`@PreAuthorize` 권한 분기 동작**: 같은 `/api/users` 가 admin(USER_ADMIN/ROLE_ADMIN)에게는 200,
  일반 사용자(권한 없음)에게는 403 — 역할별로 다르게 동작 (시나리오 5·7).
- **`/api/menu` 권한 필터 트리**: `MenuService.getTreeFor` 가 사용자 authority(역할+권한)로 메뉴를 필터해,
  admin 은 시스템 관리 전체를, 일반 사용자는 ITSM 만 보고 시스템 관리는 트리에서 제외된다 (시나리오 9·10).
- **본인 비번 변경 허용 + 타인 차단 (SelfCheck)**: `@selfCheck.isSelf` 가 대상 PK 를 username 으로 환원해
  본인은 200, 타인은 403 으로 막는다 (시나리오 11·12).
- **기존 모듈 회귀 PASS**: 인증 도입 후에도 admin 토큰으로 `/api/tickets`·`/api/meta/active/itg-ticket` 가
  정상 200 — ticket/asset/meta 모듈 회귀 없음 (시나리오 14·15). 전체 단위/통합 테스트 `BUILD SUCCESSFUL`.

### 4-1. e2e 중 발견·수정한 결함 (step 2 보정)

- **`function lower(bytea) does not exist`** — `UserRepository.search` 의 키워드 검색에서 `kw` 파라미터가
  null 일 때 PostgreSQL 이 untyped null 을 bytea 로 추론해 `lower(bytea)` 호출 오류로 `/api/users` 가
  INTERNAL_ERROR(500) 를 냈다. JPQL 에 `cast(:kw as string)` 으로 타입을 고정해 해결.
  이 결함은 **Mockito 단위 테스트로는 재현되지 않는 DB 레벨 결함**(ADR-014 §5 의 경고 사례와 동일 성격)으로,
  본 e2e 단계에서야 드러났다. 수정 후 전체 테스트 재실행 통과 (회귀 없음).

---

## 5. 한계

- **프런트엔드 통합 미포함**: 로그인 페이지·메뉴 동적 렌더링·`useAuthStore` 토큰 주입은 **phase 10** 의 책임.
  본 step 은 백엔드 cURL e2e 까지만 검증한다 (프런트엔드 수정 없음).
- **본인 비번 변경 시 기존 비번 확인 없음**: `PATCH /api/users/{id}/password` 는 현재 비밀번호 확인 절차가
  없다 (본 phase 범위 밖 — 추후 보강 대상).
- **운영 비밀번호 강도 검증 부재**: 8자 이상 등 강도 정책은 본 phase 의 가드 밖. 도입 시 별도 ADR.
- **토큰 무효화(블랙리스트) 없음**: stateless JWT 이므로 로그아웃은 클라이언트 토큰 폐기로만 처리한다
  (서버 무효화 없음 — ADR-008).
- **시드 비밀번호는 가상 샘플**: `*-sample-1234` 는 테스트용이며 운영 배포 시 즉시 변경해야 한다.

---

## 6. 산출물

| 산출물 | 비고 |
|--------|------|
| `sql/init/14_auth_seed.sql` | **본 step — 권한 13·역할 3·부서 3·사용자 3·메뉴 13 시드 (BCrypt 해시·멱등)** |
| `scripts/e2e_phase9.sh` | **본 step — cURL 15 단계 e2e 시나리오 (멱등 복원 포함)** |
| `backend/src/.../user/repository/UserRepository.java` | step 2 보정 — `cast(:kw as string)` (lower(bytea) 결함) |
| `backend/openapi/itg-api-spec.{json,yaml}` | OpenAPI 갱신 (인증/사용자/부서/역할/권한/메뉴 경로 포함) |
| `backend/E2E_REPORT_PHASE9.md` | **본 보고서** |
| `CLAUDE.md` (로컬 접속 정보) | 시드 사용자 3명 안내 추가 |

> 시드(권한·역할·사용자·부서·메뉴)는 DB 에 **보존**한다 — 다음 phase(프런트엔드 통합)에서 사용한다.

---

## 7. 결론

- **PRD §9 M7 마일스톤 이행**: `시드 → 로그인 → Bearer 인증 → 권한 분기 → 메뉴 필터 → 본인 비번 변경 →
  토큰 재발급` 의 인증·인가 전 흐름이 실제로 동작함을 cURL e2e 15 단계로 검증했다.
- **인증·사용자·부서·역할·권한·메뉴(M7) 완료**: admin/IT support/user 3개 주체가 각각 다른 권한·메뉴·접근
  결과를 보이며, 기존 ticket/asset/meta 모듈도 인증 후 정상 동작한다.
