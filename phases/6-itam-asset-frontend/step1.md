# Step 1: asset-detail-page

## 읽어야 할 파일

- `/CLAUDE.md` — 절대 규칙
- `/docs/UI_GUIDE.md` — §5-3 카드 / §6-2 페이지 골격
- `/phases/5-itam-asset-backend/index.json` step 2 summary — `/api/assets/{id}` + `/registration-meta` endpoints
- `/phases/6-itam-asset-frontend/step0.md` — usePageMeta metaId 모드
- `/frontend/src/pages/_DynamicRoute.vue` — 현재 동적 라우트 패턴
- `/frontend/src/composables/usePageData.ts` — 데이터 fetch helper (단건도 비슷)

## 작업

이 step 의 목적은 **`/itam/:id` 자산 상세 라우트를 추가하고, 자산 단건 + 등록 메타(`pageMetaIdAtRegistration`)를 fetch 해 폼 화면을 표시하는 것**이다. 이력 복원의 실 동작은 step 2.

### 1. 라우트 추가 — `src/router/index.ts`

```ts
{ path: 'itam/:id(\\d+)', name: 'itam-detail',
  component: () => import('@/pages/itam/DetailPage.vue'),
  meta: { title: '자산 상세' } },
```

`(\\d+)` 패턴으로 숫자만 허용 (자산 PK는 Long). non-숫자 path 는 다른 라우트로 fall-through.

### 2. `src/pages/itam/DetailPage.vue` — 자산 단건 + 메타 카드

```vue
<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { useApiFetch } from '@/lib/api';
import { usePageMeta } from '@/composables/usePageMeta';
import type { ApiEnvelope } from '@/types/meta';

interface AssetResponse {
  id: number; assetNo: string; name: string;
  assetType: string; status: string;
  model?: string; serialNo?: string; category?: string;
  assigneeId?: string; location?: string;
  acquiredAt?: string; disposedAt?: string;
  pageMetaIdAtRegistration: string;
  createdAt: string; updatedAt: string;
}

const route   = useRoute();
const router  = useRouter();
const assetId = computed(() => String(route.params.id));

// 1) 자산 단건 fetch
const { data: assetEnv, isFetching: isAssetLoading, error: assetError } =
  useApiFetch<ApiEnvelope<AssetResponse>>(
    computed(() => `/api/assets/${assetId.value}`),
    { refetch: true },
  ).json<ApiEnvelope<AssetResponse>>();

const asset = computed<AssetResponse | null>(() => assetEnv.value?.data ?? null);

// 2) 등록 시점 메타 fetch (metaId override 사용 — step 0 의 metaId 모드)
//    asset 이 로드된 뒤에 metaId 결정
const metaIdAtReg = computed(() => asset.value?.pageMetaIdAtRegistration ?? '');
const ident = computed(() =>
  metaIdAtReg.value ? { metaId: metaIdAtReg.value } : { metaId: '' });
const { meta: registrationMeta, error: metaError, isFetching: isMetaLoading } = usePageMeta(ident);
</script>

<template>
  <section class="space-y-4">
    <PageHeader />

    <div class="flex gap-2">
      <Button variant="outline" @click="router.push('/itam')">← 목록으로</Button>
    </div>

    <p v-if="isAssetLoading" class="text-foreground-muted">자산 조회 중…</p>
    <Card v-else-if="assetError" class="border-danger">
      <CardContent class="py-6 text-danger">{{ assetError.message ?? '조회 실패' }}</CardContent>
    </Card>

    <Card v-else-if="asset">
      <CardHeader>
        <CardTitle>{{ asset.name }} ({{ asset.assetNo }})</CardTitle>
      </CardHeader>
      <CardContent>
        <dl class="grid grid-cols-2 gap-2 text-[13px]">
          <dt class="text-foreground-muted">유형</dt><dd>{{ asset.assetType }}</dd>
          <dt class="text-foreground-muted">상태</dt><dd>{{ asset.status }}</dd>
          <dt class="text-foreground-muted">모델</dt><dd>{{ asset.model ?? '-' }}</dd>
          <dt class="text-foreground-muted">시리얼</dt><dd>{{ asset.serialNo ?? '-' }}</dd>
          <dt class="text-foreground-muted">분류</dt><dd>{{ asset.category ?? '-' }}</dd>
          <dt class="text-foreground-muted">소유자</dt><dd>{{ asset.assigneeId ?? '-' }}</dd>
          <dt class="text-foreground-muted">위치</dt><dd>{{ asset.location ?? '-' }}</dd>
          <dt class="text-foreground-muted">취득일</dt><dd>{{ asset.acquiredAt ?? '-' }}</dd>
          <dt class="text-foreground-muted">폐기일</dt><dd>{{ asset.disposedAt ?? '-' }}</dd>
          <dt class="text-foreground-muted">등록 메타</dt>
          <dd class="font-mono text-[12px]">{{ asset.pageMetaIdAtRegistration }}</dd>
        </dl>
      </CardContent>
    </Card>

    <!-- 등록 메타 카드: 어떤 상태든 표시 (step 2 에서 폼 복원으로 발전) -->
    <Card v-if="registrationMeta">
      <CardHeader>
        <CardTitle>
          등록 메타 — {{ registrationMeta.title }}
          <span class="text-[12px] font-normal text-foreground-muted">
            v{{ registrationMeta.majorVersion }}.{{ registrationMeta.minorVersion }}
            · {{ registrationMeta.metaStatus }}
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <pre class="rounded-md bg-surface-muted p-3 text-[12px] font-mono overflow-auto">
{{ JSON.stringify(registrationMeta.metaJson, null, 2) }}</pre>
      </CardContent>
    </Card>

    <p v-else-if="metaError" class="text-danger">{{ metaError }}</p>
  </section>
</template>
```

### 3. 그리드 → 상세 라우팅 (선택, 다음 단계 ADR)

`/itam` 그리드 행 클릭 시 `/itam/:id` 로 이동. 기존 `DynamicPage` 의 `DynamicGrid` 가 `@row-click` emit 을 제공하므로 후속 step 에서 wiring 가능. **본 step 은 라우트만 존재하면 OK** — URL 직접 진입(`/itam/19`) 으로 검증.

### 4. 단위 테스트

라우트 등록 + 컴포넌트는 통합 (e2e step 3) 에서 시각 검증. 본 step 은 단위 테스트 추가 없음 — `usePageMeta` 의 metaId 모드는 step 0 에서 이미 테스트.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 파일·라우트
test -f src/pages/itam/DetailPage.vue
grep -q "itam/:id" src/router/index.ts

# 2) 정적 + 테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 3) dev 부팅 — 자산 시드 5건 존재 가정
pnpm dev &
sleep 6
# 시드 자산 1번 (id 정확치 모름) → 모든 정수 id 라우트가 200 응답
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/itam/1)" = "200"
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/itam/99999)" = "200"   # 미존재도 SPA 200, 안에서 에러 카드
kill %1
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - `/itam/:id(\\d+)` 패턴이 숫자만 매치 (다른 itam 경로와 충돌 없음)?
   - DetailPage 가 자산 단건 + 등록 메타 두 fetch 를 모두 진행?
   - `usePageMeta` 의 metaId 모드를 사용?
   - 에러 카드 표시(자산 없거나 메타 없을 때)?
3. step 1 업데이트:
   - 성공 → `"summary": "pages/itam/DetailPage.vue (자산 단건 useApiFetch + usePageMeta metaId 모드로 등록 메타 fetch + dl 형태 속성 표시 + 메타 JSON 미리보기) + router /itam/:id(\\d+) 라우트. 백엔드 단건 200, 미존재 자산도 SPA 200(에러 카드)."`

## 금지사항

- `/itam/:id` 가 자산 외 다른 path(예: `/itam/new`) 와 충돌하지 않게 — `(\\d+)` 패턴 유지.
- 자산-도메인 특화 로직을 DynamicPage 에 박지 마라 (DynamicPage 는 일반 도메인-중립 컴포넌트). 자산 상세는 별도 `DetailPage.vue` 에 둠.
- 자산 폼 수정·상태 전이 UI 추가 금지 — 본 step 은 조회만. 폼 복원은 step 2.
- 사용자 검색 API 호출 금지.
- 백엔드 코드 수정 금지.
- 운영 코드 console.log 금지.
