# Step 4: api-client-and-meta-fetch

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "기술 스택" (VueUse 11 `useFetch` 표준), "절대 규칙" (Frontend: API 통신은 `useFetch`)
- `/docs/ARCHITECTURE.md` — §1 전체 구성, §3-4 PageMeta JSON 본문 예시, §7 API 설계 (특히 `/api/meta/active/{groupId}` 404 동작), §8 인증·인가 (`useFetch` `onRequest` 훅에서 토큰 자동 주입)
- `/docs/ADR.md` — ADR-009 (`ApiResponse<T>` 공통 래퍼), ADR-008 (JWT — 이번 phase 는 골격만)
- `/phases/0-meta-backend-m1/index.json` 의 step 5·6 summary — 백엔드 응답 구조 (`PageMetaResponse` 필드명)
- `/backend/openapi/itg-api-spec.json` — 백엔드의 실제 OpenAPI 사양. 응답 형태 검증 시 참고.
- `/phases/1-frontend-foundation/step0~3.md` — frontend 스켈레톤·토큰·shadcn·레이아웃·라우팅

이전 step 까지의 결과로 프런트엔드는 더미 페이지(`/system/meta`) 가 라우팅된 상태. 백엔드는 M1 완료로 `/api/meta/active/{groupId}` 가 동작 중.

## 작업

이 step 의 목적은 **VueUse `useFetch` 기반 API 클라이언트를 만들고, 백엔드의 `PageMetaResponse` 와 형태 일치하는 타입을 정의하며, `usePageMeta(groupId)` composable 로 `/system/meta/{groupId}` 페이지가 실제 백엔드 응답을 표시하도록 만드는 것**이다. 부수적으로 CORS, 404 처리, JWT 헤더 자동 주입(현재는 토큰 없음 — 골격), 그리고 frontend↔backend 동시 부팅 E2E 시나리오를 검증한다.

### 1. 환경 변수 — `frontend/.env`·`frontend/.env.example`

Vite 는 `VITE_` 접두어 환경 변수만 클라이언트로 노출.

`frontend/.env.example` (커밋):

```
VITE_API_BASE_URL=http://localhost:8080
```

`frontend/.env.local` (gitignore, 개발자 PC 한정):

```
VITE_API_BASE_URL=http://localhost:8080
```

> `.env.local` 은 step 0 의 `.gitignore` 에 이미 포함. 운영 호스트 등 민감 정보가 들어가지 않도록 주의 (현재는 localhost 만).

타입 선언 `frontend/src/env.d.ts` (또는 `vite-env.d.ts` 보강):

```ts
/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string;
}
interface ImportMeta {
  readonly env: ImportMetaEnv;
}
```

### 2. 메타 타입 — `src/types/meta.ts`

백엔드 응답과 1:1 일치. 백엔드 `PageMetaResponse` (`backend/src/main/java/com/nkia/itg/meta/dto/PageMetaResponse.java`) 의 필드를 그대로:

```ts
export type SystemType  = 'ITSM' | 'ITAM' | 'PMS' | 'COMMON' | 'SYSTEM';
export type PackageType = 'PACKAGE' | 'CUSTOM';
export type MetaStatus  = 'DRAFT' | 'PUBLISHED' | 'DEPRECATED' | 'ARCHIVED';

/** 메타 본문 — 다음 phase 의 DynamicPage 가 구조화된 타입으로 다룬다. 본 phase 에서는 unknown 으로 다룬다. */
export type PageMetaBody = Record<string, unknown>;

export interface PageMeta {
  id:           string;
  title:        string;
  systemType:   SystemType;
  packageType:  PackageType;
  groupId:      string;
  majorVersion: number;
  minorVersion: number;
  metaStatus:   MetaStatus;
  metaJson:     PageMetaBody;
  active:       boolean;
  createdAt:    string;   // ISO 8601 (LocalDateTime → JSON)
  updatedAt:    string;
}

export interface PageMetaVersion {
  id:           string;
  groupId:      string;
  majorVersion: number;
  minorVersion: number;
  metaStatus:   MetaStatus;
  createdAt:    string;
  updatedAt:    string;
}

/** ADR-009 공통 응답 래퍼와 동일 형태 */
export interface ApiEnvelope<T> {
  success:   boolean;
  data:      T | null;
  message:   string | null;
  errorCode: string | null;
}
```

> `metaJson` 은 `Record<string, unknown>` 로 시작. 다음 phase 에서 `GridMeta`·`FormMeta` 등 세부 타입을 박는다.

### 3. API 클라이언트 — `src/lib/api.ts`

VueUse `useFetch` 의 `createFetch` 로 baseURL + JWT 헤더 골격을 박는다:

```ts
import { createFetch } from '@vueuse/core';

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

/**
 * JWT 토큰 게터. 현재 phase 는 토큰 없음 — 다음 phase 에서 auth store 연결.
 * 반환이 null 이면 Authorization 헤더를 붙이지 않는다.
 */
function readToken(): string | null {
  // TODO(phase-2-auth): useAuthStore 또는 localStorage 에서 토큰 조회
  return null;
}

export const useApiFetch = createFetch({
  baseUrl: baseURL,
  options: {
    onFetchError(ctx) {
      // ApiEnvelope 의 errorCode 를 ctx.error 로 정규화
      const data = ctx.data as { errorCode?: string; message?: string } | null;
      if (data?.errorCode) {
        ctx.error = new Error(`${data.errorCode}: ${data.message ?? ''}`.trim());
      }
      return ctx;
    },
    beforeFetch(ctx) {
      const token = readToken();
      if (token) {
        ctx.options.headers = {
          ...(ctx.options.headers ?? {}),
          Authorization: `Bearer ${token}`,
        };
      }
      return ctx;
    },
  },
  fetchOptions: {
    mode: 'cors',
  },
});
```

> `useApiFetch` 사용 패턴 (이번 phase 의 표준):
> ```ts
> const { data, error, isFetching, statusCode } =
>   useApiFetch('/api/meta/active/itg-ticket').json<ApiEnvelope<PageMeta>>();
> ```
> `statusCode` 가 404 일 때 `error` 가 자동 설정되도록 `onFetchError` 에서 메시지 정규화.

### 4. usePageMeta composable — `src/composables/usePageMeta.ts`

화면 노출용 메타 조회를 위한 단일 진입점.

```ts
import { computed, type Ref } from 'vue';
import { useApiFetch } from '@/lib/api';
import type { ApiEnvelope, PageMeta } from '@/types/meta';

interface UsePageMetaResult {
  /** PUBLISHED 최신 메타. PUBLISHED 가 없으면 null. */
  meta:       Ref<PageMeta | null>;
  /** PUBLISHED 가 없는 경우(404 + META_NOT_PUBLISHED) true */
  notPublished: Ref<boolean>;
  /** 기타 에러 메시지 */
  error:      Ref<string | null>;
  isFetching: Ref<boolean>;
  /** 재조회 */
  execute:    () => Promise<unknown>;
}

export function usePageMeta(groupId: string | Ref<string>): UsePageMetaResult {
  // VueUse useFetch 는 reactive URL 지원
  const url = computed(() => {
    const gid = typeof groupId === 'string' ? groupId : groupId.value;
    return `/api/meta/active/${encodeURIComponent(gid)}`;
  });

  const {
    data, statusCode, error, isFetching, execute,
  } = useApiFetch<ApiEnvelope<PageMeta>>(url, { refetch: true })
    .json<ApiEnvelope<PageMeta>>();

  const meta = computed(() => data.value?.data ?? null);

  const notPublished = computed(() => {
    return statusCode.value === 404
        && data.value?.errorCode === 'META_NOT_PUBLISHED';
  });

  const errorMsg = computed<string | null>(() => {
    if (notPublished.value) return null;
    if (error.value)        return error.value.message ?? '메타 조회 실패';
    return null;
  });

  return {
    meta,
    notPublished,
    error: errorMsg as Ref<string | null>,
    isFetching,
    execute,
  };
}
```

> `usePageMeta('itg-ticket')` 처럼 plain string 또는 ref 둘 다 받는다 — 라우트 파라미터 변경에 자연스럽게 반응.

### 5. `/system/meta/:groupId?` 화면 — `src/pages/system/MetaPage.vue`

step 3 의 더미 페이지를 갱신. groupId 가 URL 파라미터로 들어오면 그 메타를 fetch, 없으면 안내 메시지.

```vue
<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { usePageMeta } from '@/composables/usePageMeta';

const route = useRoute();
const initialId = (route.query.groupId as string | undefined) ?? '';

const groupId = computed(() => (route.query.groupId as string | undefined) ?? '');
const { meta, notPublished, error, isFetching, execute } = usePageMeta(groupId);
</script>

<template>
  <section class="space-y-4">
    <PageHeader />

    <Card>
      <CardHeader><CardTitle>화면 노출용 메타 조회</CardTitle></CardHeader>
      <CardContent class="space-y-3">
        <form
          class="flex gap-2"
          @submit.prevent="() => $router.replace({ query: { groupId: ($refs.input as HTMLInputElement).value } })"
        >
          <Input ref="input" :placeholder="'예: itg-ticket'" :value="initialId" />
          <Button type="submit">조회</Button>
        </form>

        <p v-if="isFetching" class="text-foreground-muted">조회 중...</p>
        <p v-else-if="!groupId" class="text-foreground-muted">groupId 를 입력하라.</p>
        <p v-else-if="notPublished" class="text-warning">
          배포된 버전이 없습니다 (META_NOT_PUBLISHED).
        </p>
        <p v-else-if="error" class="text-danger">{{ error }}</p>
      </CardContent>
    </Card>

    <Card v-if="meta">
      <CardHeader><CardTitle>{{ meta.title }} ({{ meta.id }})</CardTitle></CardHeader>
      <CardContent>
        <dl class="grid grid-cols-2 gap-2 text-[13px]">
          <dt class="text-foreground-muted">systemType</dt><dd>{{ meta.systemType }}</dd>
          <dt class="text-foreground-muted">packageType</dt><dd>{{ meta.packageType }}</dd>
          <dt class="text-foreground-muted">version</dt><dd>v{{ meta.majorVersion }}.{{ meta.minorVersion }}</dd>
          <dt class="text-foreground-muted">metaStatus</dt><dd>{{ meta.metaStatus }}</dd>
        </dl>
        <pre class="mt-3 rounded-md bg-surface-muted p-3 text-[12px] font-mono overflow-auto">
{{ JSON.stringify(meta.metaJson, null, 2) }}</pre>
      </CardContent>
    </Card>
  </section>
</template>
```

라우터에 query 파라미터 사용 (path 파라미터로 안 하는 이유: groupId 가 빈 값일 수 있어서 라우트 매치 까다로움).

### 6. 백엔드 CORS 검증

step 3 의 backend `SecurityConfig` 가 `localhost:5173` 만 허용하도록 설정되어 있는지 확인. 아니면 backend 의 `application(-local).yml` 또는 `SecurityConfig` 를 손대지 말고 **이 phase 의 step 4 안에서 결함을 보고**한다 (수정은 별도 phase·hotfix).

확인용 curl:
```bash
curl -i -H "Origin: http://localhost:5173" \
     -H "Access-Control-Request-Method: GET" \
     -X OPTIONS http://localhost:8080/api/meta/active/itg-ticket
# 기대: Access-Control-Allow-Origin: http://localhost:5173
```

> 만약 CORS 가 막혀 있으면 frontend 가 200 응답을 못 받으므로 시나리오 실패. 이때는 `phases/1-frontend-foundation/index.json` 의 step 4 를 `blocked` 로 표시하고 `blocked_reason` 에 "backend CORS Origin allowlist 누락" 명시 — 사용자가 별도 backend 패치 phase 진행.

### 7. E2E 시나리오

```bash
# 1. 인프라
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d

# 2. 백엔드 부팅
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
BE_PID=$!
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'

# 3. 시드 메타 — DRAFT + PUBLISHED 두 개
docker exec -i itg-postgres psql -U itg -d itgdb <<'SQL'
INSERT INTO page_meta (id, title, system_type, package_type, group_id, major_version, minor_version, meta_status, meta_json)
VALUES ('itg-sample-front-v1-1', 'Front E2E 샘플', 'ITSM', 'PACKAGE', 'itg-sample-front', 1, 1, 'DRAFT', '{"api":"/api/samples"}'::jsonb),
       ('itg-no-publish-v1-1',    'No Publish 샘플', 'ITSM', 'PACKAGE', 'itg-no-publish',    1, 1, 'DRAFT', '{}'::jsonb);
-- DRAFT 만: active 가 404 여야 함

INSERT INTO page_meta (id, title, system_type, package_type, group_id, major_version, minor_version, meta_status, meta_json)
VALUES ('itg-published-v1-1', 'Published 샘플', 'ITSM', 'PACKAGE', 'itg-published', 1, 1, 'PUBLISHED', '{"api":"/api/published-samples"}'::jsonb);
SQL

# 4. 프론트엔드 빌드 + dev 부팅
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend
pnpm install
pnpm type-check
pnpm lint
pnpm build
pnpm dev &
FE_PID=$!
sleep 5

# 5. 시나리오 검증 (curl 로 백엔드, 그리고 페이지 진입 가능성)
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/system/meta?groupId=itg-published)" = "200"
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/system/meta?groupId=itg-no-publish)" = "200"

# 백엔드 직접 호출
curl -fsS http://localhost:8080/api/meta/active/itg-published | grep -q '"id":"itg-published-v1-1"'
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/meta/active/itg-no-publish)" = "404"

# CORS preflight 확인
curl -i -H "Origin: http://localhost:5173" \
     -H "Access-Control-Request-Method: GET" \
     -X OPTIONS http://localhost:8080/api/meta/active/itg-published 2>&1 | grep -i "Access-Control-Allow-Origin"

# 6. 정리
kill $FE_PID
kill $BE_PID
docker exec -i itg-postgres psql -U itg -d itgdb \
  -c "DELETE FROM page_meta WHERE group_id IN ('itg-sample-front','itg-no-publish','itg-published');"
```

수동 검증 (브라우저, 위 환경 유지):
- `http://localhost:5173/system/meta?groupId=itg-published` 진입 → 카드에 "Published 샘플 (itg-published-v1-1)" 표시 + metaStatus="PUBLISHED" + metaJson pre 블록.
- `http://localhost:5173/system/meta?groupId=itg-no-publish` 진입 → "배포된 버전이 없습니다 (META_NOT_PUBLISHED)" 경고 표시.
- 브라우저 DevTools Network 탭에서 CORS 오류 없음.

### 8. 결과 보고서 — `frontend/E2E_REPORT.md`

다음 섹션:
- 환경 (브라우저/Node/pnpm 버전, 백엔드/DB).
- 시나리오 결과 (DRAFT only → 404 / PUBLISHED → 200 / CORS 등).
- 핵심 검증 사실 (DRAFT 노출 차단 클라이언트 사이드 확인, ApiEnvelope 정규화 동작, useFetch onRequest/onFetchError 동작).
- 한계 (JWT 자동 주입은 토큰 없음 — 다음 phase, 다크 모드 토글 없음 등).
- 산출물 (이 파일).

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 의존성/타입
pnpm install
pnpm type-check
pnpm lint
pnpm build

# 2) 정적 파일 존재
test -f src/types/meta.ts
test -f src/lib/api.ts
test -f src/composables/usePageMeta.ts
test -f src/env.d.ts
test -f .env.example
test -f E2E_REPORT.md

# 3) 백엔드 + 프론트 동시 부팅 시나리오 (위 §7 의 전체 흐름)
#    — 위 §7 의 모든 단계가 통과해야 한다 (404 / 200 / CORS 헤더 / 시드 정리)
```

## 검증 절차

1. 위 AC 커맨드와 §7 의 E2E 시나리오를 순서대로 실행한다. 모든 단계가 통과해야 한다.
2. 아키텍처 체크리스트:
   - `useApiFetch` 가 `createFetch` 로 만들어지고 `baseURL`·`beforeFetch`(JWT 헤더 골격)·`onFetchError`(`ApiEnvelope` 에러 정규화) 가 구현되었는가? (CLAUDE.md 절대 규칙 — `useFetch` 표준)
   - `usePageMeta` 가 reactive groupId 를 받고, 404 + `META_NOT_PUBLISHED` 일 때 `notPublished` true 를 반환하는가? (DRAFT 노출 차단 — 클라이언트 사이드 안전망)
   - `types/meta.ts` 의 타입 형태가 백엔드 `PageMetaResponse` 와 1:1 일치하는가? (필드명·옵셔널 여부)
   - 백엔드 CORS 가 `http://localhost:5173` 을 허용하는가? (Access-Control-Allow-Origin 헤더)
   - `metaJson` 타입이 `Record<string, unknown>` 으로 시작하여 `any` 가 아닌가? (CLAUDE.md 절대 규칙)
   - `.env.example` 만 커밋되고 `.env.local` 은 gitignore 되었는가?
3. 결과에 따라 `phases/1-frontend-foundation/index.json` 의 step 4 를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "VueUse useFetch 기반 useApiFetch (baseURL + JWT beforeFetch 골격 + ApiEnvelope onFetchError 정규화) + types/meta.ts (백엔드 PageMetaResponse 1:1) + usePageMeta(groupId) composable (404 META_NOT_PUBLISHED → notPublished 신호) + /system/meta?groupId=... 페이지 카드 + JSON 미리보기. 백엔드 동시 부팅 E2E 시나리오 (DRAFT only → 404, PUBLISHED → 200, CORS preflight 통과) 통과. frontend/E2E_REPORT.md 작성. M2 절반(foundation) 완료."`
   - 결함 발견(예: CORS 미허용) → `"status": "blocked"`, `"blocked_reason": "<구체적 결함 — 어떤 backend 설정 보강 필요>"` 후 중단. 다음 phase 에서 별도 hotfix.
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`

## 금지사항

- `axios`·`ky`·`ofetch` 등 별도 HTTP 라이브러리를 추가하지 마라. 이유: CLAUDE.md 절대 규칙 — VueUse `useFetch` 표준. 일관성.
- API 클라이언트에 토큰·시크릿을 하드코딩하지 마라. 이유: 보안. 환경변수 또는 store 통해.
- `metaJson` 타입을 `any` 또는 구체 타입(`{ grid: ..., form: ... }`)으로 박지 마라. 이유: 본 phase 는 fetch 검증. 메타 본문의 구조화 타이핑은 다음 phase 의 `DynamicPage`/`DynamicForm`/`DynamicGrid` 책임. 지금은 `Record<string, unknown>` 으로 유보.
- 백엔드 코드(`backend/`·`sql/`·`docker-compose.yml`) 를 수정하지 마라. 이유: 결함 발견 시 `blocked` 로 표시하고 별도 phase 에서 처리. 본 phase 의 scope 일관성.
- `/system/meta` 페이지에 데이터 변경(POST/PATCH/DELETE) 기능을 추가하지 마라. 이유: 본 phase 는 조회만. 메타 변경 UI 는 다음 phase 또는 별도 admin phase.
- `console.log` 운영 코드 잔류 금지 (ESLint error).
- `DynamicPage`·`DynamicForm`·`DynamicGrid` 컴포넌트를 만들지 마라. 이유: 다음 phase 의 책임. 이번 step 은 단일 페이지의 메타 fetch + 표시.
- AG Grid Vue3 의존성을 추가하지 마라. 이유: 다음 phase 의 책임.
- 시나리오 종료 후 시드 메타(`itg-sample-front`·`itg-no-publish`·`itg-published`) 를 DB 에 남기지 마라.
- E2E_REPORT 에 실 운영 데이터를 적지 마라. 모든 예시는 가상 샘플(`itg-published`, `샘플`). ADR-011.
