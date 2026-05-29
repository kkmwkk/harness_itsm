# Step 3: e2e-login-flow

## 읽어야 할 파일

- `/CLAUDE.md` v2.1
- `/docs/PRD.md` §9 M7
- `/docs/ADR.md` ADR-008·ADR-020
- `/phases/9-auth-and-users/index.json` (백엔드 시드)
- `/phases/10-menu-and-routing/step0~2.md` 산출물

## 작업

이 step 의 목적은 **프런트엔드 + 백엔드 동시 부팅 상태에서 로그인 흐름·메뉴 권한 필터·시스템 관리 페이지 동작을 e2e 로 검증하고, `frontend/E2E_REPORT_PHASE10.md` 작성**하는 것이다.

### 1. 시나리오

#### A. 환경
```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
BE_PID=$!
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q UP

cd ../frontend
pnpm install
pnpm type-check
pnpm lint
pnpm build
pnpm test
pnpm dev &
FE_PID=$!
sleep 6
```

#### B. cURL 자동 (시드 검증)
```bash
# 1) /api/auth/login 으로 admin 토큰 + 메뉴 노출 검증
ADMIN_TOKEN=$(curl -fsS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-sample-1234"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])")

# 2) admin 메뉴 — 시스템 관리 포함
curl -fsS -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/menu \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 labels=set(); \
                 def walk(ns): [labels.add(n['label']) or walk(n.get('children',[])) for n in ns]; \
                 walk(d); \
                 assert '시스템 관리' in labels; assert '사용자' in labels"

# 3) user-sample-1 메뉴 — 시스템 관리 미포함
USER_TOKEN=$(curl -fsS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user-sample-1","password":"user-sample-1234"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])")
curl -fsS -H "Authorization: Bearer $USER_TOKEN" http://localhost:8080/api/menu \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 labels=set(); \
                 def walk(ns): [labels.add(n['label']) or walk(n.get('children',[])) for n in ns]; \
                 walk(d); \
                 assert '시스템 관리' not in labels"

# 4) admin 사용자 등록 (POST /api/users) — 후속 시각 검증을 위해 1명 추가
curl -fsS -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"username":"u-phase10","password":"u-sample-9999","name":"E2E Phase10","roleCodes":["ROLE_USER"]}' \
  http://localhost:8080/api/users \
  | grep -q '"username":"u-phase10"'
```

#### C. SPA 라우트 200
```bash
for p in / /login /system/users /system/depts /system/roles /system/menus /itsm /itam /system/meta; do
  test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173$p)" = "200"
done
```

#### D. 브라우저 수동 검증

`http://localhost:5173` 진입:

1. **비인증 진입 → /login 자동 리다이렉트**. URL 에 `?next=/` 부착.
2. **admin 로그인** (`admin` / `admin-sample-1234`) → 홈으로 이동. Sidebar 에 시드 메뉴 트리 표시 (대시보드·ITSM(자식)·ITAM·PMS·공통·시스템 관리(자식 5)).
3. **TopBar** 에 사용자명(`시스템 관리자` 또는 `admin`) + 로그아웃 버튼.
4. **시스템 관리 → 사용자** 진입: 시드 3명 + B4 단계의 `u-phase10` 표시. 등록 다이얼로그 동작.
5. **새로고침**: 토큰 유지·메뉴 트리 자동 복원·user/role/perms 자동 채워짐 (/me 호출).
6. **로그아웃** → /login.
7. **user-sample-1 로그인** → Sidebar 에 시스템 관리 미노출, ITSM 만. `/system/users` 진입 시도 → 403 또는 빈 결과 (백엔드가 차단).
8. **u-phase10 로그인** → 본인 비번 변경 가능 (현재 PATCH `/api/users/{id}/password` 가 본인만 허용).

### 2. 결과 보고서 — `frontend/E2E_REPORT_PHASE10.md`

섹션:
- 환경
- 시나리오 결과 (위 §B/C/D 단계 표)
- 핵심 검증 사실:
  - **No-code + 인증 통합**: 사용자 권한에 따라 메뉴 트리가 동적 (백엔드 필터 + 프런트 동적 렌더).
  - 라우터 가드 + useApiFetch beforeFetch + 401 자동 리다이렉트로 인증 안전망 완비.
  - 시스템 관리 4 페이지 정식 동작 (사용자/부서/역할/메뉴 CRUD).
  - 새로고침 후에도 세션 유지 (localStorage + /me 자동 호출).
- 한계 (다음 phase 범위):
  - UX 메시지 카탈로그 (`UI.error.*` 통일) — phase 11.
  - Playwright 시각 회귀 자동화 — phase 11.
  - 토큰 갱신 자동화 (interceptor refresh) — 별도 ADR.
  - 메뉴 동적 라우터 추가 (`router.addRoute`) — M9 메타 편집기와 함께.
  - WYSIWYG·BPMN — M10·M11.

### 3. 산출물

- `frontend/E2E_REPORT_PHASE10.md` 신규.
- 운영 코드 수정 금지.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 보고서
test -s E2E_REPORT_PHASE10.md
grep -q "ROLE_ADMIN" E2E_REPORT_PHASE10.md
grep -q "시스템 관리" E2E_REPORT_PHASE10.md
grep -q "401\|로그인" E2E_REPORT_PHASE10.md

# 2) 정적·테스트 회귀
pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 검증 절차

1. AC + §B/C/D 시나리오 통과.
2. 아키텍처 체크:
   - 권한별 메뉴 차이 시각 PASS?
   - 라우터 가드·새로고침 시 세션 복원·로그아웃 흐름 매끄러움?
   - 시스템 관리 4 페이지가 CRUD 정상 동작?
   - 시드 사용자(u-phase10 포함) 보존 (다음 phase 사용)?
3. step 3 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "프런트 + 백엔드 동시 부팅 + 로그인 흐름 e2e 시나리오 PASS — 비인증 /login 자동 리다이렉트, admin/IT support/user 로그인 + Sidebar 동적 메뉴 차이, 새로고침 후 세션 자동 복원(/me), 시스템 관리 4 페이지 CRUD 정상. frontend/E2E_REPORT_PHASE10.md 작성. M7(인증·사용자·메뉴) 완료."`
   - 결함 → blocked.

## 금지사항

- 운영 코드 수정 금지.
- 시나리오 종료 후 시드(`u-phase10` 등) 정리 금지.
- 보고서에 실 운영 데이터 금지. 가상 샘플.
- Playwright 자동화 추가 금지 — phase 11.
- 새 라우트·새 endpoint 추가 금지.
- 토큰 갱신 자동화 본 step 에서 구현 금지.
