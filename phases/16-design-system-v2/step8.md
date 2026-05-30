# Step 8: rich-empty-states-and-module-identity

## 읽어야 할 파일
- `/docs/UI_GUIDE.md` v2 §3 모듈 컬러·§5 EmptyState
- `/phases/16-design-system-v2/step0.md` (module-color helper)
- `/frontend/src/components/dynamic/DynamicPage.vue` (현재 notPublished 카드)
- `/frontend/src/lib/ui-messages.ts`

## 작업
**모듈별 시각 아이덴티티 + 풍부한 빈 상태**.

### 1. EmptyState 컴포넌트 — `frontend/src/components/feedback/EmptyState.vue`
```vue
<script setup lang="ts">
import type { Component } from 'vue';
import type { SystemType } from '@/types/meta';
interface Props {
  icon?: Component;
  title: string;
  description?: string;
  actionLabel?: string;
  module?: SystemType;
}
const props = defineProps<Props>();
defineEmits<{ action: [] }>();
</script>
<template>
  <div class="flex flex-col items-center justify-center text-center py-12 px-6 gap-3">
    <div :class="['size-14 rounded-full flex items-center justify-center',
                   props.module ? `bg-${props.module.toLowerCase()}-soft text-${props.module.toLowerCase()}`
                                : 'bg-surface-muted text-foreground-subtle']">
      <component :is="icon" class="size-7" :stroke-width="1.5" v-if="icon" />
    </div>
    <p class="text-base font-semibold">{{ title }}</p>
    <p class="text-sm text-foreground-muted max-w-md" v-if="description">{{ description }}</p>
    <Button v-if="actionLabel" class="mt-2" @click="$emit('action')">{{ actionLabel }}</Button>
  </div>
</template>
```

### 2. DynamicPage 의 notPublished 카드 — EmptyState 로 교체
- `<EmptyState :icon="Sparkles" title="아직 준비된 화면이 없습니다" description="..." actionLabel="메타 관리로" @action="router.push('/system/meta-editor')" />`

### 3. 모듈별 시각 아이덴티티
PageHeader 갱신:
- props: `module?: SystemType` 추가
- 헤더 좌측에 4px 컬러 띠 + 모듈 아이콘 (lucide) + 컬러 텍스트.
- `_DynamicRoute.vue` 가 router meta.systemType 에 따라 module prop 전달.

router 의 모듈 라우트에 `systemType` 추가:
```ts
{ path: 'itsm', meta: { groupId: 'itg-ticket', systemType: 'ITSM', detailUrlTemplate: '/itsm/{id}' }, ... }
```

### 4. Avatar 컴포넌트 — `frontend/src/components/common/Avatar.vue`
- props: `name`·`size` (sm/md/lg)
- 이름 첫 글자 2개로 initials, 색은 hash 기반.
- 그리드·칸반·NotificationItem·workflow 에서 사용.

### 5. 그리드 빈 상태
shadcn DataTable 의 `TableEmpty` 컴포넌트가 이미 있으니 그 안의 메시지를 EmptyState 로 교체. AG Grid 의 noRowsOverlay 도 동일.

### 6. 단위 테스트
- EmptyState 렌더링 케이스 (module prop·action emit).
- Avatar 의 initials/hash 색 매핑.

## Acceptance Criteria
```bash
cd frontend
test -f src/components/feedback/EmptyState.vue
test -f src/components/common/Avatar.vue
grep -q "EmptyState" src/components/dynamic/DynamicPage.vue
grep -q "systemType" src/router/index.ts
grep -q "module" src/components/layout/PageHeader.vue
pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- 모듈 컬러를 PageHeader 외 글로벌 헤더·버튼에 전염 X — 각 페이지 내부 한정.
- Avatar 색 매핑이 hash 기반이라 같은 이름 → 다른 색이 되지 않게 deterministic.
- EmptyState 의 actionLabel 누락 시 행동 유도 button 도 렌더하지 마라.
- 운영 코드 console.log 금지.
