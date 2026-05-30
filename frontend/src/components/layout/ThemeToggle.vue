<script setup lang="ts">
import { SunIcon, MoonIcon, MonitorIcon } from '@lucide/vue';
import { storeToRefs } from 'pinia';
import { useThemeStore } from '@/stores/useThemeStore';

const theme = useThemeStore();
const { mode } = storeToRefs(theme);
</script>

<template>
  <div
    class="inline-flex items-center gap-1 rounded-md border border-border bg-surface p-0.5"
  >
    <button
      type="button"
      :class="[
        'inline-flex h-7 w-7 items-center justify-center rounded',
        mode === 'light' ? 'bg-surface-hover text-foreground' : 'text-foreground-muted',
      ]"
      aria-label="라이트 모드"
      @click="theme.setMode('light')"
    >
      <SunIcon
        class="h-4 w-4"
        :stroke-width="1.5"
      />
    </button>
    <button
      type="button"
      :class="[
        'inline-flex h-7 w-7 items-center justify-center rounded',
        mode === 'system' ? 'bg-surface-hover text-foreground' : 'text-foreground-muted',
      ]"
      aria-label="시스템 테마"
      @click="theme.setMode('system')"
    >
      <MonitorIcon
        class="h-4 w-4"
        :stroke-width="1.5"
      />
    </button>
    <button
      type="button"
      :class="[
        'inline-flex h-7 w-7 items-center justify-center rounded',
        mode === 'dark' ? 'bg-surface-hover text-foreground' : 'text-foreground-muted',
      ]"
      aria-label="다크 모드"
      @click="theme.setMode('dark')"
    >
      <MoonIcon
        class="h-4 w-4"
        :stroke-width="1.5"
      />
    </button>
  </div>
</template>
