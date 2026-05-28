# Step 3: layout-and-routing

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "기술 스택" (Pinia 2 `storeToRefs` 필수, Vue Router 4), "절대 규칙" (Frontend 컴포넌트 PascalCase·composable `use` 접두사)
- `/docs/UI_GUIDE.md` — §6 레이아웃 (페이지 골격 ASCII 다이어그램, 그리드·간격·브레이크포인트), §11 접근성 (`:focus-visible`, 색만으로 정보 전달 금지)
- `/docs/ARCHITECTURE.md` — §2-2 Frontend 디렉토리 구조 (특히 `components/common/`, `pages/{itsm,itam,pms,common,system}`)
- `/docs/ADR.md` — ADR-004 (No-code 동적 렌더링 — 화면별 Vue 파일 작성 금지 원칙. 단, 이 step 의 "더미 페이지" 는 다음 phase 의 `DynamicPage` 로 즉시 대체될 골격이며, 화면-당-1-파일 패턴이 아닌 단일 `DynamicPage` 가 라우트에 바인딩되도록 사전 설계한다)
- `/phases/1-frontend-foundation/step0.md`·`step1.md`·`step2.md` — Vite/Vue/TS 스켈레톤, Tailwind v4 토큰, shadcn/vue 컴포넌트 5종

이전 step 의 결과로 shadcn `Button`·`Input`·`Card`·`Dialog`·`Table` 이 우리 토큰으로 동작. 이번 step 부터는 라우터·상태관리·레이아웃이 들어온다.

## 작업

이 step 의 목적은 **Pinia + Vue Router 를 설치하고, `UI_GUIDE.md §6-2` 의 페이지 골격(TopBar / Sidebar / PageHeader) 을 만들고, 다섯 모듈의 더미 페이지(`/`·`/itsm`·`/itam`·`/pms`·`/system/meta`)에 라우팅하는 것**이다. 실 메타 fetch 는 step 4.

### 1. 의존성

```bash
cd frontend
pnpm add pinia vue-router@4
pnpm add @vueuse/core @vueuse/components  # 다음 step 에서 useFetch 등으로도 사용
```

### 2. 디렉토리 구조 추가

```
src/
├── components/
│   ├── ui/                  # shadcn (step 2)
│   ├── layout/              # ← 이 step 에서 신규
│   │   ├── TopBar.vue
│   │   ├── Sidebar.vue
│   │   ├── PageHeader.vue
│   │   └── AppLayout.vue
│   └── common/              # ← 이 step 에서 시작 (StatusBadge 는 다음 phase)
├── stores/
│   └── useLayoutStore.ts    # Sidebar collapse 상태 등
├── router/
│   └── index.ts
└── pages/
    ├── HomePage.vue
    ├── itsm/IndexPage.vue
    ├── itam/IndexPage.vue
    ├── pms/IndexPage.vue
    ├── common/IndexPage.vue
    └── system/MetaPage.vue
```

> `pages/` 의 더미 페이지는 다음 phase 에서 `<DynamicPage group-id="..." />` 로 대체된다. 이 step 에서는 라우팅·레이아웃 검증을 위한 placeholder 만.

### 3. Pinia 셋업

`src/main.ts` 갱신:

```ts
import './assets/styles/tokens.css';
import './assets/styles/shadcn-mapping.css';
import './assets/styles/base.css';
import 'pretendard/dist/web/variable/pretendardvariable.css';
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';

createApp(App)
  .use(createPinia())
  .use(router)
  .mount('#app');
```

`src/stores/useLayoutStore.ts`:

```ts
import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useLayoutStore = defineStore('layout', () => {
  const sidebarCollapsed = ref(false);
  const sidebarMobileOpen = ref(false);   // md 이하에서 오프캔버스 토글

  function toggleSidebar() { sidebarCollapsed.value = !sidebarCollapsed.value; }
  function toggleMobile()  { sidebarMobileOpen.value = !sidebarMobileOpen.value; }

  return { sidebarCollapsed, sidebarMobileOpen, toggleSidebar, toggleMobile };
});
```

> store 를 컴포넌트에서 쓸 때 반드시 `storeToRefs` 사용 — CLAUDE.md 절대 규칙.

### 4. Router

`src/router/index.ts`:

```ts
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    children: [
      { path: '',           name: 'home',        component: () => import('@/pages/HomePage.vue'),
        meta: { title: '대시보드' } },
      { path: 'itsm',       name: 'itsm',        component: () => import('@/pages/itsm/IndexPage.vue'),
        meta: { title: 'ITSM' } },
      { path: 'itam',       name: 'itam',        component: () => import('@/pages/itam/IndexPage.vue'),
        meta: { title: 'ITAM' } },
      { path: 'pms',        name: 'pms',         component: () => import('@/pages/pms/IndexPage.vue'),
        meta: { title: 'PMS' } },
      { path: 'common',     name: 'common',      component: () => import('@/pages/common/IndexPage.vue'),
        meta: { title: '공통' } },
      { path: 'system/meta',name: 'system-meta', component: () => import('@/pages/system/MetaPage.vue'),
        meta: { title: '시스템 / 메타 관리' } },
    ],
  },
  {
    path: '/_dev/tokens',
    component: () => import('@/views/_dev/TokenGallery.vue'),
  },
  {
    path: '/_dev/shadcn',
    component: () => import('@/views/_dev/ShadcnSampler.vue'),
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
];

export default createRouter({
  history: createWebHistory(),
  routes,
});
```

> `_dev/*` 는 step 1·2 의 검증 페이지를 라우팅으로 옮긴 것. `App.vue` 는 더 이상 직접 갤러리를 렌더링하지 않고 `<RouterView />` 만 사용.

`src/App.vue`:

```vue
<template>
  <RouterView />
</template>
```

### 5. 레이아웃 컴포넌트

#### `AppLayout.vue` — 슬롯 레이아웃

```vue
<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { useLayoutStore } from '@/stores/useLayoutStore';
import TopBar from './TopBar.vue';
import Sidebar from './Sidebar.vue';

const layout = useLayoutStore();
const { sidebarCollapsed } = storeToRefs(layout);   // storeToRefs 필수
</script>

<template>
  <div class="flex min-h-screen bg-background text-foreground">
    <Sidebar />
    <div class="flex flex-1 flex-col">
      <TopBar />
      <main class="flex-1 p-6">
        <RouterView />
      </main>
    </div>
  </div>
</template>
```

#### `TopBar.vue` — 높이 56px, sticky

요구사항 (UI_GUIDE §6-2 + §11):
- 좌측: 햄버거 버튼(md 이하에서만 표시, `lucide-vue-next` 의 `Menu` 아이콘, `aria-label="메뉴 열기"`).
- 가운데/좌측 1: 프로젝트 타이틀 "Polestar10 ITG".
- 우측: 사용자 메뉴 placeholder (이름은 가상 샘플 `샘플 사용자`).
- 배경: `bg-surface`, 보더: `border-b border-border`, sticky `top-0`, z-index `40`.
- 본 step 은 dummy — 클릭 시 동작은 햄버거(`toggleMobile()`) 만.

#### `Sidebar.vue` — 너비 240px, 데스크탑 고정 / 모바일 오프캔버스

요구사항:
- 라우트 5개 링크 (`/`·`/itsm`·`/itam`·`/pms`·`/system/meta`). lucide 아이콘 + 라벨.
- 활성 라우트: `bg-surface-selected text-primary`. 비활성: `text-foreground hover:bg-surface-hover`.
- 데스크탑(`md` 이상): 항상 고정 240px.
- 모바일(`md` 미만): `sidebarMobileOpen` 이 true 면 오프캔버스로 슬라이드, false 면 숨김. 오버레이는 `bg-foreground/40` (UI_GUIDE §5-7).
- 접근성: 활성 항목에 `aria-current="page"`.

#### `PageHeader.vue` — 페이지 진입 시 라우트 meta.title 표시

요구사항:
- 높이 64px, padding 좌우 0 (main 의 패딩에 의존).
- `h1` 으로 `route.meta.title` 표시. 우측에 액션 슬롯(`<slot name="actions" />`).
- 본 step 의 더미 페이지에서 `<PageHeader />` 를 직접 사용. props 로 title 을 받는 형태도 허용 (route.meta 우선).

### 6. 더미 페이지

각 더미 페이지는 단순히 `<PageHeader />` + 한 줄 placeholder. 예시:

`src/pages/itsm/IndexPage.vue`:

```vue
<script setup lang="ts">
import PageHeader from '@/components/layout/PageHeader.vue';
</script>

<template>
  <section>
    <PageHeader />
    <p class="text-foreground-muted">
      이 페이지는 다음 phase 에서 &lt;DynamicPage group-id="itg-ticket" /&gt; 로 대체될 예정.
    </p>
  </section>
</template>
```

같은 패턴으로 `itam`·`pms`·`common`·`system/MetaPage` 를 작성. `HomePage.vue` 는 5개 모듈로 가는 카드 그리드 (shadcn `Card`) 정도로 약간만 더 풍부하게.

### 7. 반응형 검증

UI_GUIDE §6-3 의 브레이크포인트 (`md 768`) 기준:
- `< 768`: TopBar 좌측 햄버거 표시, Sidebar 데스크탑 고정 안 함(오프캔버스만).
- `>= 768`: Sidebar 항상 240px 표시, 햄버거 숨김.

### 8. ESLint·TypeScript 정합

- `vue-router` 의 `RouteRecordRaw` 타입 import.
- 컴포넌트는 PascalCase 파일명 (`TopBar.vue`·`AppLayout.vue` 등).
- composable·store 는 `use` 접두사 (`useLayoutStore`).
- `storeToRefs` 사용 없이 store 의 reactive 속성을 분해해 쓰면 ESLint 가 잡지 못하지만, **PR/리뷰에서 막는다** — 본 step 에서는 `AppLayout.vue` 의 예시처럼 반드시 `storeToRefs` 패턴을 유지.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 의존성
pnpm install
grep -E '"pinia"|"vue-router"|"@vueuse/core"' package.json | wc -l | xargs -I{} test {} -ge 3

# 2) 디렉토리·파일 존재
test -f src/router/index.ts
test -f src/stores/useLayoutStore.ts
test -f src/components/layout/AppLayout.vue
test -f src/components/layout/TopBar.vue
test -f src/components/layout/Sidebar.vue
test -f src/components/layout/PageHeader.vue
test -f src/pages/HomePage.vue
test -f src/pages/itsm/IndexPage.vue
test -f src/pages/itam/IndexPage.vue
test -f src/pages/pms/IndexPage.vue
test -f src/pages/common/IndexPage.vue
test -f src/pages/system/MetaPage.vue

# 3) 타입·린트·빌드
pnpm type-check
pnpm lint
pnpm build
test -f dist/index.html

# 4) dev 서버 부팅 + 라우트 200 (5 모듈 + dev 2)
pnpm dev &
DEV_PID=$!
sleep 5
for p in / /itsm /itam /pms /common /system/meta /_dev/tokens /_dev/shadcn; do
  code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:5173$p")
  test "$code" = "200" || { echo "FAIL $p ($code)"; exit 1; }
done
kill $DEV_PID
```

수동 검증:
- 데스크탑 너비(≥1024px) 에서 Sidebar 가 240px 로 좌측 고정, TopBar 56px sticky, PageHeader 64px.
- 1024px 미만에서 햄버거 클릭 시 Sidebar 오프캔버스 + 오버레이 표시.
- 활성 라우트 항목에 `bg-surface-selected text-primary` 적용.
- `:focus-visible` 이 시각적으로 표시 (Tab 키로 이동).

## 검증 절차

1. 위 AC 커맨드를 실행한다. 8 개 라우트가 모두 200 응답해야 한다.
2. 아키텍처 체크리스트:
   - `useLayoutStore` 가 setup 스타일 store 이고, 컴포넌트에서 `storeToRefs` 로 사용되는가? (CLAUDE.md 절대 규칙)
   - 라우터가 `createWebHistory` 사용, 모든 자식 라우트가 lazy import 인가?
   - `AppLayout.vue` 안에 `<RouterView />` 가 있고, 자식 라우트 5개가 이 레이아웃 아래에 있는가?
   - `_dev/*` 라우트는 `AppLayout` 외부에 두었는가? (디자인 검증 페이지 의도 — 레이아웃 없이 단독)
   - 사이드바 활성 항목에 `aria-current="page"` 가 있는가? (UI_GUIDE §11)
   - 햄버거 버튼에 `aria-label` 이 있는가?
   - Sidebar 오프캔버스의 오버레이가 blur 없이 단순 dim 인가? (UI_GUIDE §5-7)
3. 결과에 따라 `phases/1-frontend-foundation/index.json` 의 step 3 을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "Pinia 2 + Vue Router 4 + VueUse 11 도입. AppLayout(TopBar 56px sticky + Sidebar 240px collapsible + PageHeader 64px) + 5 모듈 더미 페이지(/, /itsm, /itam, /pms, /common, /system/meta) + _dev 라우트 2개. useLayoutStore (storeToRefs 패턴). 8 개 라우트 200 응답 확인."`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "<구체적 사유>"` 후 즉시 중단

## 금지사항

- Options API 사용 금지. `<script setup lang="ts">` 전용. CLAUDE.md 절대 규칙.
- Pinia store 의 반응형 속성을 `storeToRefs` 없이 구조 분해하지 마라 (`const { sidebarCollapsed } = useLayoutStore()`). 반응성이 깨진다. CLAUDE.md 절대 규칙.
- 더미 페이지에 실제 비즈니스 로직(데이터 fetch·CRUD)을 넣지 마라. 이유: 본 step 은 라우팅·레이아웃 검증. 메타 fetch 는 step 4.
- 다섯 모듈 페이지를 `DynamicPage` 미사용 영구 구조로 설계하지 마라. 이유: ADR-004 — 다음 phase 에서 `<DynamicPage group-id="..." />` 로 즉시 대체될 골격. 화면-당-1-파일 패턴이 굳어지면 No-code 약속이 깨진다.
- Sidebar 의 활성 라우트 표시에 단일 색상만 사용하지 마라. 이유: UI_GUIDE §11 — 색만으로 정보 전달 금지. `aria-current="page"` + 배경 색 변경 + 텍스트 색 변경 함께.
- TopBar 의 배경에 그라데이션·blur 장식을 추가하지 마라. UI_GUIDE §2 / §5-7 — TopBar 만 frosted backdrop-filter 가 허용되지만 이 step 의 검증 범위 밖이므로, 우선 단순 솔리드 + Hairline 보더로 시작. 필요 시 다음 phase ADR.
- 햄버거 버튼·아이콘 단독 버튼에 `aria-label` 누락하지 마라. UI_GUIDE §10.
- `console.log` 운영 코드 잔류 금지 (ESLint error 유지).
- API 호출 코드(`fetch`·`useFetch`) 를 이 step 에서 작성하지 마라. 이유: step 4 의 책임.
- `colors.primary` 외의 두 번째 액센트 색을 활성 라우트 표시에 추가하지 마라. UI_GUIDE §1.
- 더미 페이지에 사용자 이름·이메일·서버명 등 실 운영 데이터를 박지 마라. 가상 샘플(`"샘플 사용자"`, `example.com`) 만. ADR-011.
