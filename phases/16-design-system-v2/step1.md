# Step 1: dark-mode-and-theme-switcher

## 읽어야 할 파일
- `/phases/16-design-system-v2/step0.md` — 다크 모드 토큰 정의
- `/frontend/src/assets/styles/tokens.css` (다크 토큰 추가 완료)
- `/frontend/src/components/layout/TopBar.vue`·`stores/useLayoutStore.ts`

## 작업
**다크 모드 토글 + localStorage 영구화 + SSR-safe initial 적용**.

### 1. `useThemeStore` — `frontend/src/stores/useThemeStore.ts`
```ts
import { defineStore } from 'pinia';
import { ref, watch } from 'vue';

type ThemeMode = 'light' | 'dark' | 'system';
const STORAGE_KEY = 'itg.theme';

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(
    (localStorage.getItem(STORAGE_KEY) as ThemeMode) ?? 'system'
  );

  function effective(): 'light' | 'dark' {
    if (mode.value === 'system') {
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return mode.value;
  }

  function apply() {
    const eff = effective();
    document.documentElement.classList.toggle('dark', eff === 'dark');
  }

  function setMode(next: ThemeMode) {
    mode.value = next;
    localStorage.setItem(STORAGE_KEY, next);
    apply();
  }

  watch(mode, apply, { immediate: false });

  // 시스템 변화 감지
  if (typeof window !== 'undefined') {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (mode.value === 'system') apply();
    });
  }

  return { mode, setMode, apply, effective };
});
```

### 2. `main.ts` 초기 적용
앱 부팅 시 즉시 `useThemeStore().apply()` 호출 → flash 회피.

```ts
import { useThemeStore } from '@/stores/useThemeStore';
// ... createPinia 등록 후
useThemeStore().apply();
```

### 3. TopBar 토글 컴포넌트 — `frontend/src/components/layout/ThemeToggle.vue`
```vue
<script setup lang="ts">
import { SunIcon, MoonIcon, MonitorIcon } from '@lucide/vue';
import { useThemeStore } from '@/stores/useThemeStore';
const theme = useThemeStore();
</script>
<template>
  <div class="inline-flex items-center gap-1 rounded-md border border-border bg-surface p-0.5">
    <button :class="['size-7 inline-flex items-center justify-center rounded',
                      theme.mode==='light' ? 'bg-surface-hover text-foreground' : 'text-foreground-muted']"
            @click="theme.setMode('light')" aria-label="Light mode"><SunIcon class="size-4" :stroke-width="1.5"/></button>
    <button :class="['size-7 inline-flex items-center justify-center rounded',
                      theme.mode==='system' ? 'bg-surface-hover text-foreground' : 'text-foreground-muted']"
            @click="theme.setMode('system')" aria-label="System theme"><MonitorIcon class="size-4" :stroke-width="1.5"/></button>
    <button :class="['size-7 inline-flex items-center justify-center rounded',
                      theme.mode==='dark' ? 'bg-surface-hover text-foreground' : 'text-foreground-muted']"
            @click="theme.setMode('dark')" aria-label="Dark mode"><MoonIcon class="size-4" :stroke-width="1.5"/></button>
  </div>
</template>
```

### 4. TopBar 에 통합
ThemeToggle 컴포넌트를 우측(로그아웃 옆) 에 배치.

### 5. 단위 테스트
- `useThemeStore.spec` — setMode 호출 → localStorage 저장·class 토글.

### 6. 다크 모드 회귀 확인
모든 기존 페이지(/itsm·/itam·/system/users 등) 가 다크 모드에서 정상 보이는지 (텍스트·배경·보더 충돌 없는지) 자기 검증.

## Acceptance Criteria
```bash
cd frontend
test -f src/stores/useThemeStore.ts
test -f src/components/layout/ThemeToggle.vue
grep -q "useThemeStore" src/main.ts
grep -q "ThemeToggle" src/components/layout/TopBar.vue

pnpm type-check
pnpm lint
pnpm build
pnpm test

pnpm dev &
sleep 5
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/)" = "200"
kill %1
```

## 금지사항
- 다크 모드를 inline style 로 강제하지 마라 — `.dark` 클래스 토글만.
- localStorage 키를 변경하지 마라 (`itg.theme` 고정).
- prefers-color-scheme 무시 금지 — system 모드 지원.
- 다크 모드 전환 시 페이지 flicker 발생 X (main.ts 부팅 시 즉시 apply).
- 운영 코드 console.log 금지.
