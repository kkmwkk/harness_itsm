# Step 0: data-fetch-integration

## 읽어야 할 파일

- `/CLAUDE.md` — "절대 규칙" (API 통신은 VueUse `useFetch` 표준)
- `/docs/ARCHITECTURE.md` — §4 동적 렌더링 흐름, §7 API 설계
- `/docs/ADR.md` — ADR-004 (No-code), ADR-009 (`ApiResponse<T>`)
- `/phases/2-dynamic-render/step5.md` — `DynamicPage` 의 현재 `props.rows` 우선·`meta.api` fetch 경로의 한계 ("다음 phase 에서 정식 처리")
- `/phases/3-itsm-ticket-backend/index.json` step 2·3 summary — TicketController 8 endpoint·`PageResponse<T>` 형태
- `/frontend/src/components/dynamic/DynamicPage.vue` — 현재 props.rows / fetchedRows 분기
- `/frontend/src/lib/api.ts` — `useApiFetch` (createFetch)
- `/frontend/src/types/meta.ts`·`/frontend/src/types/meta-body.ts` — `PageMeta`·`PageMetaBody`·`ApiEnvelope<T>`
- `/backend/openapi/itg-api-spec.json` — 실 백엔드 응답 형태 (`/api/tickets` GET response = `ApiResponse<PageResponse<TicketSummary>>`)

## 작업

이 step 의 목적은 **`DynamicPage` 가 props.rows 미제공 시 `meta.api` 를 통해 백엔드 데이터를 실제로 fetch 하고, 페이지·정렬·필터·새로고침 인터페이스를 단일 composable 로 노출하는 것**이다. 페이지네이션·정렬 UI 와 그리드 인터랙션 연결은 step 1~2 의 후속 작업과 공유.

### 1. 백엔드 응답 형태 확인

`GET /api/tickets?page=0&size=20` 의 응답:

```json
{
  "success": true,
  "data": {
    "content": [ { "id": 1, "ticketNo": "ITSM-SAMPLE-001", ... }, ... ],
    "totalElements": 5,
    "totalPages": 1,
    "number": 0,
    "size": 20
  },
  "message": null,
  "errorCode": null
}
```

즉 `ApiEnvelope<PageResponse<TicketSummary>>` 형태. 본 step 에서 frontend 의 타입에 `PageResponse<T>` 를 추가한다.

### 2. 타입 보강 — `src/types/meta.ts` 또는 새 파일 `src/types/page.ts`

```ts
export interface PageResponse<T> {
  content:       T[];
  totalElements: number;
  totalPages:    number;
  number:        number;   // page index (0-based)
  size:          number;
}
```

기존 `ApiEnvelope<T>` 와 조합해 `ApiEnvelope<PageResponse<TicketSummary>>` 같은 형태로 사용. 단, **본 phase 의 DynamicPage 는 도메인-중립**이므로 `TicketSummary` 가 아닌 generic `unknown` (또는 `Record<string, unknown>`) 으로 다룬다.

### 3. composable — `src/composables/usePageData.ts`

`meta.api` + 페이지·정렬·필터 파라미터를 받아 `useApiFetch` 호출. 반응형 상태 + 새로고침 함수.

```ts
import { computed, ref, type Ref } from 'vue';
import { useApiFetch } from '@/lib/api';
import type { ApiEnvelope } from '@/types/meta';
import type { PageResponse } from '@/types/page';

export interface DataQuery {
  page?:   number;
  size?:   number;
  sort?:   string;                 // "field,asc" | "field,desc"
  filters?: Record<string, string>;
}

export interface UsePageDataResult<T> {
  rows:           Ref<T[]>;
  page:           Ref<number>;
  totalElements:  Ref<number>;
  totalPages:     Ref<number>;
  size:           Ref<number>;
  isFetching:     Ref<boolean>;
  error:          Ref<string | null>;
  reload:         () => Promise<unknown>;
  setQuery:       (q: DataQuery) => void;
}

/**
 * meta.api 경로로 PageResponse<T> 를 fetch.
 * api 가 비어있거나 null 이면 빈 결과로 동작 (no-op).
 */
export function usePageData<T>(api: Ref<string | null | undefined>): UsePageDataResult<T> {
  const query = ref<DataQuery>({ page: 0, size: 20 });

  const url = computed<string>(() => {
    const a = api.value;
    if (!a) return '';
    const p = new URLSearchParams();
    p.set('page', String(query.value.page ?? 0));
    p.set('size', String(query.value.size ?? 20));
    if (query.value.sort) p.set('sort', query.value.sort);
    for (const [k, v] of Object.entries(query.value.filters ?? {})) {
      if (v !== '' && v !== undefined) p.set(k, v);
    }
    return `${a}?${p.toString()}`;
  });

  const { data, statusCode, error, isFetching, execute } =
    useApiFetch<ApiEnvelope<PageResponse<T>>>(url, { refetch: true })
      .json<ApiEnvelope<PageResponse<T>>>();

  const rows          = computed<T[]>(() => data.value?.data?.content ?? []);
  const page          = computed(() => data.value?.data?.number        ?? 0);
  const totalElements = computed(() => data.value?.data?.totalElements ?? 0);
  const totalPages    = computed(() => data.value?.data?.totalPages    ?? 0);
  const size          = computed(() => data.value?.data?.size          ?? (query.value.size ?? 20));

  const errorMsg = computed<string | null>(() => {
    if (error.value) return error.value.message ?? '데이터 조회 실패';
    if (statusCode.value && statusCode.value >= 400) {
      return data.value?.message ?? `HTTP ${statusCode.value}`;
    }
    return null;
  });

  function setQuery(q: DataQuery) {
    query.value = { ...query.value, ...q };
  }

  return {
    rows: rows as unknown as Ref<T[]>,
    page, totalElements, totalPages, size,
    isFetching,
    error: errorMsg as Ref<string | null>,
    reload: execute,
    setQuery,
  };
}
```

> generic `T` 는 호출자가 알려준다 (예: `TicketSummary`). DynamicPage 자체는 `unknown` 으로 두고, 표시는 DynamicGrid 가 메타 columns 에 맞춰 다룬다.

### 4. `DynamicPage` 통합

기존 phase 2 의 `DynamicPage.vue` 는 `props.rows` 우선, 없으면 `useApiFetch(body.api)` 호출하는 임시 구조. 본 step 에서:

- `props.rows` 가 제공되면 그대로 (호환성 유지).
- `props.rows` 미제공 + `body.api` 존재 → `usePageData(api)` 사용.
- 그리드 인터랙션 → `setQuery({ page, sort })` 호출. **본 step 은 setQuery 호출만 가능하게 노출**하고, 실제 그리드 UI 의 sort/page 이벤트 연결은 step 2 또는 별도 작업.
- 에러 시 카드 메시지 표시 (기존 `metaError`·`bodyError` 와 동일 패턴).

```vue
<script setup lang="ts">
import { computed, watchEffect } from 'vue';
import { usePageData } from '@/composables/usePageData';
/* ... 기존 import ... */

interface Props { groupId: string; rows?: unknown[]; }
const props = defineProps<Props>();

// 기존: usePageMeta, asPageMetaBody, body, bodyError
/* ... */

// 신규: api 가 있고 props.rows 미제공이면 자동 fetch
const api = computed<string | null>(() => (props.rows ? null : (body.value?.api ?? null)));
const {
  rows: fetchedRows, totalElements, page, totalPages, size,
  isFetching: isDataFetching, error: dataError, reload, setQuery,
} = usePageData<Record<string, unknown>>(api);

const rows = computed<unknown[]>(() => props.rows ?? fetchedRows.value ?? []);

// emit 으로 reload·query 변경 노출 (선택)
defineExpose({ reload, setQuery });
</script>
```

> 페이지 전환 UI 는 기존 `DataTable` 의 prev/next 가 클라이언트 사이드 페이징이라 서버 사이드 페이징과 결을 달리한다. **본 step 의 범위는 "데이터 fetch 까지". UI 연결은 step 2 또는 별도.** 단, 만약 페이지가 1을 넘는 시드를 만들고 싶지 않다면 phase 4 내내 size=20 + 단일 페이지로 충분.

### 5. 단위 테스트 — `src/composables/__tests__/usePageData.spec.ts`

Vitest. fetch 는 mock (vi.mock 또는 happy-dom + msw). 단, `useApiFetch` 가 createFetch 의 인스턴스라 mocking 이 복잡. 가장 단순한 길은 **`usePageData` 의 URL 빌드 로직을 별도 함수로 분리하여 그 함수만 테스트**.

```ts
// usePageData.ts 내부에서 export
export function buildUrl(api: string, q: DataQuery): string {
  const p = new URLSearchParams();
  p.set('page', String(q.page ?? 0));
  p.set('size', String(q.size ?? 20));
  if (q.sort) p.set('sort', q.sort);
  for (const [k, v] of Object.entries(q.filters ?? {})) {
    if (v !== '' && v !== undefined) p.set(k, v);
  }
  return `${api}?${p.toString()}`;
}
```

테스트 케이스:
1. `buildUrl_기본_page_0_size_20`.
2. `buildUrl_sort_field_asc_파라미터_포함`.
3. `buildUrl_filters_빈_값_제외`.
4. `buildUrl_filters_여러_개_포함`.
5. `buildUrl_size_50_적용`.

> 통합 동작(`usePageData` 가 reactive URL 로 useFetch 발사) 은 step 3 의 브라우저 e2e 가 검증한다.

### 6. AG Grid 분기 시 페이지네이션 (참고)

`useGridColumns#decideRenderer` 가 AG Grid 를 고를 때, AG Grid 는 자체 client-side row model 을 사용한다. 본 step 은 backend pagination 만 다루고, AG Grid 의 서버 사이드 row model 도입은 별도 ADR (Enterprise 가 필요할 수 있어 미루기).

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 파일 존재
test -f src/composables/usePageData.ts
test -f src/types/page.ts            # 또는 types/meta.ts 에 PageResponse 추가
test -f src/composables/__tests__/usePageData.spec.ts

# 2) DynamicPage 가 usePageData 사용
grep -q "usePageData" src/components/dynamic/DynamicPage.vue

# 3) 정적 + 단위 테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 4) dev 부팅 — 기존 /system/meta·/_dev/dynamic-page 라우트 회귀 없음
pnpm dev &
sleep 6
for p in /_dev/dynamic-page /system/meta; do
  test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173$p)" = "200"
done
kill %1
```

수동 검증 (브라우저, backend + 시드 5건 살아있는 상태):
- `/itsm` 진입 → 백엔드 `/api/tickets?page=0&size=20` 호출 → 시드 5건이 그리드에 표시되어야 한다. (status / priority 셀은 아직 plain 텍스트 — step 1 에서 뱃지화)

## 검증 절차

1. AC 통과.
2. 아키텍처 체크리스트:
   - `usePageData` 가 reactive URL + useApiFetch refetch 패턴인가?
   - `buildUrl` 이 별도 함수로 분리되어 단위 테스트 가능한가?
   - DynamicPage 가 `props.rows` 우선 (phase 2 호환) → 없으면 `usePageData` 자동 fetch?
   - 응답이 `ApiEnvelope<PageResponse<T>>` 일치하고 generic 으로 다뤄지는가?
   - `metaJson` 은 `Record<string, unknown>` 유지 (any 없음)?
3. step 0 업데이트:
   - 성공 → `"summary": "PageResponse<T> 타입 추가. usePageData composable (buildUrl 분리 + reactive URL + useApiFetch refetch + page/sort/filters setQuery + reload). DynamicPage props.rows 우선 + 미제공 시 meta.api 자동 fetch. /itsm 진입 시 백엔드 시드 5건 그리드 표시. 단위 테스트 5 케이스(buildUrl)."`

## 금지사항

- AG Grid 의 서버 사이드 row model 도입 금지. 이유: Enterprise 의존 가능, scope 외.
- `usePageData` 안에서 도메인 타입(`TicketSummary`) 을 직접 사용 금지. generic 유지.
- `props.rows` 우선순위를 깨지 마라. 이유: phase 2 의 `DynamicPageSampler` 호환.
- 상태 전이 UI(PATCH `/status`) 트리거를 만들지 마라. 이유: 본 phase 의 step 0~3 어디서도 다루지 않음.
- 백엔드 코드 수정 금지.
- mock data 를 운영 코드에 박지 마라.
- `console.log` 운영 코드 잔류 금지.
