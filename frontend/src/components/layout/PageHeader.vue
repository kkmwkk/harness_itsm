<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const props = defineProps<{
  title?: string;
}>();

const route = useRoute();

const displayTitle = computed<string>(() => {
  if (props.title) return props.title;
  const metaTitle = route.meta.title;
  return typeof metaTitle === 'string' ? metaTitle : '';
});
</script>

<template>
  <header class="flex h-16 items-center justify-between">
    <h1 class="text-[28px] font-bold leading-tight tracking-tight text-foreground">
      {{ displayTitle }}
    </h1>
    <div class="flex items-center gap-2">
      <slot name="actions" />
    </div>
  </header>
</template>
