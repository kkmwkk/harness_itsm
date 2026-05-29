<script setup lang="ts">
import { computed } from 'vue';
import { Trash2Icon, PlusIcon } from '@lucide/vue';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { Card, CardContent } from '@/components/ui/card';
import type { GridColumnMeta, FieldType } from '@/types/meta-body';
import {
  FIELD_TYPES,
  FIELD_TYPE_LABELS,
  PINNED_OPTIONS,
  HIDE_AT_OPTIONS,
  isValidFieldName,
  rendersAsBadge,
  createBlankColumn,
  duplicateFields,
  validateGridColumns,
  pinnedValue,
  setPinned,
  hideAtValue,
  setHideAt,
  type PinnedOption,
  type HideAtOption,
} from '@/composables/useGridColumnEditor';

/**
 * 그리드 컬럼(grid.columns) GUI 편집기 (ADR-016 1단계).
 * JSON 직접 노출 없이 추가/삭제/속성 편집만 — 순서 변경(드래그)은 phase 14.
 * 순수 로직은 useGridColumnEditor 에 위임한다.
 * form.fields 의 name 과 매칭되지 않는 field 는 WARNING 만 — 클라이언트에서 강제 차단하지 않는다.
 */
const columns = defineModel<GridColumnMeta[]>('columns', { required: true });
const inlineEdit = defineModel<boolean>('inlineEdit', { default: false });
const exportEnabled = defineModel<boolean>('exportEnabled', { default: false });

const props = defineProps<{
  /** form.fields 의 name 목록 — 매칭 WARNING 판정에 사용(선택). */
  formFieldNames?: string[];
}>();

const issues = computed(() => validateGridColumns(columns.value, props.formFieldNames ?? []));
const dupFields = computed(() => new Set(duplicateFields(columns.value)));

function addColumn(): void {
  columns.value = [...columns.value, createBlankColumn(columns.value)];
}

function removeColumn(idx: number): void {
  columns.value = columns.value.filter((_, i) => i !== idx);
}

/** field 입력이 비었거나 식별자 규칙 위반/중복이면 인라인 에러. */
function fieldError(c: GridColumnMeta): string | null {
  const f = c.field?.trim() ?? '';
  if (!f) return 'field 는 필수입니다.';
  if (!isValidFieldName(f)) return '영문 소문자/언더스코어로 시작하는 식별자만 가능합니다.';
  if (dupFields.value.has(f)) return 'field 가 중복됩니다.';
  return null;
}

defineExpose({ issues });
</script>

<template>
  <div class="space-y-3">
    <!-- 그리드 전역 옵션 -->
    <Card>
      <CardContent class="flex flex-wrap items-center gap-x-6 gap-y-2 py-3">
        <p class="text-sm font-semibold">
          그리드 옵션
        </p>
        <div class="flex items-center gap-2">
          <Checkbox
            id="grid-inline-edit"
            :model-value="inlineEdit"
            @update:model-value="(val) => (inlineEdit = val === true)"
          />
          <Label
            for="grid-inline-edit"
            class="font-normal"
          >
            인라인 편집 (AG Grid 강제)
          </Label>
        </div>
        <div class="flex items-center gap-2">
          <Checkbox
            id="grid-export"
            :model-value="exportEnabled"
            @update:model-value="(val) => (exportEnabled = val === true)"
          />
          <Label
            for="grid-export"
            class="font-normal"
          >
            엑셀 export (AG Grid 강제)
          </Label>
        </div>
      </CardContent>
    </Card>

    <p class="text-[12px] text-foreground-muted">
      타입이 <span class="font-mono">status</span> · <span class="font-mono">priority</span> 인 컬럼은
      셀이 상태/우선순위 뱃지로 자동 렌더링됩니다.
    </p>

    <Card
      v-for="(c, idx) in columns"
      :key="idx"
    >
      <CardContent class="space-y-3 py-4">
        <div class="flex items-start justify-between gap-2">
          <p class="text-sm font-semibold">
            컬럼 {{ idx + 1 }}
            <span class="font-mono text-xs text-foreground-muted">{{ c.field || '(field 미지정)' }}</span>
            <span
              v-if="rendersAsBadge(c.type)"
              class="ml-1 text-xs text-info"
            >· 뱃지 렌더</span>
          </p>
          <Button
            variant="ghost"
            size="icon"
            aria-label="컬럼 삭제"
            @click="removeColumn(idx)"
          >
            <Trash2Icon class="size-4" />
          </Button>
        </div>

        <div class="grid gap-x-4 gap-y-3 md:grid-cols-2">
          <!-- field -->
          <div class="space-y-1">
            <Label :for="`c-field-${idx}`">field *</Label>
            <Input
              :id="`c-field-${idx}`"
              v-model="c.field"
              placeholder="예: ticketNo"
              autocomplete="off"
            />
            <p
              v-if="fieldError(c)"
              class="text-[12px] text-danger"
            >
              {{ fieldError(c) }}
            </p>
          </div>

          <!-- label -->
          <div class="space-y-1">
            <Label :for="`c-label-${idx}`">라벨 *</Label>
            <Input
              :id="`c-label-${idx}`"
              v-model="c.label"
              placeholder="예: 티켓 번호"
            />
          </div>

          <!-- type -->
          <div class="space-y-1">
            <Label :for="`c-type-${idx}`">타입 *</Label>
            <select
              :id="`c-type-${idx}`"
              :value="c.type"
              class="h-9 w-full rounded-md border border-border bg-surface px-2 text-sm text-foreground"
              @change="c.type = ($event.target as HTMLSelectElement).value as FieldType"
            >
              <option
                v-for="t in FIELD_TYPES"
                :key="t"
                :value="t"
              >
                {{ FIELD_TYPE_LABELS[t] }} ({{ t }})
              </option>
            </select>
          </div>

          <!-- width -->
          <div class="space-y-1">
            <Label :for="`c-width-${idx}`">너비(width, px)</Label>
            <Input
              :id="`c-width-${idx}`"
              v-model.number="c.width"
              type="number"
              min="1"
              placeholder="예: 120"
            />
          </div>

          <!-- flex -->
          <div class="space-y-1">
            <Label :for="`c-flex-${idx}`">flex (지정 시 width 무시)</Label>
            <Input
              :id="`c-flex-${idx}`"
              v-model.number="c.flex"
              type="number"
              min="0"
              placeholder="예: 1"
            />
          </div>

          <!-- pinned -->
          <div class="space-y-1">
            <Label :for="`c-pinned-${idx}`">고정(pinned)</Label>
            <select
              :id="`c-pinned-${idx}`"
              :value="pinnedValue(c)"
              class="h-9 w-full rounded-md border border-border bg-surface px-2 text-sm text-foreground"
              @change="setPinned(c, ($event.target as HTMLSelectElement).value as PinnedOption)"
            >
              <option
                v-for="p in PINNED_OPTIONS"
                :key="p.value"
                :value="p.value"
              >
                {{ p.label }}
              </option>
            </select>
          </div>

          <!-- hideAt -->
          <div class="space-y-1">
            <Label :for="`c-hide-${idx}`">반응형 숨김(hideAt)</Label>
            <select
              :id="`c-hide-${idx}`"
              :value="hideAtValue(c)"
              class="h-9 w-full rounded-md border border-border bg-surface px-2 text-sm text-foreground"
              @change="setHideAt(c, ($event.target as HTMLSelectElement).value as HideAtOption)"
            >
              <option
                v-for="h in HIDE_AT_OPTIONS"
                :key="h.value"
                :value="h.value"
              >
                {{ h.label }}
              </option>
            </select>
          </div>
        </div>
      </CardContent>
    </Card>

    <p
      v-if="columns.length === 0"
      class="rounded-md border border-dashed border-border py-8 text-center text-sm text-foreground-muted"
    >
      아직 컬럼이 없습니다. 아래에서 컬럼을 추가하세요.
    </p>

    <Button
      variant="outline"
      class="w-full"
      @click="addColumn"
    >
      <PlusIcon class="mr-1 size-4" /> 컬럼 추가
    </Button>
  </div>
</template>
