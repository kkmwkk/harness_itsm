# Step 3: e2e-with-meta-rotation

## 읽어야 할 파일

- `/CLAUDE.md`·`/docs/PRD.md` §5-2·§9 M4
- `/docs/ADR.md` ADR-004·ADR-006·ADR-011
- `/phases/5-itam-asset-backend/step3.md` — backend E2E §13 의 메타 회전 결과 (v1-1 DEPRECATED, v1-2 PUBLISHED)
- `/phases/6-itam-asset-frontend/step0~2.md` — 산출물 전체
- `/backend/E2E_REPORT_PHASE5.md` — 형식 참고

## 작업

이 step 의 목적은 **백엔드 + 프런트 동시 부팅 상태에서 자산 이력 메타 복원 시나리오를 e2e 로 검증하고, `frontend/E2E_REPORT_PHASE6.md` 보고서를 작성하는 것**이다. 운영 코드 변경 없음.

### 1. 사전 환경

- phase 5 의 시드(자산 5건 + 메타 `itg-asset-v1-1` PUBLISHED) + phase 5 §13 의 결과(`itg-asset-v1-2` PUBLISHED, `itg-asset-v1-1` DEPRECATED) 가 DB 에 남아 있는 상태가 기대.
- 만약 phase 5 §13 의 결과가 없다면(컨테이너 재생성·시드만 재적용 등), 본 step 의 시나리오에서 메타 회전을 다시 수행.

### 2. E2E 시나리오

#### A. 환경 기동
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

#### B. 메타 회전 상태 보장
```bash
# 이미 v1-2 PUBLISHED 면 no-op (멱등성). 없으면 만들어 publish.
v2_status=$(curl -s http://localhost:8080/api/meta/itg-asset-v1-2 \
   | python3 -c "import json,sys; \
                  d=json.load(sys.stdin).get('data'); print(d.get('metaStatus','') if d else '')" 2>/dev/null \
   || echo "")
if [ "$v2_status" != "PUBLISHED" ]; then
  # v1-2 가 없으면 복사 후 publish (시드 v1-1 의 copy)
  curl -fsS -X POST http://localhost:8080/api/meta/itg-asset-v1-1/copy >/dev/null 2>&1 || true
  curl -fsS -X PATCH http://localhost:8080/api/meta/itg-asset-v1-2/publish
fi

# 검증: v1-1 DEPRECATED, v1-2 PUBLISHED, active=v1-2
curl -fsS http://localhost:8080/api/meta/itg-asset-v1-1 | grep -q '"metaStatus":"DEPRECATED"'
curl -fsS http://localhost:8080/api/meta/active/itg-asset | grep -q '"id":"itg-asset-v1-2"'
```

#### C. SPA 라우트
```bash
for p in /itam /itam/1 /system/meta; do
  test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173$p)" = "200"
done
```

#### D. 시드 자산 ID 확인
```bash
# 시드 자산(AST-SAMPLE-001) 의 id 를 가져옴
SAMPLE_ID=$(curl -fsS http://localhost:8080/api/assets/by-no/AST-SAMPLE-001 \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['id'])")
test -n "$SAMPLE_ID"

# 등록 메타 확인 — v1-1 (DEPRECATED 라도 보존)
curl -fsS "http://localhost:8080/api/assets/$SAMPLE_ID/registration-meta" \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 assert d['id']=='itg-asset-v1-1', d; \
                 assert d['metaStatus']=='DEPRECATED', d"
```

#### E. 신규 자산 생성 → v1-2 메타로 등록
```bash
NEW_ID=$(curl -fsS -X POST http://localhost:8080/api/assets \
  -H 'Content-Type: application/json' \
  -d '{"name":"Phase6 E2E 자산","assetType":"HARDWARE","model":"SAMPLE-P6","serialNo":"SN-P6","category":"노트북","assigneeId":"assignee-sample-9","location":"본사 4층","acquiredAt":"2026-05-29","pageGroupId":"itg-asset"}' \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['id'])")
test -n "$NEW_ID"
curl -fsS "http://localhost:8080/api/assets/$NEW_ID/registration-meta" \
  | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
                 assert d['id']=='itg-asset-v1-2', d; \
                 assert d['metaStatus']=='PUBLISHED', d  # 신규 자산은 현 PUBLISHED 메타로"
```

> 핵심: **같은 `/itam` 그리드 안에서 v1-1(DEPRECATED) 등록 자산과 v1-2(PUBLISHED) 등록 자산이 공존한다**.

#### F. 브라우저 수동/시각 검증 (보고서에 기록)

`http://localhost:5173/itam` 진입:
1. 그리드: 시드 5건(`AST-SAMPLE-001~005`) + phase 5 E2E 생성분(`AST-00006` 정도) + phase 6 E2E 생성분(`AST-00007` 정도) — 약 7+건.
2. status 컬럼이 뱃지(ACTIVE·STORAGE·RETIRED 등)로 표시 — 단, 자산 status 매핑은 ticket 과 다름. 본 phase 의 뱃지는 `StatusBadge` 의 fallback (중립색 + 원본 value) 으로 충분 (확장은 별도 ADR).
3. 행 클릭 → `/itam/:id` 로 라우팅 (step 2 의 `detailUrlTemplate` 동작).
4. `AST-SAMPLE-001` 상세 진입 → 메타 라벨 `v1.1 · DEPRECATED` (warning/neutral 색). 폼은 v1-1 의 form.fields 로 렌더 (`pageGroupId` 필드 도움말 포함).
5. `Phase6 E2E 자산` (NEW_ID) 상세 진입 → 메타 라벨 `v1.2 · PUBLISHED`. 폼이 v1-2 의 form.fields 로 렌더.
6. v1-1 과 v1-2 의 form.fields 가 동일하면(시드가 그랬다면) 시각 차이 없음 — 라벨만 다름. 그래도 핵심 약속 검증.

> 만약 시각 차이를 더 명확히 보이려면 본 step 의 메타 회전 시 v1-2 의 form.fields 를 살짝 수정 (예: 필드 1개 추가). 그러나 backend 의 `/copy` 가 단순 복사이므로, v1-2 의 metaJson 을 직접 SQL UPDATE 로 패치하는 단계가 필요. **본 step 은 단순화 — 메타 자체 차이는 만들지 않고, "버전 라벨 차이"만 검증**.

### 3. 보고서 — `frontend/E2E_REPORT_PHASE6.md`

섹션:
- 환경
- 메타 회전 상태(v1-1 DEPRECATED + v1-2 PUBLISHED)
- 시나리오 결과 표 (위 §C~F)
- 핵심 검증 사실:
  - **PRD §5-2 활용 사례 이행**: `AST-SAMPLE-*` 등록 자산은 v1-1 메타로 화면 복원, 신규 자산은 v1-2 메타로 화면 렌더 — 같은 그리드 안에서 시점 다른 메타로 그려진 자산 공존.
  - DynamicPage 의 metaId override 가 자산 상세 페이지의 폼 복원에 사용됨.
  - 메타 v1-1 의 metaStatus 가 DEPRECATED 임에도 `/api/meta/{id}` 로 반환 가능 (ADR-006).
  - `_DynamicRoute.vue` 의 detailUrlTemplate 이 메타 기반(도메인 하드코딩 없음).
- 한계 (다음 phase 범위):
  - 자산 상세 폼 저장 (현재 read-only).
  - AG Grid 인라인 편집·상세 페이지의 history timeline UI.
  - 인증 (JWT).
- 산출물.

### 4. 변경 범위

- `frontend/E2E_REPORT_PHASE6.md` — 신규.
- 운영 코드 수정 금지.

## Acceptance Criteria

```bash
# 위 §A~E 시나리오 모두 통과

# 보고서
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend
test -s E2E_REPORT_PHASE6.md
grep -q "itg-asset-v1-1" E2E_REPORT_PHASE6.md
grep -q "itg-asset-v1-2" E2E_REPORT_PHASE6.md
grep -q "PRD §5-2" E2E_REPORT_PHASE6.md
grep -q "DEPRECATED" E2E_REPORT_PHASE6.md

# 정적 회귀
pnpm type-check
pnpm lint
pnpm build
pnpm test
```

수동 검증 (브라우저, 위 §F 1~6) 모두 PASS 라고 보고서에 표기.

## 검증 절차

1. AC + §B~E 자동 검증 + §F 수동 검증 모두 통과.
2. 아키텍처 체크:
   - 메타 회전 상태(v1-1 DEPRECATED, v1-2 PUBLISHED)가 정상?
   - 시드 자산의 등록 메타가 여전히 v1-1?
   - 신규 자산의 등록 메타는 v1-2?
   - `/itam/:id` 상세 진입 → DynamicForm 이 등록 메타로 렌더?
   - 그리드 행 클릭 → 상세 라우팅 동작?
3. step 3 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "백엔드 + 프런트 동시 부팅 + 메타 회전(v1-1 DEPRECATED·v1-2 PUBLISHED) 상태에서 자산 이력 복원 e2e 시나리오 PASS — 시드 자산(AST-SAMPLE-*) 의 /registration-meta 는 v1-1 보존, 신규 자산은 v1-2 로 등록. /itam/:id 상세 페이지가 DynamicForm 으로 등록 메타에 따라 화면 복원(v1.1·DEPRECATED / v1.2·PUBLISHED 버전 라벨). frontend/E2E_REPORT_PHASE6.md 작성. PRD §5-2 활용 사례·M4 마일스톤 완료."`
   - 결함 → blocked.

## 금지사항

- 운영 코드 수정 금지. 결함 시 step 0~2 로 되돌린다.
- 메타 회전 후 시드(v1-1·v1-2·AST-SAMPLE-*) 정리 금지.
- 자동화 e2e(Playwright) 도입 금지.
- 보고서에 실 운영 데이터 금지.
- 새 라우트·새 엔드포인트 추가 금지.
- v1-1 을 ARCHIVED 로 만들지 마라 (DEPRECATED 자연 전이 유지).
- 사용자 모듈·인증 시나리오 추가 금지.
