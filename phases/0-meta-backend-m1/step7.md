# Step 7: e2e-verification

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "핵심 설계 사상" 전체 (DRAFT 노출 금지·복사본 DRAFT 시작·PUBLISHED 전환 시 자동 DEPRECATE)
- `/docs/ARCHITECTURE.md` — §3 메타 모델, §7 API 설계, §9 트랜잭션·동시성, §10 AI 메타 자동 생성 파이프라인
- `/docs/PRD.md` — §5-2 버전 라우팅 (필수 요건 4가지), §9 마일스톤 M1
- `/phases/0-meta-backend-m1/step1.md` ~ `step6.md` — 산출물 전체

이전 step 의 산출물을 모두 신뢰하고, **새 코드를 추가하지 않는다**. 이 step 은 시나리오 검증과 산출물 정리만 한다.

## 작업

이 step 의 목적은 **컨테이너 + DB + Spring Boot 가 함께 떴을 때, M1 의 핵심 시나리오가 모두 동작하는지 End-to-End 로 검증하고, 결과 산출물을 `backend/E2E_REPORT.md` 에 정리하는 것**이다.

### 1. 사전 정리

- 진행 전 `docker-compose down -v` 로 깨끗한 상태에서 시작 (기존 볼륨에 남은 시드 데이터 제거). 단, **사용자가 다른 작업에 동일 컨테이너를 쓰고 있을 수 있으니 down 전 사용자에게 확인을 받는다는 가정** 으로, 이 step 실행 자체가 사용자 명시 트리거(execute.py)에서 시작된다는 점만 신뢰. 그래도 안전 측면에서 `down -v` 대신 `down` (볼륨 보존) 후 재기동을 우선 시도하고, 시드 충돌 시에만 `-v` 추가.

### 2. 검증 시나리오

아래 시나리오를 순서대로 실행한다. 각 단계의 기대 응답을 명시한다.

#### 시나리오 A — `groupId = "itg-sample-e2e"` 기준

1. **컨테이너·앱 기동**
   ```bash
   docker-compose up -d
   cd backend
   SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
   sleep 10
   curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'
   ```

2. **DRAFT 메타 1건 INSERT (시드)** — POST 엔드포인트는 없으므로 직접 SQL.
   ```bash
   docker exec -i itg-postgres psql -U itg -d itgdb <<'SQL'
   INSERT INTO page_meta (id, title, system_type, package_type,
                          group_id, major_version, minor_version,
                          meta_status, meta_json)
   VALUES ('itg-sample-e2e-v1-1', 'E2E 샘플 페이지', 'ITSM', 'PACKAGE',
           'itg-sample-e2e', 1, 1, 'DRAFT', '{"api":"/api/samples"}'::jsonb);
   SQL
   ```

3. **`GET /api/meta/active/itg-sample-e2e` → 404** (배포된 PUBLISHED 없음)
   ```bash
   curl -s -o /tmp/r2.json -w '%{http_code}\n' \
     http://localhost:8080/api/meta/active/itg-sample-e2e
   # 기대: 404, body $.success=false, $.errorCode="META_NOT_PUBLISHED"
   ```

4. **`PATCH /api/meta/itg-sample-e2e-v1-1/publish` → 200**
   ```bash
   curl -s -X PATCH http://localhost:8080/api/meta/itg-sample-e2e-v1-1/publish
   # 기대: 200, $.success=true, $.data.metaStatus="PUBLISHED", $.message="배포되었습니다."
   ```

5. **`GET /api/meta/active/itg-sample-e2e` → 200, v1.1 반환**
   ```bash
   curl -s http://localhost:8080/api/meta/active/itg-sample-e2e
   # 기대: $.data.id="itg-sample-e2e-v1-1", $.data.metaStatus="PUBLISHED"
   ```

6. **`POST /api/meta/itg-sample-e2e-v1-1/copy` → 201, v1.2 DRAFT**
   ```bash
   curl -s -X POST -o /tmp/r6.json -w '%{http_code}\n' \
     http://localhost:8080/api/meta/itg-sample-e2e-v1-1/copy
   # 기대: 201, $.data.id="itg-sample-e2e-v1-2", $.data.metaStatus="DRAFT"
   ```

7. **`GET /api/meta/group/itg-sample-e2e/versions` → 2건**
   ```bash
   curl -s http://localhost:8080/api/meta/group/itg-sample-e2e/versions
   # 기대: $.data 배열 2건. v1-2(DRAFT) 가 먼저(높은 minor desc), v1-1(PUBLISHED) 가 그 다음.
   ```

8. **`PATCH /api/meta/itg-sample-e2e-v1-2/publish` → 200, v1.1 자동 DEPRECATED**
   ```bash
   curl -s -X PATCH http://localhost:8080/api/meta/itg-sample-e2e-v1-2/publish
   # 기대: 200, $.data.id="itg-sample-e2e-v1-2", metaStatus="PUBLISHED"

   curl -s http://localhost:8080/api/meta/itg-sample-e2e-v1-1
   # 기대: 200, $.data.id="itg-sample-e2e-v1-1", metaStatus="DEPRECATED"
   ```

9. **`GET /api/meta/active/itg-sample-e2e` → v1.2 반환**
   ```bash
   curl -s http://localhost:8080/api/meta/active/itg-sample-e2e
   # 기대: $.data.id="itg-sample-e2e-v1-2"
   ```

10. **DRAFT 메타가 active 에 절대 노출되지 않음 확인**
    ```bash
    docker exec -i itg-postgres psql -U itg -d itgdb <<'SQL'
    INSERT INTO page_meta (id, title, system_type, package_type,
                           group_id, major_version, minor_version,
                           meta_status, meta_json)
    VALUES ('itg-sample-e2e-v1-3', 'E2E 샘플 페이지', 'ITSM', 'PACKAGE',
            'itg-sample-e2e', 1, 3, 'DRAFT', '{}'::jsonb);
    SQL
    curl -s http://localhost:8080/api/meta/active/itg-sample-e2e
    # 기대: 여전히 v1-2 (PUBLISHED). DRAFT v1-3 무시.
    ```

11. **카테고리 조회**
    ```bash
    curl -s http://localhost:8080/api/meta/system/ITSM/active
    # 기대: ITSM PUBLISHED 메타 목록에 itg-sample-e2e-v1-2 포함

    curl -s http://localhost:8080/api/meta/package/PACKAGE
    # 기대: PACKAGE 전체. itg-sample-e2e 그룹 메타 모두 포함.
    ```

12. **archive 동작**
    ```bash
    curl -s -X PATCH http://localhost:8080/api/meta/itg-sample-e2e-v1-3/archive
    # 기대: 200, $.data.metaStatus="ARCHIVED"
    ```

13. **OpenAPI 산출물 추출**
    ```bash
    curl -fsS http://localhost:8080/v3/api-docs      > backend/openapi/itg-api-spec.json
    curl -fsS http://localhost:8080/v3/api-docs.yaml > backend/openapi/itg-api-spec.yaml
    ```

14. **앱 종료 + 정리**
    ```bash
    kill %1
    docker exec -i itg-postgres psql -U itg -d itgdb \
      -c "DELETE FROM page_meta WHERE group_id='itg-sample-e2e';"
    ```

### 3. 결과 보고서 — `backend/E2E_REPORT.md`

다음 섹션 포함:

```markdown
# M1 E2E 검증 보고서

## 환경
- 검증 일시: <YYYY-MM-DD HH:MM (KST)>
- Docker 이미지: postgres:16, dpage/pgadmin4:latest
- Spring Boot: 3.3.x, Java: 21, Gradle: 8.x
- profile: local

## 시나리오 결과
| # | 단계 | HTTP | 결과 | 비고 |
|---|------|------|------|------|
| 1 | bootRun + /actuator/health | 200 | PASS | UP 응답 |
| 2 | DRAFT 시드 INSERT | — | PASS | itg-sample-e2e-v1-1 |
| 3 | GET active (배포 없음) | 404 | PASS | errorCode=META_NOT_PUBLISHED |
| 4 | PATCH publish v1-1 | 200 | PASS | metaStatus=PUBLISHED |
| 5 | GET active → v1-1 | 200 | PASS | |
| 6 | POST copy → v1-2 DRAFT | 201 | PASS | |
| 7 | GET group versions | 200 | PASS | 2건 |
| 8 | PATCH publish v1-2 + v1-1 DEPRECATE | 200 | PASS | 트리거+Service 이중 안전망 검증 |
| 9 | GET active → v1-2 | 200 | PASS | |
| 10 | DRAFT v1-3 추가, active 무영향 | 200 | PASS | DRAFT 노출 차단 |
| 11 | system/ITSM/active, package/PACKAGE | 200 | PASS | |
| 12 | archive v1-3 | 200 | PASS | |
| 13 | OpenAPI JSON/YAML 추출 | 200 | PASS | backend/openapi/ |

## 핵심 검증 사실
- ADR-006 자동 DEPRECATE 이중 안전망: Service.publish() 의 deprecatePublished() 호출과 DB trg_auto_deprecate 트리거 모두에서 동일 결과 (검증 8단계).
- PRD §5-2 버전 라우팅 4규칙 모두 충족:
  1. PUBLISHED 최신 1건만 노출 (5,9단계)
  2. PUBLISHED 없으면 미노출 / 404 (3단계)
  3. 새 PUBLISHED 전환 시 기존 자동 DEPRECATED (8단계)
  4. 복사본은 항상 DRAFT (6단계)
- ADR-011 Swagger 민감정보: /v3/api-docs 의 모든 example 가상 샘플 (itg-sample-*, example.com).

## 산출물
- `backend/openapi/itg-api-spec.json` (OpenAPI 3.x JSON)
- `backend/openapi/itg-api-spec.yaml` (OpenAPI 3.x YAML)
```

`backend/openapi/.gitignore` 또는 루트 `.gitignore` 에 `backend/openapi/*` 를 추가할지는 선택:
- **권장**: 산출물은 커밋한다. 이유: API 변경 이력을 git diff 로 추적 가능. M1 시점의 baseline 으로 의미.
- 단, 매 빌드마다 자동 생성을 강제하지 않는다 (이 phase 범위 아님).

### 4. 변경 가능한 코드 범위 (이 step)

- `backend/E2E_REPORT.md` — 새로 작성.
- `backend/openapi/itg-api-spec.json` / `itg-api-spec.yaml` — 새로 작성.
- (선택) 루트 `README.md` 의 "현재 상태" 섹션에 "M1 백엔드 완료, E2E 검증 통과" 한 줄 추가.

이 외의 운영 코드(Service, Controller, Entity, SQL 등) 는 수정하지 않는다. **수정이 필요한 결함이 발견되면 해당 step 의 status 를 error 로 되돌리는 것이 정석** 이지만, execute.py 워크플로우 상 이 step 까지 왔다는 것은 이전 step 들이 완료된 상태이므로:
- 검증 중 결함 발견 시: 결함 내용을 `E2E_REPORT.md` 의 "결함" 섹션에 기록하고, `phases/0-meta-backend-m1/index.json` 의 step 7 을 `"status": "blocked"` + `"blocked_reason": "<구체적 결함>"` 로 표시한 뒤, **이 step 을 중단**한다. 사용자가 어떤 이전 step 으로 되돌릴지 결정.

## Acceptance Criteria

```bash
# 위 시나리오 1~14 단계 전체가 정상 종료해야 한다.
# 핵심 검증 — 각 라인이 모두 PASS 라야 함:
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10

# (a) health
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'

# (b) seed + 시나리오 핵심 4 단계 압축 검증
docker exec -i itg-postgres psql -U itg -d itgdb -c \
  "INSERT INTO page_meta (id, title, system_type, package_type, group_id, major_version, minor_version, meta_status, meta_json) VALUES ('itg-sample-e2e-v1-1','E2E 샘플','ITSM','PACKAGE','itg-sample-e2e',1,1,'DRAFT','{}'::jsonb);"

# active 없음 → 404
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/meta/active/itg-sample-e2e)" = "404"

# publish v1-1 → 200
curl -fsS -X PATCH http://localhost:8080/api/meta/itg-sample-e2e-v1-1/publish > /dev/null

# active → v1-1
curl -fsS http://localhost:8080/api/meta/active/itg-sample-e2e | grep -q '"id":"itg-sample-e2e-v1-1"'

# copy → v1-2 DRAFT
curl -fsS -X POST http://localhost:8080/api/meta/itg-sample-e2e-v1-1/copy | grep -q '"id":"itg-sample-e2e-v1-2"'

# publish v1-2 → v1-1 DEPRECATED 자동
curl -fsS -X PATCH http://localhost:8080/api/meta/itg-sample-e2e-v1-2/publish > /dev/null
curl -fsS http://localhost:8080/api/meta/itg-sample-e2e-v1-1 | grep -q '"metaStatus":"DEPRECATED"'

# OpenAPI 산출
mkdir -p openapi
curl -fsS http://localhost:8080/v3/api-docs      > openapi/itg-api-spec.json
curl -fsS http://localhost:8080/v3/api-docs.yaml > openapi/itg-api-spec.yaml
test -s openapi/itg-api-spec.json && test -s openapi/itg-api-spec.yaml

# 정리
kill %1
docker exec -i itg-postgres psql -U itg -d itgdb \
  -c "DELETE FROM page_meta WHERE group_id='itg-sample-e2e';"

# 보고서 존재
test -s E2E_REPORT.md
```

## 검증 절차

1. 위 AC 커맨드를 순서대로 실행한다. 모든 검증이 통과해야 한다.
2. 아키텍처 체크리스트:
   - PRD §5-2 의 버전 라우팅 4규칙이 모두 시나리오로 통과되었는가?
   - ADR-006 의 자동 DEPRECATE 이중 안전망(Service + DB 트리거)이 시나리오 8단계에서 검증되었는가?
   - `backend/openapi/itg-api-spec.{json,yaml}` 이 비어있지 않고, 9 개 엔드포인트가 모두 포함되어 있는가?
   - `backend/E2E_REPORT.md` 가 작성되었고, 위 결과 표·핵심 검증 사실·산출물 섹션이 모두 포함되어 있는가?
   - 시나리오 시드 메타가 정리(DELETE) 되었는가?
3. 결과에 따라 `phases/0-meta-backend-m1/index.json` 의 step 7 을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "E2E 시나리오 12 단계 모두 PASS (DRAFT 노출 차단·복사본 DRAFT·자동 DEPRECATE 이중 안전망 검증). OpenAPI JSON/YAML 산출. E2E_REPORT.md 작성. M1 마일스톤 완료."`
   - 결함 발견 → `"status": "blocked"`, `"blocked_reason": "<구체적 결함 + 영향받는 step 번호>"` 후 중단.
   - 시도 3회 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`

## 금지사항

- 이 step 에서 운영 코드(Entity, Service, Controller, Repository, SQL, Config) 를 수정하지 마라. 이유: 검증 step 은 검증만. 결함 발견 시 해당 step 으로 되돌린다 (status → blocked).
- 시나리오 종료 후 `itg-sample-e2e` 그룹 시드 메타를 DB 에 남기지 마라. 이유: 후속 phase 의 시나리오 충돌·노이즈.
- `docker-compose down -v` 를 자동 실행하지 마라. 이유: 사용자가 다른 작업에 컨테이너를 쓰고 있을 수 있다. 시드 정리는 SQL `DELETE` 로.
- `backend/openapi/` 산출물을 `.gitignore` 에 추가하지 마라. 이유: M1 시점의 API baseline 으로 의미. 커밋한다.
- 자동 DEPRECATE 검증에서 DB 트리거를 비활성화한 상태로 테스트하지 마라. 이유: 운영 시 트리거가 살아있어야 한다. Service + 트리거 둘 다 동작하는 상태에서 결과를 검증한다.
- 시나리오를 `assertEqual` 식의 통합 테스트 코드로 작성하지 마라 (예: 새 JUnit 클래스). 이유: 이 step 의 산출물은 보고서(`E2E_REPORT.md`) + OpenAPI 사양. JUnit 형태의 추가 자동화는 다음 phase 의 별도 ADR (`E2E-test-strategy`) 로 다룬다.
- 새 엔드포인트(예: POST 메타 생성) 를 추가하지 마라. 이유: M1 범위 아님. 시드는 SQL.
