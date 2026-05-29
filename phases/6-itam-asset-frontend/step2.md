# Step 2: history-restore-binding

## 읽어야 할 파일

- `/CLAUDE.md`·`/docs/PRD.md` §5-2 — 이력 복원 (등록 시점의 메타로 화면 복원)
- `/docs/ADR.md` — ADR-004·ADR-006
- `/phases/6-itam-asset-frontend/step0~1.md` — 산출물
- `/frontend/src/components/dynamic/DynamicForm.vue` — meta.form 으로 렌더되는 폼 (props.meta·initialValues·@submit)
- `/frontend/src/lib/meta-body.ts` — `asPageMetaBody` type guard
- `/frontend/src/pages/itam/DetailPage.vue` — 직전 step 산출물

## 작업

이 step 의 목적은 **자산 상세 페이지의 폼이 `pageMetaIdAtRegistration` 메타로 정확히 복원되도록 `DynamicForm` 을 연결하고, 메타 버전 라벨(`v1.1 · DEPRECATED`)을 명시 표시하는 것**이다. **현재 PUBLISHED 메타가 v1-2 더라도 v1-1 메타로 등록된 자산의 상세 폼은 v1-1 의 form.fields 로 그려진다** — M4 의 핵심 약속.

### 1. `DetailPage.vue` 확장 — DynamicForm 으로 복원

step 1 의 메타 JSON pre 블록을 실제 `DynamicForm` 으로 대체.

```vue
<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { useApiFetch } from '@/lib/api';
import { usePageMeta } from '@/composables/usePageMeta';
import { asPageMetaBody, MetaBodyShapeError } from '@/lib/meta-body';
import DynamicForm from '@/components/dynamic/DynamicForm.vue';
import type { ApiEnvelope, MetaStatus } from '@/types/meta';
import type { PageMetaBody } from '@/types/meta-body';

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

const { data: assetEnv, isFetching: isAssetLoading } =
  useApiFetch<ApiEnvelope<AssetResponse>>(
    computed(() => `/api/assets/${assetId.value}`),
    { refetch: true },
  ).json<ApiEnvelope<AssetResponse>>();

const asset = computed<AssetResponse | null>(() => assetEnv.value?.data ?? null);

const metaIdAtReg = computed(() => asset.value?.pageMetaIdAtRegistration ?? '');
const ident = computed(() => ({ metaId: metaIdAtReg.value }));
const { meta: registrationMeta, error: metaError } = usePageMeta(ident);

const body = computed<PageMetaBody | null>(() => {
  const m = registrationMeta.value;
  if (!m) return null;
  try { return asPageMetaBody(m.id, m.metaJson); }
  catch (e) {
    if (e instanceof MetaBodyShapeError) return null;
    throw e;
  }
});

// 폼 초기값 — 자산의 속성을 그대로 폼 field name 으로 매핑
const initialValues = computed<Record<string, unknown>>(() => {
  const a = asset.value;
  if (!a) return {};
  return {
    name:       a.name,
    assetType:  a.assetType,
    category:   a.category,
    model:      a.model,
    serialNo:   a.serialNo,
    assigneeId: a.assigneeId,
    location:   a.location,
    acquiredAt: a.acquiredAt,
    pageGroupId: 'itg-asset',
  };
});

function statusColor(s: MetaStatus | undefined): string {
  if (s === 'PUBLISHED')  return 'text-success';
  if (s === 'DRAFT')      return 'text-warning';
  if (s === 'DEPRECATED') return 'text-neutral';
  if (s === 'ARCHIVED')   return 'text-foreground-subtle';
  return 'text-foreground-muted';
}

function onSubmit(/* values */) {
  // 본 phase 의 detail 폼은 readOnly 의도 (이력 보기). 저장은 별도 phase 의 ADR.
  // toast 등 사용자 피드백은 step 3 e2e 시점에 결정.
}
</script>

<template>
  <section class="space-y-4">
    <PageHeader />

    <div class="flex gap-2">
      <Button variant="outline" @click="router.push('/itam')">← 목록으로</Button>
    </div>

    <p v-if="isAssetLoading" class="text-foreground-muted">자산 조회 중…</p>

    <Card v-if="asset">
      <CardHeader>
        <CardTitle>{{ asset.name }} ({{ asset.assetNo }})</CardTitle>
      </CardHeader>
      <CardContent>
        <dl class="grid grid-cols-2 gap-2 text-[13px]">
          <dt class="text-foreground-muted">상태</dt><dd>{{ asset.status }}</dd>
          <dt class="text-foreground-muted">등록 메타</dt>
          <dd>
            <span class="font-mono text-[12px]">{{ asset.pageMetaIdAtRegistration }}</span>
            <span v-if="registrationMeta"
                  :class="['ml-2 text-[12px]', statusColor(registrationMeta.metaStatus)]">
              v{{ registrationMeta.majorVersion }}.{{ registrationMeta.minorVersion }}
              · {{ registrationMeta.metaStatus }}
            </span>
          </dd>
        </dl>
      </CardContent>
    </Card>

    <Card v-if="body && asset">
      <CardHeader>
        <CardTitle>
          폼 화면 복원 — {{ registrationMeta?.title }}
          <span class="ml-2 text-[12px] font-normal text-foreground-muted">
            (등록 시점 메타로 렌더링)
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <!-- 본 phase 의 폼은 read-only 의도. submit 은 disabled 처럼 처리 (저장은 별도 phase). -->
        <DynamicForm
          :meta="body.form"
          :initial-values="initialValues"
          @submit="onSubmit"
          @cancel="router.push('/itam')"
        />
      </CardContent>
    </Card>

    <p v-else-if="metaError" class="text-danger">{{ metaError }}</p>
  </section>
</template>
```

### 2. 메타 버전 라벨 + 색 매핑

- `PUBLISHED` → `text-success`
- `DRAFT` → `text-warning`
- `DEPRECATED` → `text-neutral`
- `ARCHIVED` → `text-foreground-subtle`

UI_GUIDE 토큰만 사용.

### 3. 폼 제출 동작 (선택)

본 phase 의 상세 폼은 **이력 보기 의도**라 PATCH/PUT 호출은 만들지 않는다. submit 핸들러는 빈 함수 또는 toast info ("이력 보기 모드는 저장 비활성"). 저장 기능 도입은 별도 phase 의 ADR.

### 4. 그리드 → 상세 라우팅 (강한 권장)

`/itam` 그리드 행 클릭 시 `/itam/:id` 로 이동. 현재 `DynamicPage` 의 `DynamicGrid` 가 `@row-click` 을 emit 하므로:

`_DynamicRoute.vue` 만으로는 도메인-중립이지만, `/itam` 진입을 위한 router 수준에서 별도 처리 가능. 가장 단순한 길은 **DynamicPage 의 row-click 이벤트를 부모가 받아 라우터 push** — DynamicPage 의 emits 확장.

```ts
// DynamicPage.vue 의 emits
const emit = defineEmits<{ 'row-click': [row: unknown] }>();
// DynamicGrid 의 row-click 전파
<DynamicGrid :meta="body.grid" :rows="rows" @row-click="(r) => emit('row-click', r)" />
```

`_DynamicRoute.vue`:
```vue
<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
import DynamicPage from '@/components/dynamic/DynamicPage.vue';
const route  = useRoute();
const router = useRouter();
const groupId = String(route.meta.groupId);

function onRowClick(row: unknown) {
  // itam 그룹만 id 기반 상세 라우팅. 다른 모듈은 노옵 (다음 phase 의 ADR).
  if (groupId === 'itg-asset') {
    const r = row as { id?: number };
    if (typeof r.id === 'number') router.push(`/itam/${r.id}`);
  }
}
</script>
<template>
  <DynamicPage :group-id="groupId" @row-click="onRowClick" />
</template>
```

> 도메인 분기가 `_DynamicRoute.vue` 에 들어가는 게 ADR-004 의 No-code 정신에 약간 어긋나지만, **route-meta 에 `detailPath` 같은 옵션을 두는 것이 더 깔끔**. router/index.ts 의 itam 라우트에:

```ts
{ path: 'itam', meta: { title: 'ITAM', groupId: 'itg-asset',
                        detailPath: (id: number) => `/itam/${id}` }, ... }
```

`_DynamicRoute.vue` 에서:
```ts
const detailPathFn = route.meta.detailPath as ((id: number) => string) | undefined;
function onRowClick(row: unknown) {
  if (detailPathFn) {
    const r = row as { id?: number };
    if (typeof r.id === 'number') router.push(detailPathFn(r.id));
  }
}
```

> 다만 `meta.detailPath` 에 함수를 두는 것은 hot-reload 와 SSR 시 직렬화 이슈 가능. 단순 string template 으로 두고 `{id}` 치환:

```ts
{ path: 'itam', meta: { ..., detailUrlTemplate: '/itam/{id}' }, ... }
```

`_DynamicRoute.vue`:
```ts
const detailUrl = (id: number) =>
  String(route.meta.detailUrlTemplate ?? '').replace('{id}', String(id));
```

**권장: 위 string template 방식**. 메타 라우팅이 No-code 정신과 맞고 깔끔.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) DetailPage 에 DynamicForm 통합
grep -q "DynamicForm" src/pages/itam/DetailPage.vue
grep -q "asPageMetaBody" src/pages/itam/DetailPage.vue

# 2) router /itam 라우트에 detailUrlTemplate
grep -q "detailUrlTemplate.*itam" src/router/index.ts

# 3) _DynamicRoute.vue 가 row-click → detailUrlTemplate 처리
grep -q "row-click\|@row-click" src/pages/_DynamicRoute.vue
grep -q "detailUrlTemplate" src/pages/_DynamicRoute.vue

# 4) DynamicPage emits row-click
grep -q "row-click" src/components/dynamic/DynamicPage.vue

# 5) 정적·테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 6) dev 부팅 회귀
pnpm dev &
sleep 6
for p in /itsm /itam /itam/1 /system/meta /_dev/dynamic-page; do
  test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173$p)" = "200"
done
kill %1
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - DetailPage 의 폼이 **등록 메타** 로 렌더링되고 현재 PUBLISHED 와 다를 수 있다는 점이 명시 표시(메타 버전 + status)?
   - DynamicForm 의 initialValues 로 자산 속성이 폼에 prefil 되는가?
   - `_DynamicRoute.vue` 의 row-click 동작이 메타 기반(`detailUrlTemplate`)인가? 도메인 하드코딩(`'itg-asset' ===` 같은 분기) 없음?
   - 상세 폼이 저장 기능 없음(읽기·이력 보기 의도)?
3. step 2 업데이트:
   - 성공 → `"summary": "DetailPage 에 DynamicForm 통합(등록 메타 asPageMetaBody 좁힘 + initialValues 로 자산 속성 prefil + 메타 버전·status 라벨). router itam.meta.detailUrlTemplate='/itam/{id}' + DynamicPage row-click emit + _DynamicRoute 의 row-click 핸들러가 detailUrlTemplate replace → router.push (도메인-중립, ADR-004 정합). 상세 폼은 read-only 의도(저장은 별도 phase)."`

## 금지사항

- DetailPage 에 자산 도메인 외 로직(다른 모듈 분기) 금지.
- `_DynamicRoute.vue` 에 `if (groupId === 'itg-asset')` 같은 도메인 하드코딩 금지. meta-driven (`detailUrlTemplate`) 만.
- 상세 폼 submit 으로 자산을 PATCH 하는 동작 금지 (저장은 별도 phase 의 ADR).
- 메타 버전 라벨에 토큰 외 색 사용 금지.
- DynamicForm 에 read-only 모드 prop 추가 금지 — 본 step 은 onSubmit 을 빈 함수로 둠. read-only 모드 자체는 별도 phase.
- 백엔드 코드 수정 금지.
- 운영 코드 console.log 금지.
