# Step 0: meta-create-update-endpoints

## 읽어야 할 파일
- `/CLAUDE.md`·`/docs/PRD.md` §5-4·`/docs/ARCHITECTURE.md` §16
- `/docs/ADR.md` ADR-004·ADR-006·ADR-016
- `/backend/src/main/java/com/nkia/itg/meta/controller/MetaController.java`
- `/backend/src/main/java/com/nkia/itg/meta/service/MetaService.java`

## 작업
**메타 신규 생성·본문 갱신·복사 endpoints 정착** (No-code 편집기의 백엔드 기반). 기존 publish/archive/dry-run 위에 CREATE/UPDATE 추가.

### 1. 새 endpoints
- `POST /api/meta` — DRAFT 생성. 본문: PageMetaCreateRequest (id/title/systemType/packageType/groupId/major/minor/metaJson 등). 항상 metaStatus=DRAFT 강제(ADR-006). 권한 `META_PUBLISH` 또는 `META_EDIT`.
- `PUT /api/meta/{id}/body` — DRAFT 의 metaJson 본문 교체. PUBLISHED·DEPRECATED·ARCHIVED 메타는 거부(편집은 DRAFT 만).
- `POST /api/meta/{id}/copy` (기존 유지).
- 검증: `POST /api/meta/dry-run` 으로 사전 검증 후 `POST /api/meta` 호출.

### 2. MetaService 확장
- `create(PageMetaCreateRequest req)` — 검증(MetaValidationService) → save → return PageMetaResponse. group_id 중복 major.minor 시 DataIntegrity.
- `updateBody(String metaId, Map<String,Object> body)` — DRAFT 만. 도메인 메서드 `PageMeta.replaceBody(map)`.
- Entity 도메인 메서드: `replaceBody(Map)` — `metaStatus != DRAFT` 면 IllegalStateException.

### 3. 권한 코드
- `META_EDIT` 신규 추가 (sql/init/14_auth_seed.sql 갱신·신규 SQL or 본 phase 에서 14b_meta_perm.sql).
- ROLE_ADMIN 이 자동 보유. ROLE_META_EDITOR 같은 신규 역할도 추가 가능(선택).

### 4. 테스트
- `MetaService.create_metaStatus_DRAFT_강제`.
- `MetaService.updateBody_DRAFT_아닌_메타_거부`.
- `@WebMvcTest` 권한 분기.

## Acceptance Criteria
```bash
cd backend
test -f src/main/java/com/nkia/itg/meta/dto/PageMetaCreateRequest.java  # 또는 기존 위치
grep -q "PostMapping.*\"\"" src/main/java/com/nkia/itg/meta/controller/MetaController.java || \
  grep -q "@PostMapping$\|@PostMapping(\"\")" src/main/java/com/nkia/itg/meta/controller/MetaController.java
grep -q "updateBody\|PutMapping" src/main/java/com/nkia/itg/meta/controller/MetaController.java
./gradlew test --tests "com.nkia.itg.meta.*"
./gradlew build

cd .. && docker-compose up -d
docker exec -i itg-postgres psql -U itg -d itgdb -c "SELECT count(*) FROM permission WHERE code='META_EDIT';" | grep -q "1"

cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10
ADMIN=$(curl -fsS -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin-sample-1234"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])")
# POST /api/meta DRAFT 생성
curl -fsS -X POST -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"id":"itg-test-v1-1","title":"테스트","systemType":"COMMON","packageType":"PACKAGE","groupId":"itg-test","majorVersion":1,"minorVersion":1,"metaJson":{"api":"/api/test","grid":{"columns":[]},"form":{"layout":"two-column","fields":[]}}}' \
  http://localhost:8080/api/meta \
  | grep -q '"metaStatus":"DRAFT"'
kill %1
```

## 금지사항
- 클라이언트가 metaStatus 를 PUBLISHED 등 다른 값으로 보내도 무시. 항상 DRAFT 강제(ADR-006).
- PUBLISHED·DEPRECATED·ARCHIVED 메타 본문 직접 편집 거부. 새 DRAFT 복사 후 편집.
- DELETE endpoint 추가 금지. archive 만.
- 프런트엔드 수정 금지.
