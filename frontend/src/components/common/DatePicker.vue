<script setup lang="ts">
/**
 * 날짜 선택 위젯 — 입력 box 클릭 시 캘린더 popover.
 * 모델 값은 ISO 'yyyy-MM-dd' 문자열(useFormSchema·DynamicForm 계약). ko-KR 표시.
 * native <input type="date"> 를 대체한다(UI_GUIDE — 토큰·다크 자동).
 */
import { computed, ref } from 'vue'
import type { DateValue } from '@internationalized/date'
import { CalendarIcon, X } from '@lucide/vue'
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover'
import { Calendar } from '@/components/ui/calendar'
import { cn } from '@/lib/utils'
import { toCalendarDate, fromCalendarDate, formatKo } from '@/lib/date-field'

interface Props {
  modelValue?: string | null
  id?: string
  placeholder?: string
  disabled?: boolean
}
const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  id: undefined,
  placeholder: '날짜 선택',
})
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const open = ref(false)
const calendarValue = computed(() => toCalendarDate(props.modelValue))
const display = computed(() => formatKo(props.modelValue))

function onPick(v: DateValue | undefined) {
  emit('update:modelValue', v ? fromCalendarDate(v as ReturnType<typeof toCalendarDate>) : '')
  if (v) open.value = false
}
function clear(e: Event) {
  e.stopPropagation()
  emit('update:modelValue', '')
}
</script>

<template>
  <Popover v-model:open="open">
    <PopoverTrigger
      :id="id"
      type="button"
      :disabled="disabled"
      :class="cn(
        'flex h-9 w-full items-center gap-2 rounded-md border border-border bg-background px-3 text-[14px] outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50',
        display ? 'text-foreground' : 'text-foreground-subtle',
      )"
    >
      <CalendarIcon class="size-4 shrink-0 text-foreground-muted" />
      <span class="flex-1 text-left">{{ display || placeholder }}</span>
      <button
        v-if="display && !disabled"
        type="button"
        class="text-foreground-subtle hover:text-foreground"
        aria-label="날짜 지우기"
        @click="clear"
      >
        <X class="size-3.5" />
      </button>
    </PopoverTrigger>
    <PopoverContent class="w-auto">
      <Calendar
        :model-value="calendarValue"
        @update:model-value="onPick"
      />
    </PopoverContent>
  </Popover>
</template>
