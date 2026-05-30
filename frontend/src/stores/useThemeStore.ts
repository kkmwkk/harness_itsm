import { defineStore } from 'pinia';
import { ref, watch } from 'vue';

export type ThemeMode = 'light' | 'dark' | 'system';
const STORAGE_KEY = 'itg.theme';

/** SSR/테스트(node) 환경에서도 깨지지 않도록 브라우저 전역 접근을 가드한다. */
function hasWindow(): boolean {
  return typeof window !== 'undefined';
}

function readStoredMode(): ThemeMode {
  if (typeof localStorage === 'undefined') return 'system';
  const stored = localStorage.getItem(STORAGE_KEY);
  return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system';
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(readStoredMode());

  function effective(): 'light' | 'dark' {
    if (mode.value === 'system') {
      if (!hasWindow()) return 'light';
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return mode.value;
  }

  function apply() {
    if (typeof document === 'undefined') return;
    document.documentElement.classList.toggle('dark', effective() === 'dark');
  }

  function setMode(next: ThemeMode) {
    mode.value = next;
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(STORAGE_KEY, next);
    }
    apply();
  }

  watch(mode, apply, { immediate: false });

  // 시스템 테마 변화 감지 — system 모드일 때만 재적용
  if (hasWindow()) {
    window
      .matchMedia('(prefers-color-scheme: dark)')
      .addEventListener('change', () => {
        if (mode.value === 'system') apply();
      });
  }

  return { mode, setMode, apply, effective };
});
