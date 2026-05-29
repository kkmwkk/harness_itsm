# Step 0: meta-version-override

## 읽어야 할 파일

- `/CLAUDE.md` — 절대 규칙
- `/docs/PRD.md` — §5-2 자산 이력 복원 (등록 시점의 메타로 화면 복원)
- `/docs/ADR.md` — ADR-004 (No-code), ADR-006 (버전 그룹)
- `/phases/5-itam-asset-backend/index.json` step 1·2·3 summary — `getRegistrationMeta` + `/api/assets/{id}/registration-meta` endpoint, 시드 자산 5건
- `/phases/4-itsm-ticket-frontend/index.json` step 0 — usePageData·DynamicPage 의 props.rows·meta.api 자동 fetch 패턴
- `/frontend/src/composables/usePageMeta.ts` — 현재 (`groupId` 만 받아 PUBLISHED 최신 반환)
- `/frontend/src/components/dynamic/DynamicPage.vue` — 현재 props (`groupId`·`rows?`)
- `/frontend/src/types/meta.ts` — `PageMeta`·`ApiEnvelope<T>`

## 작업

이 step 의 목적은 **`usePageMeta` 가 group 기반(PUBLISHED 최신)에 더해 특정 메타 ID 기반(어떤 상태든)으로도 메타를 조회할 수 있도록 확장하고, `DynamicPage` 가 `metaId` prop 을 받아 특정 버전 메타로 렌더링할 수 있게 하는 것**이다. 자산 이력 복원의 토대.

### 1. `usePageMeta` 확장

기존 시그니처: `usePageMeta(groupId: string | Ref<string>): UsePageMetaResult`

신규 추가 — 두 모드를 한 composable 에서 다루도록:

```ts
type MetaIdent = { groupId: string } | { metaId: string };

export function usePageMeta(ident: MetaIdent | Ref<MetaIdent>): UsePageMetaResult {
  // 내부에서 URL 결정:
  //   { groupId } → /api/meta/active/{groupId}  (PUBLISHED 최신, 404 → notPublished)
  //   { metaId  } → /api/meta/{metaId}          (어떤 상태든 반환, 404 → error)
  ...
}
```

기존 호출자 호환을 위해 두 가지 오버로드 또는 helper 분리:

```ts
// 옵션 A — string 단축은 groupId 로 해석 (기존 호환)
export function usePageMeta(arg: string | Ref<string> | MetaIdent | Ref<MetaIdent>): UsePageMetaResult { ... }

// 옵션 B — 두 함수 분리
export function usePageMetaByGroup(groupId: ...): UsePageMetaResult;
export function usePageMetaById(metaId: ...):   UsePageMetaResult;
```

**권장: 옵션 A** (단일 진입점, 호환). 다만 내부 dispatch 로직이 복잡하면 옵션 B 도 OK.

`UsePageMetaResult` 의 `notPublished` 는 group 모드에서만 의미. metaId 모드에서는 항상 `false`. error 가 발생할 수는 있음 (해당 ID 존재 안 함 → 404).

### 2. `DynamicPage` props 확장

기존:
```ts
interface Props { groupId: string; rows?: unknown[]; }
```

신규:
```ts
interface Props {
  /** 그룹 기반 — PUBLISHED 최신 메타 사용 */
  groupId?: string;
  /** 메타 ID 직접 — 특정 버전 메타 사용 (이력 복원) */
  metaId?:  string;
  /** props.rows 우선 (호환) */
  rows?:    unknown[];
}
```

규칙:
- `groupId` 와 `metaId` 둘 다 제공되면 `metaId` 우선 (이력 복원이 더 명시적 의도).
- 둘 다 없으면 명시적 에러 카드 ("groupId 또는 metaId 가 필요합니다").

### 3. `_DynamicRoute.vue` 호환 유지

기존 router 의 `meta.groupId` 매핑은 그대로. `metaId` 매핑은 별도 라우트(다음 step) 에서 사용.

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

> 변경 없음. `metaId` 사용은 다음 step 의 자산 상세 페이지에서.

### 4. 단위 테스트

`src/composables/__tests__/usePageMeta.spec.ts`. 케이스:

- `groupId_단축_string_은_active_API_호출` — buildUrl 분리 후 테스트.
- `MetaIdent_groupId_는_active_API`.
- `MetaIdent_metaId_는_단건_API`.
- `usePageMeta_metaId_모드_notPublished_항상_false`.

URL 결정 로직은 별도 함수로 분리:

```ts
export function resolvePageMetaUrl(ident: MetaIdent | string): string {
  if (typeof ident === 'string')           return `/api/meta/active/${encodeURIComponent(ident)}`;
  if ('metaId' in ident)                   return `/api/meta/${encodeURIComponent(ident.metaId)}`;
  return `/api/meta/active/${encodeURIComponent(ident.groupId)}`;
}
```

테스트:
1. `resolvePageMetaUrl_string_groupId_단축`.
2. `resolvePageMetaUrl_groupId_object`.
3. `resolvePageMetaUrl_metaId_object`.
4. `resolvePageMetaUrl_encodeURIComponent_적용` (한글·슬래시 escape).

### 5. 검증 데모 페이지 (선택)

`_dev/MetaVersionOverride.vue` 라우트 `/_dev/meta-override` — 두 모드 시각 확인:
- 입력: groupId / metaId 두 인풋 + 선택 라디오 (group / metaId).
- DynamicPage 가 입력에 따라 렌더.

> 본 step 에서 필수는 아님. 시간 여유 있으면.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) usePageMeta 가 두 모드 지원
grep -q "MetaIdent\|metaId" src/composables/usePageMeta.ts

# 2) DynamicPage 에 metaId prop
grep -q "metaId" src/components/dynamic/DynamicPage.vue

# 3) 단위 테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 4) 기존 라우트 회귀
pnpm dev &
sleep 6
for p in /itsm /system/meta /_dev/dynamic-page; do
  test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173$p)" = "200"
done
kill %1
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - `usePageMeta` 가 group/metaId 두 모드를 한 composable 에서 다루는가?
   - `DynamicPage` props 가 호환성 유지(`groupId` 만 지정해도 동작)?
   - `resolvePageMetaUrl` 함수가 분리되어 단위 테스트 4 케이스 통과?
   - encodeURIComponent 적용?
3. step 0 업데이트:
   - 성공 → `"summary": "usePageMeta 확장(MetaIdent: group|metaId 두 모드, resolvePageMetaUrl 분리) + DynamicPage props.metaId 추가(groupId 와 동시 지정 시 metaId 우선, 둘 다 없으면 에러 카드). 단위 테스트 4 케이스. 기존 /itsm 등 회귀 없음."`

## 금지사항

- 기존 `usePageMeta(groupId)` 호출자(예: `_DynamicRoute.vue`) 깨지는 변경 금지. 호환성 유지.
- `metaId` 모드에서 `notPublished` 를 true 로 반환하지 마라. metaId 는 명시적 지정이므로 PUBLISHED 가 아니어도 자연스럽다.
- DynamicPage 가 도메인(asset) 특화 로직을 갖지 않게 — metaId 는 일반 prop.
- `axios` 등 별도 HTTP lib 추가 금지.
- 백엔드 코드 수정 금지.
- 새 라우트 추가는 step 1·3 의 책임. 본 step 은 composable + DynamicPage 만.
- 운영 코드 console.log 금지.
