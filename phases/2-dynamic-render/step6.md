# Step 6: e2e-mock-render

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/docs/ARCHITECTURE.md` — §4 동적 렌더링 흐름 전체
- `/docs/ADR.md` — ADR-004 (No-code), ADR-006 (DRAFT 노출 차단), ADR-007 (그리드 자동 분기)
- `/docs/PRD.md` — §9 M2 마일스톤 (이 phase 가 M2 마무리)
- `/phases/2-dynamic-render/step0~5.md` 산출물 전체
- `/backend/openapi/itg-api-spec.json` — 백엔드 OpenAPI 사양 (참고)

## 작업

이 step 의 목적은 **백엔드 + 프런트엔드 동시 부팅 상태에서 DynamicPage 가 실제 메타로 동작하는지 시나리오 검증하고, `frontend/E2E_REPORT_PHASE2.md` 보고서를 작성하는 것**이다. 실 모듈 데이터(티켓 CRUD 등)는 M3 phase 의 범위.

본 step 은 **운영 코드를 작성하지 않는다**. 검증 보고서·시드 메타·정리 작업만.

### 1. 시드 메타 (PUBLISHED) — 두 건

#### A. `itg-mock-grid` (그리드 시각 검증)

DataTable 로 렌더링되는 작은 그리드:

```sql
INSERT INTO page_meta
  (id, title, system_type, package_type, group_id, major_version, minor_version, meta_status, meta_json)
VALUES
  ('itg-mock-grid-v1-1', 'Mock Grid', 'ITSM', 'PACKAGE', 'itg-mock-grid', 1, 1, 'PUBLISHED', '
   {
     "api": "/api/_mock/grid-rows",
     "grid": {
       "columns": [
         { "field":"id",       "label":"ID",   "type":"text",   "width":160, "pinned":"left" },
         { "field":"title",    "label":"제목", "type":"text",   "flex":1 },
         { "field":"status",   "label":"상태", "type":"status", "width":120 },
         { "field":"assignee", "label":"담당자","type":"text",  "width":140 },
         { "field":"createdAt","label":"등록일","type":"date",   "width":120 }
       ]
     },
     "form": {
       "layout": "two-column",
       "fields": [
         { "name":"title",   "label":"제목",  "type":"text",     "required":true, "span":2 },
         { "name":"category","label":"분류",  "type":"select",   "options":[
             {"value":"BUG","label":"버그"},{"value":"REQ","label":"요청"}
         ] },
         { "name":"priority","label":"우선순위","type":"radio","options":[
             {"value":"LOW","label":"낮음"},{"value":"MEDIUM","label":"보통"},
             {"value":"HIGH","label":"높음"},{"value":"CRITICAL","label":"긴급"}
         ] },
         { "name":"content", "label":"내용",  "type":"textarea", "span":2 }
       ]
     },
     "actions": [
       { "id":"create", "label":"등록", "type":"dialog-form" }
     ]
   }
   '::jsonb);
```

#### B. `itg-mock-aggrid` (AG Grid 트리거 검증)

`inlineEdit: true` 또는 컬럼이 많아 export 가 의미 — AG Grid 강제:

```sql
INSERT INTO page_meta
  (id, title, system_type, package_type, group_id, major_version, minor_version, meta_status, meta_json)
VALUES
  ('itg-mock-aggrid-v1-1', 'Mock AG Grid', 'ITAM', 'PACKAGE', 'itg-mock-aggrid', 1, 1, 'PUBLISHED', '
   {
     "api": "/api/_mock/aggrid-rows",
     "grid": {
       "inlineEdit": true,
       "columns": [
         { "field":"assetNo", "label":"자산번호","type":"text",   "width":140, "pinned":"left" },
         { "field":"name",    "label":"자산명",  "type":"text",   "flex":1 },
         { "field":"category","label":"분류",    "type":"text",   "width":120 },
         { "field":"owner",   "label":"소유자",  "type":"text",   "width":140 },
         { "field":"acquiredAt","label":"취득일","type":"date",   "width":120 }
       ]
     },
     "form": {
       "layout": "two-column",
       "fields": [
         { "name":"assetNo","label":"자산번호","type":"text","required":true },
         { "name":"name",   "label":"자산명",  "type":"text","required":true, "span":2 }
       ]
     }
   }
   '::jsonb);
```

### 2. mock rows (props.rows 전달용)

본 phase 의 DynamicPage 는 `props.rows` 가 제공되면 그것을 우선 사용한다 (step 5). 따라서 backend 의 `/api/_mock/...` 엔드포인트는 **만들지 않는다** (backend 수정 금지).

대신 **검증 페이지** `src/views/_dev/DynamicPageSampler.vue` 를 만들어, mock rows 를 직접 props 로 전달해 DynamicPage 가 잘 동작함을 확인한다:

```vue
<script setup lang="ts">
import { ref } from 'vue';
import DynamicPage from '@/components/dynamic/DynamicPage.vue';

const target = ref<'grid'|'aggrid'|'no-meta'>('grid');

const gridRows = Array.from({ length: 18 }, (_, i) => ({
  id:        `itg-row-${String(i + 1).padStart(3, '0')}`,
  title:     `샘플 항목 ${i + 1}`,
  status:    ['DRAFT','PUBLISHED','DEPRECATED','ARCHIVED'][i % 4]!,
  assignee:  `샘플 사용자 ${i % 5}`,
  createdAt: `2026-05-${String((i % 28) + 1).padStart(2, '0')}`,
}));

const agRows = Array.from({ length: 25 }, (_, i) => ({
  assetNo:    `AST-${String(i + 1).padStart(5, '0')}`,
  name:       `샘플 자산 ${i + 1}`,
  category:   ['서버','노트북','모니터','네트워크'][i % 4]!,
  owner:      `샘플 사용자 ${i % 6}`,
  acquiredAt: `2026-${String((i % 12) + 1).padStart(2, '0')}-15`,
}));
</script>

<template>
  <main class="mx-auto max-w-6xl p-6 space-y-4">
    <h1>Dynamic Page Sampler (Phase 2 E2E)</h1>
    <div class="space-x-3 text-[13px]">
      <label><input type="radio" v-model="target" value="grid"    /> itg-mock-grid (DataTable)</label>
      <label><input type="radio" v-model="target" value="aggrid"  /> itg-mock-aggrid (AG Grid, inlineEdit)</label>
      <label><input type="radio" v-model="target" value="no-meta" /> 미존재 그룹 (notPublished)</label>
    </div>

    <DynamicPage v-if="target === 'grid'"    group-id="itg-mock-grid"    :rows="gridRows" />
    <DynamicPage v-if="target === 'aggrid'"  group-id="itg-mock-aggrid"  :rows="agRows" />
    <DynamicPage v-if="target === 'no-meta'" group-id="itg-mock-missing" />
  </main>
</template>
```

라우트:

```ts
{ path: '/_dev/dynamic-page', component: () => import('@/views/_dev/DynamicPageSampler.vue') },
```

> `itg-mock-aggrid` 의 `inlineEdit: true` 가 AG Grid 로 분기됨을 시각 확인 (실 인라인 편집 동작은 다음 phase).

### 3. E2E 시나리오 실행

```bash
# 1) 인프라
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d

# 2) 백엔드 부팅
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
BE_PID=$!
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'

# 3) 시드 메타 INSERT (위 1번의 두 메타)
docker exec -i itg-postgres psql -U itg -d itgdb <<'SQL'
-- itg-mock-grid (위 sql 본문)
-- itg-mock-aggrid (위 sql 본문)
SQL

# 4) 백엔드 메타 fetch 확인
curl -fsS http://localhost:8080/api/meta/active/itg-mock-grid   | grep -q '"id":"itg-mock-grid-v1-1"'
curl -fsS http://localhost:8080/api/meta/active/itg-mock-aggrid | grep -q '"id":"itg-mock-aggrid-v1-1"'
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/meta/active/itg-mock-missing)" = "404"

# 5) 프런트 부팅
cd ../frontend
pnpm install
pnpm type-check
pnpm lint
pnpm build
pnpm test
pnpm dev &
FE_PID=$!
sleep 6

# 6) SPA 라우트 200
for p in /_dev/dynamic-page /itsm /itam /pms /common /system/meta; do
  test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173$p)" = "200"
done

# 7) 정리
kill $FE_PID
kill $BE_PID
docker exec -i itg-postgres psql -U itg -d itgdb \
  -c "DELETE FROM page_meta WHERE group_id IN ('itg-mock-grid','itg-mock-aggrid');"
```

수동 시각 검증 (브라우저, 위 환경 유지):
- `/_dev/dynamic-page` 진입.
- `itg-mock-grid` 선택: shadcn DataTable 18행. 헤더 sticky·정렬 클릭·페이지네이션 동작. "등록" 버튼 클릭 시 DynamicForm 다이얼로그 모달. 필수 표시 `*`. 저장 클릭 시 콘솔에 warn 로 mock submit 출력.
- `itg-mock-aggrid` 선택: AG Grid 25행, inlineEdit:true → AG Grid 로 분기 확인.
- `itg-mock-missing` 선택: "배포된 메타가 없습니다 (groupId: itg-mock-missing)." 경고 카드.
- `/itsm` 진입: `itg-ticket` 메타가 DB 에 없으므로 notPublished 카드 정상 표시 (실 데이터·메타는 M3).

### 4. 결과 보고서 — `frontend/E2E_REPORT_PHASE2.md`

다음 섹션 포함:

```markdown
# E2E 검증 보고서 — Phase 2: dynamic-render

## 환경
- 검증 일시
- 백엔드 (Spring Boot 3.5 + Java 21 + Postgres 16 Docker)
- 프런트 (Vite 6 + Vue 3 + Tailwind v4 + shadcn/vue + TanStack Table + AG Grid Community 32.x)
- 라우트: /_dev/dynamic-page, /itsm, /itam, /pms, /common, /system/meta

## 시나리오 결과
| # | 단계 | 기대 | 결과 |
|---|------|------|------|
| 1 | 백엔드 + DB 부팅 | UP | PASS |
| 2 | 시드 INSERT (itg-mock-grid, itg-mock-aggrid) | 2건 | PASS |
| 3 | GET /api/meta/active/itg-mock-grid | 200 | PASS |
| 4 | GET /api/meta/active/itg-mock-aggrid | 200 | PASS |
| 5 | GET /api/meta/active/itg-mock-missing | 404 META_NOT_PUBLISHED | PASS |
| 6 | DynamicPageSampler 진입 + 4 모드 동작 | 시각 PASS | PASS |
| 7 | itg-mock-grid → DataTable 18행 정상 렌더 | 헤더/정렬/페이지네이션 | PASS |
| 8 | itg-mock-aggrid (inlineEdit) → AG Grid 분기 | AG Grid 25행 | PASS |
| 9 | itg-mock-missing → notPublished 카드 | 경고 표시 | PASS |
| 10 | 등록 버튼 → DynamicForm 다이얼로그 | 필드 7종 렌더, 검증 동작 | PASS |
| 11 | /itsm·/itam·/pms·/common → notPublished 카드 | 메타 미존재이므로 정상 | PASS |
| 12 | 시드 정리 (DELETE) | 잔여 0 | PASS |

## 핵심 검증 사실
- ADR-004 No-code 약속 이행: 5 모듈 페이지가 별도 Vue 파일 없이 router 의 `meta.groupId` 와 `DynamicPage` 한 컴포넌트로 동적 생성됨.
- ADR-007 그리드 이원화: 메타의 `inlineEdit` 플래그가 AG Grid 분기를 트리거 (rows 수와 무관).
- ADR-006 DRAFT 노출 차단: 미존재 / DRAFT only 그룹에서 `notPublished` 카드만 표시, 그리드·폼 렌더 차단.
- 타입 안전: `asPageMetaBody` type guard 가 깨진 메타에 대해 `MetaBodyShapeError` 던지고 페이지가 명시적 에러 카드 표시.

## 한계 (다음 phase 범위)
- 실 모듈 백엔드 (티켓·자산 CRUD): M3 phase.
- AG Grid 인라인 편집의 실 저장: 다음 phase ADR.
- date/date-range/user-picker/file 의 풀 구현 (달력·사용자 검색·업로드): 다음 phase.
- meta.api 의 실 fetch + 페이지네이션·필터: 다음 phase.

## 산출물
- `frontend/src/components/dynamic/{DynamicPage,DynamicGrid,DynamicForm}.vue`
- `frontend/src/composables/{useGridColumns,useFormSchema}.ts`
- `frontend/src/lib/meta-body.ts` (type guard)
- `frontend/src/types/meta-body.ts`
- `frontend/src/views/_dev/{DataTableSampler,AgGridSampler,DynamicGridSampler,DynamicFormSampler,DynamicPageSampler}.vue`
- `frontend/E2E_REPORT_PHASE2.md` (본 파일)
```

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 검증 페이지 + 라우트
test -f src/views/_dev/DynamicPageSampler.vue
grep -q '/_dev/dynamic-page' src/router/index.ts

# 2) 보고서
test -s E2E_REPORT_PHASE2.md
grep -q "itg-mock-grid"   E2E_REPORT_PHASE2.md
grep -q "itg-mock-aggrid" E2E_REPORT_PHASE2.md

# 3) 정적 + 단위 테스트 회귀
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 4) (시나리오) 백엔드 + 프런트 부팅 후 위 §3 의 cURL/SPA 라우트 검증이 모두 PASS

# 5) 시드 메타 잔존 X
# (시나리오 마지막 DELETE 가 정상 종료했는지 확인)
```

## 검증 절차

1. 위 §3 시나리오와 AC 모두 통과.
2. 아키텍처 체크리스트:
   - DynamicPageSampler 의 4 모드 (grid/aggrid/no-meta) 가 모두 시각 PASS?
   - 5 모듈 페이지가 별도 Vue 파일 없이 router meta + DynamicPage 로 동작?
   - 시드 종료 후 DB 에 mock 메타 0 건?
   - E2E_REPORT_PHASE2.md 가 결과 표·핵심 검증·한계 섹션을 모두 포함?
3. step 6 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "E2E 시나리오 12 단계 PASS — itg-mock-grid(DataTable 18행)·itg-mock-aggrid(AG Grid inlineEdit 25행)·itg-mock-missing(notPublished) 자동 분기 검증. 5 모듈 라우트가 별도 Vue 파일 없이 router meta.groupId + DynamicPage 로 동적 렌더(ADR-004 약속 실코드 이행). frontend/E2E_REPORT_PHASE2.md 작성. M2 마일스톤 완료."`
   - 결함(예: AG Grid 토큰 미적용 시각 결함) → `"status": "blocked"`, `"blocked_reason": "<구체>"` 후 중단.

## 금지사항

- 운영 코드 수정 금지 (DynamicPage/Form/Grid·composable·type guard). 이유: 본 step 은 시각·시나리오 검증. 결함 발견 시 해당 step 으로 되돌린다(blocked).
- backend 의 `/api/_mock/...` 엔드포인트를 추가하지 마라. 이유: backend 수정 금지. mock rows 는 props 로 직접 전달.
- 시나리오 종료 후 mock 메타(`itg-mock-grid`·`itg-mock-aggrid`)를 DB 에 남기지 마라.
- 검증을 JUnit/Spring 테스트로 옮겨 작성하지 마라. 이유: 본 step 의 산출물은 보고서 + 시각 검증. 자동화 e2e 는 별도 phase 의 ADR.
- E2E 보고서에 실 운영 데이터 적지 마라. 모든 예시는 가상 샘플.
- 새 엔드포인트·새 페이지를 추가하지 마라 (검증 페이지 1개 제외). 이유: scope.
