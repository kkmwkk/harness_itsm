<script setup lang="ts">
import { computed } from 'vue';
import { statusSpec } from '@/lib/badges';

interface Props {
  value: string | null | undefined;
}
const props = defineProps<Props>();

const spec = computed(() => statusSpec(props.value));
</script>

<template>
  <span
    :class="[
      'inline-flex items-center rounded-full px-2.5 py-0.5 text-[12px] font-semibold',
      spec.bg,
      spec.color,
    ]"
  >
    {{ spec.label }}
  </span>
</template>
