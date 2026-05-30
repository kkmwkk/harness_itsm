# Step 6: micro-interactions

## 읽어야 할 파일
- `/docs/UI_GUIDE.md` v2 §9 마이크로 인터랙션 카탈로그
- `/phases/16-design-system-v2/step0~5.md`
- `/frontend/src/components/dynamic/DynamicGrid.vue`·`DynamicForm.vue`·`DynamicPage.vue`

## 작업
**Skeleton·Optimistic UI·Page transition·Hover detail** — 마이크로 인터랙션 풀세트 도입.

### 1. Skeleton 컴포넌트 — `frontend/src/components/feedback/Skeleton.vue`
```vue
<script setup lang="ts">
interface Props { width?: string; height?: string; rounded?: 'sm'|'md'|'lg'|'full'; }
const props = withDefaults(defineProps<Props>(), { width:'100%', height:'1em', rounded:'md' });
</script>
<template>
  <div :class="['relative overflow-hidden bg-surface-muted',
                 props.rounded === 'full' ? 'rounded-full' : `rounded-${props.rounded}`]"
       :style="{ width: props.width, height: props.height }">
    <div class="absolute inset-0 -translate-x-full animate-shimmer
                bg-gradient-to-r from-transparent via-foreground/5 to-transparent" />
  </div>
</template>
```

tokens.css 에 `@keyframes shimmer { from { transform: translateX(-100%) } to { transform: translateX(100%) } }` + `.animate-shimmer { animation: shimmer 1.2s infinite ease-out }`.

### 2. 적용
- `DynamicGrid` 로딩 시 (`isFetching && !rows.length`) → 행 8개 짜리 Skeleton 그리드.
- `KpiCard` 로딩 시 → 숫자·sparkline 자리 Skeleton.
- `DetailPage` 로딩 시 → dl 자리 Skeleton.

### 3. Optimistic UI
- `useDataMutation` 확장: `submitOptimistic(path, payload, optimisticUpdate)` — mutation 시작 시 즉시 client cache 갱신, 실패 시 rollback + 에러 토스트.
- ITSM 등록 폼 submit 후 그리드 reload 대신 client side 에 즉시 추가 → 백엔드 응답 결과로 보정.
- 칸반 드래그 이동도 optimistic.

### 4. Page transition
- `App.vue` 의 RouterView 를 `<router-view v-slot="{ Component }"><transition name="page" mode="out-in">...` 로 감싸기.
- CSS: `.page-enter-active{ transition: all var(--motion-base); } .page-enter-from { opacity: 0; transform: translateY(8px); }`

### 5. Hover detail
- 그리드 행 hover 시 우측에 quick action 아이콘 (편집·삭제·복사) fade-in.
- 카드 hover 시 shadow-card → shadow-hover, 살짝 translateY(-1px).

### 6. Toast stagger
- vue-sonner 의 `position` 우측 상단, 자동 스택. 새 toast 진입 시 위 toast 들이 살짝 아래로 밀림 (sonner 기본 동작).

### 7. prefers-reduced-motion
- 위 모든 애니메이션이 reduce 모드 시 0.01ms 또는 비활성.

## Acceptance Criteria
```bash
cd frontend
test -f src/components/feedback/Skeleton.vue
grep -q "animate-shimmer\|@keyframes shimmer" src/assets/styles/tokens.css
grep -q "Skeleton" src/components/dynamic/DynamicGrid.vue
grep -q "submitOptimistic\|optimistic" src/composables/useDataMutation.ts
grep -q "transition.*page\|<transition name=\"page\"" src/App.vue

pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- Skeleton 을 일반 spinner 로 대체 금지 — UI_GUIDE v2.
- Optimistic UI 가 백엔드 응답 도착 전 user 가 다른 페이지로 이동했을 때 race condition 발생 X — useDataMutation 내부 cancellation.
- Page transition 이 500ms 이상 길어지지 않게.
- 운영 코드 console.log 금지.
