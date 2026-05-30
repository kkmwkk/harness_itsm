<script setup lang="ts">
/**
 * 범위 날짜 캘린더 — reka-ui RangeCalendar 프리미티브를 토큰 스타일로 감쌌다.
 * v-model 은 { start?, end? } (DateValue). 표시·변환은 호출자(DateRangePicker) 책임.
 */
import type { DateValue } from '@internationalized/date'
import { ChevronLeft, ChevronRight } from '@lucide/vue'
import {
  RangeCalendarCell,
  RangeCalendarCellTrigger,
  RangeCalendarGrid,
  RangeCalendarGridBody,
  RangeCalendarGridHead,
  RangeCalendarGridRow,
  RangeCalendarHeadCell,
  RangeCalendarHeader,
  RangeCalendarHeading,
  RangeCalendarNext,
  RangeCalendarPrev,
  RangeCalendarRoot,
} from 'reka-ui'

/** reka-ui RangeCalendar 의 DateRange 와 동일 형태(키는 필수, 값은 미선택 시 undefined). */
interface DateRange {
  start: DateValue | undefined
  end: DateValue | undefined
}

withDefaults(defineProps<{ modelValue?: DateRange }>(), {})
defineEmits<{ 'update:modelValue': [value: DateRange] }>()
</script>

<template>
  <RangeCalendarRoot
    v-slot="{ weekDays, grid }"
    :model-value="modelValue"
    locale="ko-KR"
    class="select-none"
    @update:model-value="(v) => $emit('update:modelValue', (v ?? {}) as DateRange)"
  >
    <RangeCalendarHeader class="flex items-center justify-between pb-3">
      <RangeCalendarPrev
        class="inline-flex size-7 items-center justify-center rounded-md text-foreground-muted hover:bg-surface-hover hover:text-foreground"
        aria-label="이전 달"
      >
        <ChevronLeft class="size-4" />
      </RangeCalendarPrev>
      <RangeCalendarHeading class="text-[14px] font-semibold text-foreground" />
      <RangeCalendarNext
        class="inline-flex size-7 items-center justify-center rounded-md text-foreground-muted hover:bg-surface-hover hover:text-foreground"
        aria-label="다음 달"
      >
        <ChevronRight class="size-4" />
      </RangeCalendarNext>
    </RangeCalendarHeader>

    <RangeCalendarGrid
      v-for="month in grid"
      :key="month.value.toString()"
      class="w-full border-collapse"
    >
      <RangeCalendarGridHead>
        <RangeCalendarGridRow class="flex">
          <RangeCalendarHeadCell
            v-for="day in weekDays"
            :key="day"
            class="w-9 text-[12px] font-normal text-foreground-subtle"
          >
            {{ day }}
          </RangeCalendarHeadCell>
        </RangeCalendarGridRow>
      </RangeCalendarGridHead>
      <RangeCalendarGridBody>
        <RangeCalendarGridRow
          v-for="(weekDates, wi) in month.rows"
          :key="`w-${wi}`"
          class="flex"
        >
          <RangeCalendarCell
            v-for="weekDate in weekDates"
            :key="weekDate.toString()"
            :date="weekDate"
            class="p-0.5 text-center data-[selected]:bg-primary/10 data-[selection-start]:rounded-l-md data-[selection-end]:rounded-r-md"
          >
            <RangeCalendarCellTrigger
              :day="weekDate"
              :month="month.value"
              class="inline-flex size-8 items-center justify-center rounded-md text-[13px] tabular-nums text-foreground hover:bg-surface-hover data-[selection-start]:bg-primary data-[selection-start]:text-primary-foreground data-[selection-end]:bg-primary data-[selection-end]:text-primary-foreground data-[today]:font-semibold data-[outside-view]:text-foreground-subtle data-[disabled]:opacity-40 data-[disabled]:pointer-events-none"
            />
          </RangeCalendarCell>
        </RangeCalendarGridRow>
      </RangeCalendarGridBody>
    </RangeCalendarGrid>
  </RangeCalendarRoot>
</template>
