<script setup lang="ts">
/**
 * 슬라이더 숫자 입력 — DynamicForm 의 type='number' + 옵션 widget:'slider' 일 때 사용.
 * 모델 값은 number. 트랙 옆에 현재 값을 tabular-nums 로 표시한다.
 */
import { computed } from 'vue'

interface Props {
  modelValue?: number | null
  id?: string
  min?: number
  max?: number
  step?: number
  disabled?: boolean
}
const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  id: undefined,
  min: 0,
  max: 100,
  step: 1,
})
const emit = defineEmits<{ 'update:modelValue': [value: number] }>()

const current = computed(() => props.modelValue ?? props.min)

function onInput(e: Event) {
  emit('update:modelValue', Number((e.target as HTMLInputElement).value))
}
</script>

<template>
  <div class="flex items-center gap-3">
    <input
      :id="id"
      type="range"
      :min="min"
      :max="max"
      :step="step"
      :value="current"
      :disabled="disabled"
      class="h-1.5 flex-1 cursor-pointer appearance-none rounded-full bg-surface-hover accent-primary disabled:opacity-50"
      @input="onInput"
    >
    <span class="w-12 shrink-0 text-right text-[14px] font-semibold tabular-nums text-foreground">
      {{ current }}
    </span>
  </div>
</template>
