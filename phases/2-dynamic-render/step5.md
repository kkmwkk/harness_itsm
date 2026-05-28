# Step 5: dynamic-page-assembly

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/docs/ARCHITECTURE.md` — §4 동적 렌더링 흐름 (DynamicPage 가 usePageMeta → useGridColumns/useFormSchema → DynamicGrid/DynamicForm 조합)
- `/docs/ADR.md` — ADR-004 (No-code: 신규 화면 = Vue 파일 작성 없음, `<DynamicPage group-id="..." />`)
- `/phases/2-dynamic-render/step0~4.md` — 타입·DataTable·AG Grid 어댑터·DynamicGrid·DynamicForm
- `/frontend/src/composables/usePageMeta.ts` (phase 1 step 4) — 메타 fetch
- `/frontend/src/pages/{itsm,itam,pms,common}/IndexPage.vue` 와 `/frontend/src/pages/system/MetaPage.vue` — 현재 더미

## 작업

이 step 의 목적은 **`DynamicPage` 컴포넌트를 만들어 메타 한 건으로 화면을 자동 구성하고, phase 1 의 더미 페이지들을 `<DynamicPage :group-id="..." />` 로 교체하여 ADR-004 의 No-code 약속을 실제 코드로 이행하는 것**이다.

### 1. 새 컴포넌트 — `src/components/dynamic/DynamicPage.vue`

Props:
- `groupId: string`

내부 동작:
1. `usePageMeta(groupId)` 로 백엔드의 PUBLISHED 최신 메타 fetch.
2. `notPublished` 일 때 안내 + (로컬 환경에서) 메타 미존재 라벨.
3. `error` 일 때 에러 카드.
4. 성공 시 `asPageMetaBody(meta.id, meta.metaJson)` 으로 강타입 좁히기.
5. 좁히기 실패(`MetaBodyShapeError`) 시 별도 에러 카드 (개발자에게 의미 있는 메시지).
6. 좁히기 성공:
   - 데이터 fetch: `meta.api` 가 가리키는 엔드포인트 호출하여 `rows` 획득.
     - 본 phase 는 mock 데이터로 검증되므로, **`rows` 를 prop 으로 받을 수도 있게 한다** (다음 phase 에서 정식 fetch + 페이지네이션 도입). 시그니처:
       ```ts
       interface Props { groupId: string; rows?: unknown[]; }
       ```
       `rows` 가 제공되면 그것을 사용, 미제공이면 `meta.api` 로 GET 시도. fetch 결과 형태는 `ApiEnvelope<unknown[]>` 가정.
   - PageHeader 표시(`meta.title` + 버전 라벨).
   - 본문에 DynamicGrid (있을 때) + 액션 버튼 (메타 `actions` 중 `type='dialog-form'` 이면 DynamicForm 모달, `navigate` 면 라우터 push 등).

스켈레톤:

```vue
<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import { usePageMeta } from '@/composables/usePageMeta';
import { asPageMetaBody, MetaBodyShapeError } from '@/lib/meta-body';
import { useApiFetch } from '@/lib/api';
import DynamicGrid from './DynamicGrid.vue';
import DynamicForm from './DynamicForm.vue';
import type { PageMetaBody, ActionMeta } from '@/types/meta-body';
import type { ApiEnvelope } from '@/types/meta';

interface Props { groupId: string; rows?: unknown[]; }
const props = defineProps<Props>();

const groupRef = computed(() => props.groupId);
const { meta, notPublished, error: metaError, isFetching: isMetaFetching } = usePageMeta(groupRef);

const body         = ref<PageMetaBody | null>(null);
const bodyError    = ref<string | null>(null);

watchEffect(() => {
  body.value      = null;
  bodyError.value = null;
  if (!meta.value) return;
  try {
    body.value = asPageMetaBody(meta.value.id, meta.value.metaJson);
  } catch (e) {
    if (e instanceof MetaBodyShapeError) bodyError.value = e.message;
    else throw e;
  }
});

// 데이터 fetch (props.rows 미제공 시)
const fetchedRows = ref<unknown[] | null>(null);
watchEffect(() => {
  fetchedRows.value = null;
  if (!body.value || props.rows) return;
  const { data } = useApiFetch<ApiEnvelope<unknown[]>>(body.value.api).json<ApiEnvelope<unknown[]>>();
  // 단순 동기 watcher; useFetch 의 reactive 결과는 짧은 지연 후 채워짐.
  // 본 phase 는 prop.rows 로 검증하므로 이 경로는 다음 phase 에서 정식 처리.
  if (data.value?.data) fetchedRows.value = data.value.data;
});

const rows = computed<unknown[]>(() => props.rows ?? fetchedRows.value ?? []);

// dialog-form 액션
const dialogOpen     = ref(false);
const dialogFormMeta = computed(() => body.value?.form ?? null);

function onAction(a: ActionMeta) {
  if (a.type === 'dialog-form') dialogOpen.value = true;
  // navigate / export / custom 은 다음 phase
}

function onFormSubmit(values: Record<string, unknown>) {
  // 본 phase 는 mock — POST 호출은 다음 phase 의 책임
  // eslint-disable-next-line no-console
  console.warn('[DynamicPage] form submit (mock)', values);
  dialogOpen.value = false;
}
</script>

<template>
  <section class="space-y-4">
    <PageHeader>
      <template #actions v-if="body?.actions?.length">
        <div class="flex gap-2">
          <Button
            v-for="a in body.actions"
            :key="a.id"
            :variant="a.type === 'dialog-form' ? 'default' : 'outline'"
            @click="onAction(a)"
          >{{ a.label }}</Button>
        </div>
      </template>
    </PageHeader>

    <p v-if="isMetaFetching" class="text-foreground-muted">조회 중...</p>
    <Card v-else-if="notPublished">
      <CardContent class="py-6">
        <p class="text-warning">배포된 메타가 없습니다 (groupId: {{ groupId }}).</p>
      </CardContent>
    </Card>
    <Card v-else-if="metaError">
      <CardContent class="py-6">
        <p class="text-danger">{{ metaError }}</p>
      </CardContent>
    </Card>
    <Card v-else-if="bodyError">
      <CardContent class="py-6">
        <p class="text-danger">메타 본문이 손상되었습니다. {{ bodyError }}</p>
      </CardContent>
    </Card>

    <template v-else-if="body">
      <DynamicGrid :meta="body.grid" :rows="rows" />

      <Dialog v-model:open="dialogOpen">
        <DialogContent>
          <DialogHeader><DialogTitle>{{ meta?.title }}</DialogTitle></DialogHeader>
          <DynamicForm
            v-if="dialogFormMeta"
            :meta="dialogFormMeta"
            @submit="onFormSubmit"
            @cancel="dialogOpen = false"
          />
        </DialogContent>
      </Dialog>
    </template>
  </section>
</template>
```

> `PageHeader` 의 `#actions` slot 은 phase 1 step 3 에서 정의됨. 정상 동작 확인.

> `useApiFetch` 의 직접 호출이 watchEffect 안에 있으면 부수효과가 두 번 일어날 수 있음 — VueUse 의 useFetch 가 사용 시점에 fetch 를 발사한다. 본 phase 는 prop.rows 우선 검증 + 보고서에 "props.rows 경로만 검증, fetch 경로는 다음 phase 의 책임" 명시.

### 2. 더미 페이지 교체

phase 1 step 3 의 더미 페이지들을 `<DynamicPage>` 로 교체. 각 페이지는 단 한 줄:

`src/pages/itsm/IndexPage.vue`:
```vue
<script setup lang="ts">
import DynamicPage from '@/components/dynamic/DynamicPage.vue';
</script>
<template>
  <DynamicPage group-id="itg-ticket" />
</template>
```

같은 패턴:
- `itam/IndexPage.vue` → `group-id="itg-asset"`
- `pms/IndexPage.vue` → `group-id="itg-project"`
- `common/IndexPage.vue` → `group-id="itg-code"`
- `system/MetaPage.vue` → 기존 query-기반 페이지 유지 (메타 관리용 UI). DynamicPage 로 교체 X.

> `system/meta` 페이지는 **메타 자체를 다루는** 페이지라 DynamicPage 로 자동 생성하는 의미가 약함 (재귀적). 그대로 유지.

> `HomePage.vue` 도 그대로 유지 (5 모듈 카드 그리드 — 정적 콘텐츠).

### 3. 라우트의 `group-id` 외부화 (선택, 권장)

각 더미 페이지에 group-id 를 하드코딩하는 것은 ADR-004 의 정신과 맞지 않는다. 라우트 정의에서 메타 매핑을 받아 단일 `DynamicPage` 컴포넌트가 모두 처리하도록 한 단계 더 추상화:

`src/router/index.ts`:

```ts
{
  path: 'itsm',  name: 'itsm',
  component: () => import('@/pages/_DynamicRoute.vue'),
  meta: { title: 'ITSM', groupId: 'itg-ticket' },
},
{ path: 'itam', meta: { title: 'ITAM', groupId: 'itg-asset' }, component: () => import('@/pages/_DynamicRoute.vue') },
/* ... */
```

`src/pages/_DynamicRoute.vue`:

```vue
<script setup lang="ts">
import { useRoute } from 'vue-router';
import DynamicPage from '@/components/dynamic/DynamicPage.vue';
const route = useRoute();
const groupId = String(route.meta.groupId);
</script>
<template>
  <DynamicPage :group-id="groupId" />
</template>
```

이렇게 하면 새 모듈 추가 = 라우트 한 줄 + 메타 DB INSERT — 진정한 No-code. **권장 방향** 으로 채택한다. (단, `itsm/IndexPage.vue` 등 phase 1 의 더미 파일은 그대로 두되 더 이상 라우터에서 참조하지 않게 할지, 삭제할지 결정. 본 step 에서는 **삭제** — 사용처 없는 파일은 남기지 않는다.)

### 4. (선택) DynamicPage 단위 테스트

vue-test-utils 또는 Vitest + happy-dom 으로 컴포넌트 테스트가 가능하지만, 본 step 은 시각 통합 검증을 e2e step (6) 에서 다루므로 **단위 테스트는 도입하지 않는다** (scope 제어). useFormSchema·useGridColumns 의 단위 테스트로 핵심 로직은 이미 커버.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 파일
test -f src/components/dynamic/DynamicPage.vue
test -f src/pages/_DynamicRoute.vue
# 더미 페이지는 라우터에서 미참조 또는 삭제
! grep -q "import.*pages/itsm/IndexPage" src/router/index.ts || exit 1
! grep -q "import.*pages/itam/IndexPage" src/router/index.ts || exit 1

# 2) 라우트가 meta.groupId 로 모듈을 매핑
grep -q "groupId.*itg-ticket"  src/router/index.ts
grep -q "groupId.*itg-asset"   src/router/index.ts
grep -q "groupId.*itg-project" src/router/index.ts
grep -q "groupId.*itg-code"    src/router/index.ts

# 3) 정적 + 테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 4) dev 부팅 — 5 라우트 200 (메타가 없어도 'notPublished' 카드만 표시)
pnpm dev &
sleep 6
for p in / /itsm /itam /pms /common /system/meta; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:5173$p")
  test "$code" = "200" || { echo "FAIL $p ($code)"; exit 1; }
done
kill %1
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크리스트:
   - `DynamicPage` 가 fetch + 좁히기 + DynamicGrid·DynamicForm 조합 + dialog-form 액션을 모두 한 컴포넌트에서 처리하는가?
   - `MetaBodyShapeError` 가 발생해도 페이지 전체가 깨지지 않고 명시적 에러 카드만 표시되는가?
   - 라우트가 meta.groupId 를 통해 단일 `_DynamicRoute.vue` 로 매핑되어 ADR-004 의 No-code 약속을 충족하는가?
   - phase 1 의 더미 페이지(`itsm/IndexPage.vue` 등) 가 router 에서 참조되지 않거나 삭제되었는가?
   - HomePage·system/MetaPage 는 의도적으로 유지(정적·메타-관리)되었는가?
3. step 5 업데이트:
   - 성공 → `"summary": "DynamicPage.vue (usePageMeta + asPageMetaBody 좁히기 + DynamicGrid + DynamicForm dialog + action 핸들러) + pages/_DynamicRoute.vue + router meta.groupId 매핑(itsm=itg-ticket, itam=itg-asset, pms=itg-project, common=itg-code). phase 1 더미 페이지 제거. ADR-004 No-code 약속 실코드 이행."`

## 금지사항

- 모듈별 별도 Vue 파일(`TicketPage.vue`·`AssetPage.vue`)을 만들지 마라. ADR-004 — DynamicPage 로 메타에서 자동 생성.
- DynamicPage 가 메타 본문 형태를 가정하고 `as` 강제 캐스트하지 마라. `asPageMetaBody` type guard 사용.
- form submit·grid 인라인 편집의 실 API 호출(POST·PATCH) 을 이 step 에서 구현하지 마라. 다음 phase 의 ADR. 본 step 은 mock submit (console.warn 또는 emit).
- system/MetaPage 를 DynamicPage 로 교체하지 마라. 이유: 메타 관리 UI 는 메타 자체를 다루는 메타-관리자 페이지로, DynamicPage 의 재귀적 사용은 의미가 약함.
- HomePage 를 DynamicPage 로 교체하지 마라. 이유: 5 모듈 카드 그리드는 정적 진입 화면.
- backend 코드 수정 금지. 결함 발견 시 blocked.
- 라우트의 `meta.groupId` 외에 다른 group-id 정의 방식을 섞지 마라. 일관성.
