<script setup lang="ts">
import { Toaster } from '@/components/ui/sonner';
</script>

<template>
  <RouterView />
  <!-- UI_GUIDE §5-8 — 우측 상단 stack, 4초 자동 사라짐. 스타일 규격은 toast.css 가 담당. -->
  <Toaster
    position="top-right"
    :duration="4000"
    rich-colors
  />
</template>
