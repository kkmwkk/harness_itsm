#!/usr/bin/env bash
# Phase 9 Step 4 — 인증·권한·메뉴 e2e 시나리오 (15 단계)
# 사전: 14_auth_seed.sql 적용 + 백엔드 bootRun(8080) 기동 완료.
set -u
BASE=http://localhost:8080
PASS=0; FAIL=0
ok()   { echo "PASS  $1"; PASS=$((PASS+1)); }
bad()  { echo "FAIL  $1"; FAIL=$((FAIL+1)); }
chk()  { if [ "$2" = "$3" ]; then ok "$1 (got $2)"; else bad "$1 (expected $3, got $2)"; fi; }

jget() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)"; }

# user-sample-1 비밀번호 해시(시드 원본) — 시나리오 11 이 비번을 바꾸므로 멱등성 위해 재실행 시 복원.
USER_SEED_HASH='$2a$10$iMlrKg8ADhCzgY677ydX/uVvgTQFcLZ/oQp.kBPKMInTdXWCmUBWG'
reset_user_pw() {
  docker exec -i itg-postgres psql -U itg -d itgdb -c \
    "UPDATE user_account SET password_hash='${USER_SEED_HASH}' WHERE username='user-sample-1';" >/dev/null 2>&1
}
reset_user_pw   # 시작 시 복원 (직전 실행이 비번을 바꿨을 수 있음)

# 0) 헬스
H=$(curl -s "$BASE/actuator/health" | jget "['status']")
chk "0) actuator health UP" "$H" "UP"

# 1) 익명 /api/users → 401
C=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/users")
chk "1) 익명 /api/users 401" "$C" "401"

# 2) 잘못된 비번 → 401
C=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}')
chk "2) 잘못된 비번 로그인 401" "$C" "401"

# 3) admin 로그인 → accessToken
ADMIN_TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-sample-1234"}' | jget "['data']['accessToken']" 2>/dev/null)
if [ -n "${ADMIN_TOKEN:-}" ] && [ "$ADMIN_TOKEN" != "None" ]; then ok "3) admin 로그인 accessToken 획득"; else bad "3) admin 로그인"; fi

# 4) /api/auth/me → roles ROLE_ADMIN, perms USER_ADMIN
ME=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/auth/me")
R=$(echo "$ME" | jget "['data']['roles']"); P=$(echo "$ME" | jget "['data']['permissions']")
if echo "$R" | grep -q ROLE_ADMIN && echo "$P" | grep -q USER_ADMIN; then ok "4) me roles/perms (ROLE_ADMIN,USER_ADMIN)"; else bad "4) me roles=$R perms=$P"; fi

# 5) admin /api/users → totalElements >= 3
TE=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/users?page=0&size=20" | jget "['data']['totalElements']")
if [ "${TE:-0}" -ge 3 ] 2>/dev/null; then ok "5) admin /api/users totalElements=$TE (>=3)"; else bad "5) totalElements=$TE"; fi

# 6) user-sample-1 로그인
USER_TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"user-sample-1","password":"user-sample-1234"}' | jget "['data']['accessToken']" 2>/dev/null)
if [ -n "${USER_TOKEN:-}" ] && [ "$USER_TOKEN" != "None" ]; then ok "6) user-sample-1 로그인"; else bad "6) user 로그인"; fi

# 7) user /api/users → 403
C=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $USER_TOKEN" "$BASE/api/users")
chk "7) user /api/users 403" "$C" "403"

# 8) user /api/auth/me → roles ROLE_USER
R=$(curl -s -H "Authorization: Bearer $USER_TOKEN" "$BASE/api/auth/me" | jget "['data']['roles']")
if echo "$R" | grep -q ROLE_USER; then ok "8) user me roles=$R"; else bad "8) user me roles=$R"; fi

# 9) admin /api/menu → 시스템 관리 + 사용자 노출
MENU_ADMIN=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/menu")
LABELS_ADMIN=$(echo "$MENU_ADMIN" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']; out=[]
def walk(ns):
    for n in ns:
        out.append(n['label']); walk(n.get('children',[]))
walk(d); print('|'.join(out))")
if echo "$LABELS_ADMIN" | grep -q '시스템 관리' && echo "$LABELS_ADMIN" | grep -q '사용자'; then
  ok "9) admin 메뉴에 시스템 관리·사용자 노출"; else bad "9) admin labels=$LABELS_ADMIN"; fi

# 10) user /api/menu → 시스템 관리 미노출, ITSM 노출
MENU_USER=$(curl -s -H "Authorization: Bearer $USER_TOKEN" "$BASE/api/menu")
LABELS_USER=$(echo "$MENU_USER" | python3 -c "
import json,sys
d=json.load(sys.stdin)['data']; out=[]
def walk(ns):
    for n in ns:
        out.append(n['label']); walk(n.get('children',[]))
walk(d); print('|'.join(out))")
if ! echo "$LABELS_USER" | grep -q '시스템 관리' && echo "$LABELS_USER" | grep -q 'ITSM'; then
  ok "10) user 메뉴 시스템 관리 미노출 + ITSM 노출"; else bad "10) user labels=$LABELS_USER"; fi

# 11) user 본인(id=3) 비번 변경 → 200
C=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' -d '{"newPassword":"changed-sample-1234"}' \
  "$BASE/api/users/3/password")
chk "11) user 본인 비번 변경 200" "$C" "200"

# 12) user 가 타인(id=1) 비번 변경 → 403
C=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' -d '{"newPassword":"hack-sample-1234"}' \
  "$BASE/api/users/1/password")
chk "12) user 타인 비번 변경 403" "$C" "403"

# 13) refresh token → 새 access token
REFRESH_TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-sample-1234"}' | jget "['data']['refreshToken']")
NEW=$(curl -s -X POST "$BASE/api/auth/refresh" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}" | jget "['data']['accessToken']" 2>/dev/null)
if [ -n "${NEW:-}" ] && [ "$NEW" != "None" ]; then ok "13) refresh → 새 accessToken"; else bad "13) refresh"; fi

# 14) admin /api/tickets → 200
C=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/tickets?page=0&size=5")
chk "14) admin /api/tickets 200" "$C" "200"

# 15) admin /api/meta/active/itg-ticket → 200
C=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/meta/active/itg-ticket")
chk "15) admin /api/meta/active/itg-ticket 200" "$C" "200"

# 시나리오 11 이 바꾼 비번을 시드 원본으로 복원 (다음 phase 가 문서화된 비밀번호로 로그인하도록).
reset_user_pw

echo "----------------------------------------"
echo "RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
