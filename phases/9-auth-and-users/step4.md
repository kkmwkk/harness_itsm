# Step 4: e2e-with-admin-seed

## 읽어야 할 파일

- `/CLAUDE.md` v2.1
- `/docs/PRD.md` §9 M7 마일스톤
- `/docs/ARCHITECTURE.md` §7-2~7-3·§8 인증·인가
- `/docs/ADR.md` ADR-008·ADR-011·ADR-020
- `/phases/9-auth-and-users/step0~3.md` 산출물 전체

## 작업

이 step 의 목적은 **초기 admin 사용자 + 기본 역할/권한/메뉴를 시드하고, cURL 시나리오로 인증·권한·메뉴 동작을 end-to-end 검증하며, `backend/E2E_REPORT_PHASE9.md` 보고서를 작성하는 것**이다.

### 1. 시드 SQL — `sql/init/14_auth_seed.sql`

내용:
- **권한** (Permission) 시드 — 모듈/액션 코드 일괄:
  - `USER_READ`, `USER_ADMIN`, `DEPT_READ`, `DEPT_ADMIN`, `ROLE_ADMIN`, `MENU_ADMIN`,
    `META_READ`, `META_PUBLISH`, `TICKET_READ`, `TICKET_CREATE`, `TICKET_APPROVE_L1`,
    `ASSET_READ`, `ASSET_ADMIN`.
- **역할** (Role) 3종:
  - `ROLE_ADMIN`: 모든 권한.
  - `ROLE_IT_SUPPORT`: USER_READ + TICKET_READ + TICKET_CREATE + TICKET_APPROVE_L1 + ASSET_READ.
  - `ROLE_USER`: TICKET_READ + TICKET_CREATE + ASSET_READ.
- **부서** 시드: ROOT(`/1/`) + IT(`/1/2/`) + 운영(`/1/3/`).
- **사용자** 시드:
  - `admin` / BCrypt(`admin-sample-1234`) — ROLE_ADMIN.
  - `it-support` / BCrypt(`it-sample-1234`) — ROLE_IT_SUPPORT.
  - `user-sample-1` / BCrypt(`user-sample-1234`) — ROLE_USER.
- **메뉴** 트리 시드:
  ```
  대시보드      /            (모두 보임, permission=NULL)
  ITSM         /itsm        (TICKET_READ, group=itg-ticket)
   - 티켓 등록  /itsm        (TICKET_CREATE, group=itg-ticket)
  ITAM         /itam        (ASSET_READ, group=itg-asset)
  PMS          /pms         (NULL — 메타 미존재, ready=false)
  공통         /common      (NULL)
  시스템 관리   /system      (ROLE_ADMIN)
   - 사용자    /system/users        (USER_READ)
   - 부서      /system/depts        (DEPT_READ)
   - 역할      /system/roles        (ROLE_ADMIN)
   - 메뉴      /system/menus        (MENU_ADMIN)
   - 메타 관리  /system/meta         (META_READ)
   - 메타 편집기 /system/meta-editor (META_PUBLISH)   [M9 대비]
  ```
- 멱등성: 모든 INSERT `ON CONFLICT DO NOTHING`.

> BCrypt 해시 값은 시드 SQL 에 미리 박힌 값(예: `$2a$10$...`). 보안 위해 가상 비밀번호만 — `admin-sample-1234` 등 명시.

### 2. E2E 시나리오 (cURL)

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d

# 시드 적용
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/10_auth.sql     2>/dev/null || true
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/14_auth_seed.sql

cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
BE_PID=$!
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q UP

# === 인증 시나리오 ===

# 1) 익명으로 /api/users → 401 AUTH_REQUIRED
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/users)" = "401"

# 2) 잘못된 비번 로그인 → 401 LOGIN_FAILED
test "$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}')" = "401"

# 3) admin 로그인 → 200 + accessToken
ADMIN_TOKEN=$(curl -fsS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-sample-1234"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])")
test -n "$ADMIN_TOKEN"

# 4) Bearer 로 /api/auth/me → roles 에 ROLE_ADMIN 포함
curl -fsS -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/auth/me \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 assert 'ROLE_ADMIN' in d['roles']; \
                 assert 'USER_ADMIN' in d['permissions']"

# 5) admin 으로 /api/users → 200 + 3명 이상
curl -fsS -H "Authorization: Bearer $ADMIN_TOKEN" "http://localhost:8080/api/users?page=0&size=20" \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; assert d['totalElements']>=3"

# === 권한 분기 시나리오 ===

# 6) user-sample-1 로그인
USER_TOKEN=$(curl -fsS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user-sample-1","password":"user-sample-1234"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])")

# 7) user 토큰으로 /api/users → 403 FORBIDDEN (USER_READ 없음)
test "$(curl -s -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer $USER_TOKEN" \
  http://localhost:8080/api/users)" = "403"

# 8) user 토큰으로 /api/auth/me → 200 + roles=[ROLE_USER]
curl -fsS -H "Authorization: Bearer $USER_TOKEN" http://localhost:8080/api/auth/me \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; assert 'ROLE_USER' in d['roles']"

# === 메뉴 권한 필터 시나리오 ===

# 9) admin 의 /api/menu → 시스템 관리 + 모든 자식 노출
curl -fsS -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/menu \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 labels=set(); \
                 def walk(nodes):
                   for n in nodes:
                     labels.add(n['label'])
                     walk(n.get('children',[]))
                 walk(d); \
                 assert '시스템 관리' in labels and '사용자' in labels"

# 10) user 의 /api/menu → 시스템 관리 미노출
curl -fsS -H "Authorization: Bearer $USER_TOKEN" http://localhost:8080/api/menu \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 labels=set(); \
                 def walk(nodes):
                   for n in nodes:
                     labels.add(n['label'])
                     walk(n.get('children',[]))
                 walk(d); \
                 assert '시스템 관리' not in labels; \
                 assert 'ITSM' in labels  # TICKET_READ 보유 → 노출"

# === 본인 비번 변경 시나리오 ===

# 11) user-sample-1 가 본인 비번 변경 → 200
test "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"newPassword":"changed-sample-1234"}' \
  http://localhost:8080/api/users/3/password)" = "200"

# (id=3 가 user-sample-1 가정 — 시드 ID 매핑은 sql 에 명시)

# 12) user 가 타인 비번 변경 시도 → 403
test "$(curl -s -o /dev/null -w '%{http_code}' -X PATCH \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"newPassword":"hack"}' \
  http://localhost:8080/api/users/1/password)" = "403"

# === 토큰 갱신 시나리오 ===

# 13) refresh token 으로 새 access token 받기 (정상 일 때 200)
REFRESH_TOKEN=$(curl -fsS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-sample-1234"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['refreshToken'])")
curl -fsS -X POST http://localhost:8080/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}" \
  | grep -q '"accessToken"'

# === 회귀: 기존 ticket/asset 모듈도 인증 통과 시 정상 ===

# 14) admin 으로 /api/tickets → 200 (TICKET_READ 보유)
test "$(curl -s -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/tickets?page=0&size=5)" = "200"

# 15) admin 으로 /api/meta/active/itg-ticket → 200
test "$(curl -s -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/meta/active/itg-ticket)" = "200"

# OpenAPI 갱신
curl -fsS http://localhost:8080/v3/api-docs      > openapi/itg-api-spec.json
curl -fsS http://localhost:8080/v3/api-docs.yaml > openapi/itg-api-spec.yaml

kill $BE_PID

# 시드 보존 (다음 phase 사용)
```

### 3. 결과 보고서 — `backend/E2E_REPORT_PHASE9.md`

다음 섹션:
- 환경
- 시드 (권한 13종 / 역할 3종 / 사용자 3명 / 부서 3개 / 메뉴 트리)
- 시나리오 결과 (15 단계 표)
- 핵심 검증 사실:
  - JWT 발급·검증·refresh 정상.
  - 401 AUTH_REQUIRED · 403 FORBIDDEN · 한글 메시지.
  - `@PreAuthorize` 권한 분기 동작 (admin/IT support/user 차이).
  - `/api/menu` 가 권한 필터 적용된 트리만 반환.
  - 본인 비번 변경 허용 + 타인 비번 변경 차단 (SelfCheck).
  - 기존 모듈(ticket/asset/meta) 도 인증 후 정상 호출.
- 한계:
  - 프런트엔드 통합(로그인 페이지·메뉴 동적·useAuthStore) 은 phase 10 의 책임.
  - 본인 비번 변경 시 기존 비번 확인은 본 phase 범위 밖 (추후 보강).
  - 토큰 무효화(블랙리스트) 없음 — stateless JWT.
- 산출물.

### 4. CLAUDE.md 갱신 — 로컬 접속 정보 + 시드 사용자

`CLAUDE.md` 의 "로컬 접속 정보" 섹션에 추가:

```
- 시드 사용자 (테스트용 — 운영 비밀번호 변경 필수):
  - admin / admin-sample-1234 (ROLE_ADMIN)
  - it-support / it-sample-1234 (ROLE_IT_SUPPORT)
  - user-sample-1 / user-sample-1234 (ROLE_USER)
```

## Acceptance Criteria

```bash
# 1) 시드 파일
test -f /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/sql/init/14_auth_seed.sql

# 2) 위 §2 시나리오 15 단계 모두 통과

# 3) OpenAPI
test -s backend/openapi/itg-api-spec.json
python3 -c "import json; d=json.load(open('backend/openapi/itg-api-spec.json')); \
            need={'/api/auth/login','/api/auth/me','/api/users','/api/menu'}; \
            assert need.issubset(d['paths'].keys())"

# 4) 보고서
test -s backend/E2E_REPORT_PHASE9.md
grep -q "JWT" backend/E2E_REPORT_PHASE9.md
grep -q "@PreAuthorize" backend/E2E_REPORT_PHASE9.md
grep -q "ROLE_ADMIN" backend/E2E_REPORT_PHASE9.md

# 5) 시드 보존
docker exec -i itg-postgres psql -U itg -d itgdb -c \
  "SELECT count(*) FROM user_account WHERE username IN ('admin','it-support','user-sample-1');" \
  | grep -q "3"
docker exec -i itg-postgres psql -U itg -d itgdb -c \
  "SELECT count(*) FROM permission;" | grep -q -E "1[0-9]"   # 13+ 권한

# 6) CLAUDE.md 갱신
grep -q "admin-sample-1234" /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/CLAUDE.md
```

## 검증 절차

1. AC + §2 시나리오 15 단계 모두 통과.
2. 아키텍처 체크:
   - 인증·권한 분기가 admin/IT support/user 3명에서 모두 다르게 동작?
   - 메뉴 트리가 권한 필터 적용?
   - 본인 비번 변경 가능·타인 차단?
   - 401·403 한글 메시지?
   - 시드 보존 (다음 phase frontend 통합 사용)?
3. step 4 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "sql/init/14_auth_seed.sql (권한 13종·역할 3종 admin/IT support/user·사용자 3명·부서 3개·메뉴 트리 시드 with BCrypt 해시) + cURL 시나리오 15 단계 PASS — JWT 발급/검증/refresh, 401 AUTH_REQUIRED · 403 FORBIDDEN 한글 메시지, @PreAuthorize 권한 분기, /api/menu 권한 필터 트리, SelfCheck 본인 비번 변경, 기존 ticket/asset/meta 회귀 PASS. CLAUDE.md 시드 사용자 정보 추가. M7 인증·사용자·부서·역할·권한·메뉴 완료."`
   - 결함 → blocked.

## 금지사항

- 시드 BCrypt 해시 대신 평문 비밀번호 DB 저장 금지.
- 시나리오 종료 후 시드(권한/역할/사용자/메뉴) 정리 금지 — 다음 phase 사용.
- 운영 비밀번호 강도(8자 이상 등) 검증 누락 시 별도 ADR 추가 (본 phase 의 가드만 강제).
- 보고서·시드에 실 운영 데이터 금지. 가상 샘플(`admin-sample-1234` 등).
- 자동화 Playwright e2e 본 step 에서 추가 금지 — phase 11 의 책임.
- 프런트엔드 수정 금지.
- 새 endpoint·새 권한 추가 금지 (시나리오에 없는 것).
- 기존 ticket/asset/meta 회귀가 깨지면 그 모듈로 되돌리지 마라 — 본 phase 의 SecurityConfig 변경이 원인이면 step 1·3 의 보정으로 해결.
