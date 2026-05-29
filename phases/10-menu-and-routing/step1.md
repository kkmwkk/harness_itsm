# Step 1: menu-store-and-dynamic-sidebar

## 읽어야 할 파일

- `/CLAUDE.md` 절대 규칙
- `/docs/ARCHITECTURE.md` §2-2 frontend, §8-3 메뉴 권한 필터
- `/docs/PRD.md` §4-1 Menu 모델
- `/phases/10-menu-and-routing/step0.md` — useAuthStore
- `/phases/9-auth-and-users/index.json` step 4 summary — `/api/menu` 응답 형태 (트리)
- `/frontend/src/components/layout/Sidebar.vue` (현재 하드코딩)
- `/frontend/src/router/index.ts`

## 작업

이 step 의 목적은 **`useMenuStore` 로 `/api/menu` 응답을 보관하고, Sidebar 가 그 트리를 동적으로 렌더링하며, 메뉴의 `route`·`group_id` 에 따라 라우터 가 동작하도록 wiring 하는 것**이다.

### 1. 타입 — `src/types/menu.ts`

```ts
export interface MenuNode {
  id:        number;
  code:      string;
  label:     string;
  icon:      string | null;       // lucide 아이콘명 (예: 'TicketCheck')
  route:     string | null;       // 예: '/itsm'
  groupId:   string | null;       // PageMeta 그룹 (DynamicPage 진입 키)
  permissionCode: string | null;
  sortOrder: number;
  children:  MenuNode[];
}
```

### 2. `useMenuStore` — `src/stores/useMenuStore.ts`

```ts
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useApiFetch } from '@/lib/api';
import type { ApiEnvelope } from '@/types/meta';
import type { MenuNode } from '@/types/menu';

export const useMenuStore = defineStore('menu', () => {
  const tree       = ref<MenuNode[]>([]);
  const isLoading  = ref(false);
  const lastError  = ref<string | null>(null);

  async function load() {
    if (isLoading.value) return;
    isLoading.value = true;
    lastError.value = null;
    try {
      const { data, error } = await useApiFetch('/api/menu')
        .json<ApiEnvelope<MenuNode[]>>();
      if (error.value) { lastError.value = '메뉴를 불러올 수 없습니다.'; return; }
      tree.value = data.value?.data ?? [];
    } finally {
      isLoading.value = false;
    }
  }

  function clear() { tree.value = []; lastError.value = null; }

  /** route 로 메뉴 노드 찾기 (현재 active 표시용) */
  function findByRoute(route: string): MenuNode | null { /* DFS */ ... }

  return { tree, isLoading, lastError, load, clear, findByRoute };
});
```

라우터 가드(`router.beforeEach`) 에서 로그인 직후 `menuStore.load()` 호출. 로그아웃 시 `menuStore.clear()`.

### 3. `Sidebar.vue` 동적 트리 렌더링

기존 하드코딩된 메뉴 5개를 `useMenuStore.tree` 로 대체.

```vue
<script setup lang="ts">
import { computed } from 'vue';
import { storeToRefs } from 'pinia';
import { useRoute } from 'vue-router';
import { useMenuStore } from '@/stores/useMenuStore';
import { useLayoutStore } from '@/stores/useLayoutStore';
import * as Lucide from '@lucide/vue';
import type { Component } from 'vue';
import type { MenuNode } from '@/types/menu';

const route = useRoute();
const menuStore   = useMenuStore();
const layoutStore = useLayoutStore();
const { tree, isLoading } = storeToRefs(menuStore);
const { sidebarMobileOpen } = storeToRefs(layoutStore);

function resolveIcon(name: string | null): Component | null {
  if (!name) return null;
  const all = Lucide as unknown as Record<string, Component>;
  return all[name] ?? null;
}

function isActive(node: MenuNode): boolean {
  if (!node.route) return false;
  return route.path === node.route || route.path.startsWith(node.route + '/');
}
</script>

<template>
  <aside class="...">
    <nav class="flex flex-col gap-1 p-2">
      <p v-if="isLoading" class="text-xs text-foreground-muted px-2">메뉴 로드 중...</p>
      <template v-else>
        <MenuTreeNode
          v-for="node in tree"
          :key="node.id"
          :node="node"
        />
      </template>
    </nav>
  </aside>
</template>
```

별도 재귀 컴포넌트 `MenuTreeNode.vue`:

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, RouterLink } from 'vue-router';
import * as Lucide from '@lucide/vue';
import type { Component } from 'vue';
import type { MenuNode } from '@/types/menu';

interface Props { node: MenuNode; depth?: number; }
const props = withDefaults(defineProps<Props>(), { depth: 0 });

const route = useRoute();
const open = ref(true);

const hasChildren = computed(() => props.node.children.length > 0);
const isActive = computed(() =>
  props.node.route && (route.path === props.node.route || route.path.startsWith(props.node.route + '/'))
);

function resolveIcon(name: string | null): Component | null {
  if (!name) return null;
  const all = Lucide as unknown as Record<string, Component>;
  return all[name] ?? null;
}
const IconComp = computed(() => resolveIcon(props.node.icon));
</script>

<template>
  <div>
    <component
      :is="node.route && !hasChildren ? RouterLink : 'button'"
      :to="node.route && !hasChildren ? node.route : undefined"
      :class="['flex items-center gap-2 w-full px-2 py-1.5 rounded-md text-sm text-left',
               isActive ? 'bg-surface-selected text-primary' : 'hover:bg-surface-hover']"
      :style="{ paddingLeft: `${depth * 12 + 8}px` }"
      :aria-current="isActive ? 'page' : undefined"
      @click="hasChildren && (open = !open)"
    >
      <component :is="IconComp" v-if="IconComp" class="size-4 shrink-0" :stroke-width="1.5" />
      <span class="flex-1 truncate">{{ node.label }}</span>
      <Lucide.ChevronDownIcon v-if="hasChildren"
        :class="['size-4 transition-transform', open ? '' : '-rotate-90']" />
    </component>

    <div v-if="hasChildren && open" class="flex flex-col gap-1">
      <MenuTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :depth="depth + 1"
      />
    </div>
  </div>
</template>
```

### 4. 라우터 동적 화 (선택 — 본 step 의 단순화)

본 step 에서 라우터를 완전히 동적으로 만들지 않는다. 기존 `_DynamicRoute.vue` 가 `meta.groupId` 로 동작하는 패턴을 유지하되:

- 메뉴의 `route` 가 router 에 등록된 정적 경로면 그대로 매칭.
- 새 메뉴가 추가되면 router 갱신이 필요. v2.1 의 본 phase 에서는 **시드 메뉴(시스템 관리·ITSM·ITAM 등) 가 router 에 정적 등록되어 있다고 가정**.

향후 phase (M9·M10) 에서 메뉴 추가 시 라우터에 동적 등록(`router.addRoute()`) 도입 가능 — 별도 ADR.

### 5. 라우터에 시스템 관리 자식 라우트 등록

기존 router/index.ts 에 다음을 추가 (메인 영역 children 안):

```ts
{ path: 'system/users',  name: 'system-users',  component: () => import('@/pages/system/UserPage.vue'),
  meta: { title: '사용자 관리' } },
{ path: 'system/depts',  name: 'system-depts',  component: () => import('@/pages/system/DeptPage.vue'),
  meta: { title: '부서 관리' } },
{ path: 'system/roles',  name: 'system-roles',  component: () => import('@/pages/system/RolePage.vue'),
  meta: { title: '역할 관리' } },
{ path: 'system/menus',  name: 'system-menus',  component: () => import('@/pages/system/MenuPage.vue'),
  meta: { title: '메뉴 관리' } },
```

> 실제 컴포넌트는 step 2 에서 작성. 본 step 은 라우트 등록까지.

step 2 가 만들기 전까지 임시 placeholder 페이지 둠 — 즉 step 0~1 통합 검증을 위해 `UserPage.vue` 등은 step 2 의 책임이므로 본 step 종료 시점에는 import 가 실패할 수 있다. **이 step 에서는 라우트 정의를 작성하지만 실제 import 는 step 2 후에 동작**.

대안: step 1 종료 시점에 시스템 페이지 4종을 **최소 stub** (`<template>준비 중</template>`) 으로 미리 만들어 두기. **권장 — stub 생성**.

### 6. 단위 테스트

`src/stores/__tests__/useMenuStore.spec.ts`:

1. `load_성공_시_tree_채움`.
2. `load_실패_시_lastError_set`.
3. `clear_tree_비움`.
4. `findByRoute_DFS_매칭`.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 파일
test -f src/types/menu.ts
test -f src/stores/useMenuStore.ts
test -f src/components/layout/MenuTreeNode.vue
# 시스템 페이지 stub 4개 (step 2 의 완전 구현 전 단계)
test -f src/pages/system/UserPage.vue
test -f src/pages/system/DeptPage.vue
test -f src/pages/system/RolePage.vue
test -f src/pages/system/MenuPage.vue

# 2) Sidebar 가 useMenuStore 사용
grep -q "useMenuStore" src/components/layout/Sidebar.vue
! grep -q "modules: " src/components/layout/Sidebar.vue       # 기존 하드코딩 제거 확인

# 3) 라우터 가드가 menuStore.load 호출
grep -q "useMenuStore\|menuStore.load" src/router/index.ts

# 4) 시스템 라우트 등록
grep -q "system/users" src/router/index.ts
grep -q "system/depts" src/router/index.ts
grep -q "system/roles" src/router/index.ts
grep -q "system/menus" src/router/index.ts

# 5) 정적·테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 6) dev 부팅 + 라우트 (인증 안 된 상태에서는 /login 으로 redirect 되므로 SPA HTTP 는 200)
pnpm dev &
sleep 6
for p in / /login /system/users /system/depts /system/roles /system/menus; do
  test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173$p)" = "200"
done
kill %1
```

수동 검증 (backend + 시드 살아있는 상태):
- admin 로그인 → Sidebar 에 시드 메뉴 트리(대시보드·ITSM(자식)·ITAM·PMS·공통·시스템 관리(자식 5)) 가 표시.
- user-sample-1 로그인 → 시스템 관리 노출 안 됨, ITSM 만 (TICKET_READ).
- 새로고침 후에도 메뉴 트리 정상.

## 검증 절차

1. AC + 수동 검증 통과.
2. 아키텍처 체크:
   - Sidebar 가 동적 트리 (하드코딩 제거)?
   - 사용자 권한별 메뉴 차이 시각 확인?
   - MenuTreeNode 가 재귀 컴포넌트로 트리 표현?
   - lucide 아이콘이 메뉴의 icon 코드명으로 동적 매핑?
   - 새로고침 후 토큰 복원 + 메뉴 자동 load?
3. step 1 업데이트:
   - 성공 → `"summary": "types/menu.ts + useMenuStore (load/clear/findByRoute) + MenuTreeNode.vue 재귀 컴포넌트 + Sidebar 동적 트리 (하드코딩 제거, lucide 아이콘 동적 매핑, depth 들여쓰기, active 표시) + 시스템 라우트 4개 등록(/system/users·depts·roles·menus stub) + 라우터 가드가 menuStore.load 호출. 단위 테스트 4 케이스."`

## 금지사항

- 메뉴 트리를 클라이언트에서 권한 필터하지 마라 — 백엔드 `/api/menu` 가 이미 필터된 결과 반환.
- 사용자 모르게 메뉴 새로고침 폴링 금지.
- 시스템 페이지 stub 외 실 CRUD UI 본 step 에서 만들지 마라 (step 2).
- 라우터 동적 추가(`addRoute`) 도입 금지 — v2.1 본 phase 는 정적 등록.
- 백엔드 수정 금지.
- 운영 코드 console.log 금지.
