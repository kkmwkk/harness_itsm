# Step 3: e2e-generate-itg-change

## 읽어야 할 파일

- `/CLAUDE.md`·`/docs/PRD.md` §5-4·§9 M5
- `/docs/ARCHITECTURE.md` §10
- `/docs/META_GENERATION_GUIDE.md` (step 2 산출물) — 7단계 워크플로우
- `/phases/7-ai-meta-generation/step0~2.md` 산출물 전체
- `/backend/openapi/itg-api-spec.json`

## 작업

이 step 의 목적은 **실제 OpenAPI 사양에서 출발하여 ITSM 변경관리 도메인(`itg-change`) 의 PageMeta DRAFT 를 자동 생성·검증·DB 적용·publish·화면 노출까지 e2e 로 통과시키고, `META_GENERATION_REPORT.md` 보고서를 쓰는 것**이다.

> ITSM Change 도메인 백엔드(엔티티/Service/Controller)는 본 phase 의 범위가 **아니다**. 백엔드가 없는 도메인이라도, 메타 자체는 dry-run·INSERT·publish 가 가능. 다만 `meta.api` 호출 시 404 가 날 것이므로, `/system/meta?groupId=itg-change` 페이지에서 메타 카드 표시까지만 e2e 검증.

> 메타 본문의 `api` 필드는 임시로 `/api/changes` (미존재) 로 두고, 보고서에 "백엔드 모듈 추가는 별도 phase" 명시.

### 1. 시나리오 (자동 + 수동)

#### A. 환경
```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
BE_PID=$!
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q UP

# 최신 OpenAPI 추출
curl -fsS http://localhost:8080/v3/api-docs > backend/openapi/itg-api-spec.json
```

#### B. 메타 생성 — `itg-change-v1-1`

Change 도메인 백엔드는 없으므로 ChangeCreateRequest 도 없음. 대신 **ticket 도메인의 TicketCreateRequest·TicketSummary 를 입력으로 빌려와 itg-change 메타를 생성** (도메인 골격이 비슷한 점을 활용 — 가이드에 명시).

```bash
mkdir -p sql/init/_generated
python3 scripts/generate_meta.py \
  --openapi backend/openapi/itg-api-spec.json \
  --request-dto TicketCreateRequest \
  --response-dto TicketSummary \
  --group-id itg-change --title "ITSM 변경 관리" \
  --system-type ITSM --package-type PACKAGE \
  --major 1 --minor 1 --api /api/changes \
  --output sql/init/_generated/itg-change-v1-1.sql > /tmp/itg-change-v1-1.json

# 검증: metaStatus DRAFT, id 패턴
python3 -c "import json; m=json.load(open('/tmp/itg-change-v1-1.json')); \
            assert m['metaStatus']=='DRAFT'; \
            assert m['id']=='itg-change-v1-1'; \
            assert m['systemType']=='ITSM'; \
            assert m['groupId']=='itg-change'"
```

#### C. dry-run 검증
```bash
curl -fsS -X POST http://localhost:8080/api/meta/dry-run \
  -H 'Content-Type: application/json' \
  --data @/tmp/itg-change-v1-1.json \
  | python3 -c "import json,sys; r=json.load(sys.stdin)['data']; \
                 assert r['valid']==True, r"
```

#### D. DB 적용 (DRAFT)
```bash
docker exec -i itg-postgres psql -U itg -d itgdb \
  < sql/init/_generated/itg-change-v1-1.sql

# 확인
docker exec -i itg-postgres psql -U itg -d itgdb \
  -c "SELECT id, meta_status FROM page_meta WHERE id='itg-change-v1-1';" \
  | grep -q "DRAFT"
```

#### E. publish
```bash
curl -fsS -X PATCH http://localhost:8080/api/meta/itg-change-v1-1/publish \
  | grep -q '"metaStatus":"PUBLISHED"'

# /api/meta/active/itg-change 가 200
curl -fsS http://localhost:8080/api/meta/active/itg-change \
  | grep -q '"id":"itg-change-v1-1"'
```

#### F. 프런트 라우트 확인

`/system/meta?groupId=itg-change` 진입 → 메타 카드 + JSON 미리보기 표시. 메타 active 응답이 정상이면 시각 PASS.

> 모듈 라우트(`/change`) 는 router 에 등록 안 됨. `/system/meta` 의 일반 메타 viewer 로 검증.

#### G. 정리 (선택)
```bash
# 시드 메타 보존 (다음 phase 또는 미래 활용 가능)
# 만약 정리하고 싶으면:
# docker exec -i itg-postgres psql -U itg -d itgdb \
#   -c "DELETE FROM page_meta WHERE group_id='itg-change';"
```

### 2. 보고서 — `docs/META_GENERATION_REPORT.md`

다음 섹션:
- 환경
- 입력 (DTO 이름·OpenAPI 경로·CLI 인자)
- 생성된 메타 요약 (form.fields 수·grid.columns 수·actions)
- dry-run 결과
- DB 적용·publish 결과
- 프런트 노출 결과 (`/system/meta?groupId=itg-change`)
- 핵심 검증 사실:
  - **M5 약속 이행**: OpenAPI → CLI → dry-run → INSERT → publish → 화면 노출의 5단계 파이프라인이 백엔드 모듈 없이도 동작 (메타 자체의 자율성). 단, `meta.api` 호출은 백엔드 모듈 부재로 404 — 메타 viewer 로 검증.
  - DRAFT 강제 (`metaStatus`).
  - 검증으로 결함 사전 차단 가능 (예: systemType 누락 → invalid).
- 한계:
  - 백엔드 모듈(Change 엔티티/Service/Controller) 추가는 별도 phase.
  - 라벨·옵션 한글화는 Claude Code 다듬기 단계 필요 (`docs/META_GENERATION_GUIDE.md` §1.4).
- 산출물:
  - `scripts/generate_meta.py`·`scripts/test_generate_meta.py`·`scripts/validate_meta.sh`
  - `backend/src/main/java/com/nkia/itg/meta/service/MetaValidationService.java`
  - `docs/META_GENERATION_GUIDE.md` + `scripts/templates/*`
  - `sql/init/_generated/itg-change-v1-1.sql`
  - 본 보고서

### 3. 변경 범위

- `docs/META_GENERATION_REPORT.md` — 신규.
- `sql/init/_generated/itg-change-v1-1.sql` — generator 결과.
- 운영 코드 수정 금지.

## Acceptance Criteria

```bash
# §A~F 시나리오 모두 통과

# 보고서·산출물
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
test -s docs/META_GENERATION_REPORT.md
grep -q "itg-change-v1-1" docs/META_GENERATION_REPORT.md
grep -q "PRD §9 M5\|M5 마일스톤" docs/META_GENERATION_REPORT.md
test -s sql/init/_generated/itg-change-v1-1.sql

# 메타 active 응답 (시드 보존됨)
docker exec -i itg-postgres psql -U itg -d itgdb \
  -c "SELECT meta_status FROM page_meta WHERE id='itg-change-v1-1';" \
  | grep -q "PUBLISHED"
```

## 검증 절차

1. AC + §A~F 시나리오 통과.
2. 아키텍처 체크:
   - 생성된 메타가 DRAFT → dry-run → DB INSERT → publish 의 5단계를 모두 통과?
   - 시드는 보존 (다음 phase 가 사용 가능)?
   - 백엔드 모듈 없는 도메인에 메타만 동작하는 것이 보고서에 명시?
3. step 3 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "OpenAPI → generate_meta.py → dry-run → INSERT(DRAFT) → publish → /system/meta?groupId=itg-change 메타 viewer 노출의 5단계 파이프라인 e2e PASS. sql/init/_generated/itg-change-v1-1.sql + docs/META_GENERATION_REPORT.md. PRD §9 M5 마일스톤 완료 (Claude Code 자동 메타 생성 파이프라인 안정화). 백엔드 모듈은 별도 phase."`
   - 결함 시 blocked.

## 금지사항

- Change 도메인 backend (엔티티·Service·Controller) 를 만들지 마라. 본 step 은 메타 생성 파이프라인 검증만.
- 운영 코드 수정 금지.
- 시드 메타 정리 금지 (보존).
- 보고서에 실 운영 데이터 금지.
- 자동화 e2e (Playwright) 도입 금지.
- `meta.api=/api/changes` 가 404 인 점을 시도하지 마라 (e2e 가 그것을 검증하려 들면 fail). 메타 viewer (`/system/meta`) 로만 시각 확인.
- 새 phase 시작·새 ADR 작성 금지 (별도 phase 의 책임).
