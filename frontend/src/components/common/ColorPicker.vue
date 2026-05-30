<script setup lang="ts">
/**
 * 색 선택 위젯 — 색 swatch(native color input) + hex 텍스트 입력.
 * 자산 분류 메타 등의 색 필드용(옵션). 모델 값은 hex 문자열('#rrggbb').
 */
import { computed } from 'vue'
import { Input } from '@/components/ui/input'

interface Props {
  modelValue?: string | null
  id?: string
  disabled?: boolean
}
const props = withDefaults(defineProps<Props>(), { modelValue: null, id: undefined })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const HEX = /^#[0-9a-fA-F]{6}$/
const swatch = computed(() => (props.modelValue && HEX.test(props.modelValue) ? props.modelValue : '#000000'))

function onColor(e: Event) {
  emit('update:modelValue', (e.target as HTMLInputElement).value)
}
function onHex(val: string | number) {
  emit('update:modelValue', String(val))
}
</script>

<template>
  <div class="flex items-center gap-2">
    <input
      :id="id"
      type="color"
      :value="swatch"
      :disabled="disabled"
      class="size-9 shrink-0 cursor-pointer rounded-md border border-border bg-background p-0.5 disabled:opacity-50"
      aria-label="색 선택"
      @input="onColor"
    >
    <Input
      :model-value="modelValue ?? ''"
      placeholder="#0066cc"
      :disabled="disabled"
      class="font-mono"
      @update:model-value="onHex"
    />
  </div>
</template>
