<script setup lang="ts">
/**
 * 단일 날짜 캘린더 — reka-ui Calendar 프리미티브를 토큰 스타일로 감싼 shadcn 스타일 컴포넌트.
 * v-model 은 @internationalized/date 의 DateValue(CalendarDate). 표시·변환은 호출자(DatePicker) 책임.
 */
import type { DateValue } from '@internationalized/date'
import { ChevronLeft, ChevronRight } from '@lucide/vue'
import {
  CalendarCell,
  CalendarCellTrigger,
  CalendarGrid,
  CalendarGridBody,
  CalendarGridHead,
  CalendarGridRow,
  CalendarHeadCell,
  CalendarHeader,
  CalendarHeading,
  CalendarNext,
  CalendarPrev,
  CalendarRoot,
} from 'reka-ui'

withDefaults(
  defineProps<{ modelValue?: DateValue }>(),
  {},
)
defineEmits<{ 'update:modelValue': [value: DateValue | undefined] }>()
</script>

<template>
  <CalendarRoot
    v-slot="{ weekDays, grid }"
    :model-value="modelValue"
    locale="ko-KR"
    class="select-none"
    @update:model-value="(v) => $emit('update:modelValue', v as DateValue | undefined)"
  >
    <CalendarHeader class="flex items-center justify-between pb-3">
      <CalendarPrev
        class="inline-flex size-7 items-center justify-center rounded-md text-foreground-muted hover:bg-surface-hover hover:text-foreground"
        aria-label="이전 달"
      >
        <ChevronLeft class="size-4" />
      </CalendarPrev>
      <CalendarHeading class="text-[14px] font-semibold text-foreground" />
      <CalendarNext
        class="inline-flex size-7 items-center justify-center rounded-md text-foreground-muted hover:bg-surface-hover hover:text-foreground"
        aria-label="다음 달"
      >
        <ChevronRight class="size-4" />
      </CalendarNext>
    </CalendarHeader>

    <CalendarGrid
      v-for="month in grid"
      :key="month.value.toString()"
      class="w-full border-collapse"
    >
      <CalendarGridHead>
        <CalendarGridRow class="flex">
          <CalendarHeadCell
            v-for="day in weekDays"
            :key="day"
            class="w-9 text-[12px] font-normal text-foreground-subtle"
          >
            {{ day }}
          </CalendarHeadCell>
        </CalendarGridRow>
      </CalendarGridHead>
      <CalendarGridBody>
        <CalendarGridRow
          v-for="(weekDates, wi) in month.rows"
          :key="`w-${wi}`"
          class="flex"
        >
          <CalendarCell
            v-for="weekDate in weekDates"
            :key="weekDate.toString()"
            :date="weekDate"
            class="p-0.5 text-center"
          >
            <CalendarCellTrigger
              :day="weekDate"
              :month="month.value"
              class="inline-flex size-8 items-center justify-center rounded-md text-[13px] tabular-nums text-foreground hover:bg-surface-hover data-[selected]:bg-primary data-[selected]:text-primary-foreground data-[today]:font-semibold data-[today]:text-primary data-[selected]:data-[today]:text-primary-foreground data-[outside-view]:text-foreground-subtle data-[disabled]:opacity-40 data-[disabled]:pointer-events-none"
            />
          </CalendarCell>
        </CalendarGridRow>
      </CalendarGridBody>
    </CalendarGrid>
  </CalendarRoot>
</template>
