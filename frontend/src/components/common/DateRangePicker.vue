<script setup lang="ts">
/**
 * 날짜 범위 선택 위젯 — 두 칸(from·to) 표시 + 캘린더 popover 에서 범위 선택.
 * 모델 값은 { from, to } (ISO 'yyyy-MM-dd'). useFormSchema 의 date-range 스키마와 일치.
 */
import { computed, ref } from 'vue'
import type { DateValue } from '@internationalized/date'
import { CalendarIcon } from '@lucide/vue'
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover'
import { RangeCalendar } from '@/components/ui/calendar'
import { cn } from '@/lib/utils'
import { toCalendarDate, fromCalendarDate, formatKo } from '@/lib/date-field'

interface RangeModel {
  from?: string
  to?: string
}
interface Props {
  modelValue?: RangeModel | null
  id?: string
  disabled?: boolean
}
const props = withDefaults(defineProps<Props>(), { modelValue: null, id: undefined })
const emit = defineEmits<{ 'update:modelValue': [value: RangeModel] }>()

const open = ref(false)
const calendarRange = computed(() => ({
  start: toCalendarDate(props.modelValue?.from),
  end: toCalendarDate(props.modelValue?.to),
}))
const fromText = computed(() => formatKo(props.modelValue?.from))
const toText = computed(() => formatKo(props.modelValue?.to))

function onPick(range: { start?: DateValue; end?: DateValue }) {
  emit('update:modelValue', {
    from: range.start ? fromCalendarDate(range.start as ReturnType<typeof toCalendarDate>) : '',
    to: range.end ? fromCalendarDate(range.end as ReturnType<typeof toCalendarDate>) : '',
  })
  if (range.start && range.end) open.value = false
}
</script>

<template>
  <Popover v-model:open="open">
    <PopoverTrigger
      :id="id"
      type="button"
      :disabled="disabled"
      class="flex h-9 w-full items-center gap-2 rounded-md border border-border bg-background px-3 text-[14px] outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
    >
      <CalendarIcon class="size-4 shrink-0 text-foreground-muted" />
      <span :class="cn('flex-1 text-left', fromText ? 'text-foreground' : 'text-foreground-subtle')">
        {{ fromText || '시작일' }}
      </span>
      <span class="text-foreground-subtle">~</span>
      <span :class="cn('flex-1 text-left', toText ? 'text-foreground' : 'text-foreground-subtle')">
        {{ toText || '종료일' }}
      </span>
    </PopoverTrigger>
    <PopoverContent class="w-auto">
      <RangeCalendar
        :model-value="calendarRange"
        @update:model-value="onPick"
      />
    </PopoverContent>
  </Popover>
</template>
