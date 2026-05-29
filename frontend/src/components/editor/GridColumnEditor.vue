<script setup lang="ts">
import { computed } from 'vue';
import { VueDraggable } from 'vue-draggable-plus';
import { Trash2Icon, PlusIcon, GripVerticalIcon } from '@lucide/vue';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { Card, CardContent } from '@/components/ui/card';
import { reorder } from '@/lib/drag';
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
  usesFlex,
  setFlexMode,
  setWidthMode,
  type PinnedOption,
  type HideAtOption,
} from '@/composables/useGridColumnEditor';

/**
 * 그리드 컬럼(grid.columns) GUI 편집기 (ADR-016 1·2단계).
 * JSON 직접 노출 없이 추가/삭제/속성 편집 + 순서 드래그(phase 14) + 폭(px/flex) 토글.
 * 순수 로직은 useGridColumnEditor·drag.ts 에 위임한다.
 * form.fields 의 name 과 매칭되지 않는 field 는 WARNING 만 — 클라이언트에서 강제 차단하지 않는다.
 *
 * 드래그 정책(phase 14 step 0): grip 핸들 전용·동일 리스트 내 reorder.
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

/**
 * 키보드 접근성 — 행 포커스 상태에서 Alt+↑/↓ 로 순서 변경.
 * 드래그(VueDraggable v-model)와 동일하게 reorder helper 로 새 배열을 만든다.
 */
function moveColumn(idx: number, dir: -1 | 1): void {
  columns.value = reorder(columns.value, idx, idx + dir);
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

    <!-- 그리드 헤더 미리보기 stripe — width/flex 비례 (실 데이터 fetch 없음, 시각만). -->
    <div
      v-if="columns.length > 0"
      class="flex gap-px overflow-hidden rounded-md border border-border bg-surface-muted"
      aria-hidden="true"
    >
      <div
        v-for="(c, idx) in columns"
        :key="idx"
        class="truncate border-r border-border-subtle px-2 py-1 text-[11px] font-semibold text-foreground-muted last:border-r-0"
        :style="
          usesFlex(c)
            ? { flexGrow: String(c.flex || 1), flexBasis: '0', minWidth: '0' }
            : { flexGrow: '0', flexShrink: '0', flexBasis: `${c.width || 100}px` }
        "
      >
        {{ c.label || c.field || `#${idx + 1}` }}
      </div>
    </div>

    <VueDraggable
      v-model="columns"
      tag="div"
      class="space-y-3"
      handle=".column-drag-handle"
      :animation="200"
      ghost-class="column-drag-ghost"
      chosen-class="column-drag-chosen"
    >
      <Card
        v-for="(c, idx) in columns"
        :key="idx"
      >
        <CardContent class="space-y-3 py-4">
          <div class="flex items-start justify-between gap-2">
            <div class="flex items-center gap-2">
              <button
                type="button"
                class="column-drag-handle cursor-grab text-foreground-subtle hover:text-foreground active:cursor-grabbing"
                aria-label="드래그하여 순서 변경"
                @keydown.alt.up.prevent="moveColumn(idx, -1)"
                @keydown.alt.down.prevent="moveColumn(idx, 1)"
              >
                <GripVerticalIcon class="size-4" />
              </button>
              <p class="text-sm font-semibold">
                컬럼 {{ idx + 1 }}
                <span class="font-mono text-xs text-foreground-muted">{{ c.field || '(field 미지정)' }}</span>
                <span
                  v-if="rendersAsBadge(c.type)"
                  class="ml-1 text-xs text-info"
                >· 뱃지 렌더</span>
              </p>
            </div>
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

            <!-- 폭 모드 — px 고정 너비 ↔ flex 채움 (둘 중 하나) -->
            <div class="space-y-1">
              <Label>폭 모드</Label>
              <div class="inline-flex rounded-md border border-border p-0.5">
                <button
                  type="button"
                  class="rounded-[6px] px-3 py-1 text-sm"
                  :class="
                    !usesFlex(c)
                      ? 'bg-primary text-primary-foreground'
                      : 'text-foreground-muted hover:text-foreground'
                  "
                  @click="setWidthMode(c)"
                >
                  px 너비
                </button>
                <button
                  type="button"
                  class="rounded-[6px] px-3 py-1 text-sm"
                  :class="
                    usesFlex(c)
                      ? 'bg-primary text-primary-foreground'
                      : 'text-foreground-muted hover:text-foreground'
                  "
                  @click="setFlexMode(c)"
                >
                  flex 채움
                </button>
              </div>
            </div>

            <!-- width (px 모드) / flex 비율 (flex 모드) -->
            <div
              v-if="!usesFlex(c)"
              class="space-y-1"
            >
              <Label :for="`c-width-${idx}`">너비(width, px)</Label>
              <Input
                :id="`c-width-${idx}`"
                v-model.number="c.width"
                type="number"
                min="1"
                placeholder="예: 120"
              />
            </div>
            <div
              v-else
              class="space-y-1"
            >
              <Label :for="`c-flex-${idx}`">flex 비율</Label>
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
    </VueDraggable>

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

<style scoped>
/* 드래그 중인 원본 행 — 반투명 (UI_GUIDE §9), FormFieldEditor 와 동일 규격. */
.column-drag-chosen {
  opacity: 0.5;
}

/* 드롭 위치 placeholder — 파선 indicator. */
.column-drag-ghost {
  border: 1px dashed var(--color-primary);
  opacity: 0.6;
}
</style>
