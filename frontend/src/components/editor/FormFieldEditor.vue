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
import type { FieldMeta, FieldType } from '@/types/meta-body';
import {
  FIELD_TYPES,
  FIELD_TYPE_LABELS,
  needsOptions,
  isValidFieldName,
  createBlankField,
  duplicateNames,
  validateFormFields,
  toggleSpan,
} from '@/composables/useFormFieldEditor';

/**
 * 폼 필드(form.fields) GUI 편집기 (ADR-016 1·2단계).
 * JSON 직접 노출 없이 추가/삭제/속성 편집 + 순서 드래그(phase 14) + span 토글.
 * 순수 로직은 useFormFieldEditor·drag.ts 에 위임한다.
 *
 * 드래그 정책(phase 14 step 0): grip 핸들 전용·동일 리스트 내 reorder.
 */
const fields = defineModel<FieldMeta[]>('fields', { required: true });

const issues = computed(() => validateFormFields(fields.value));
const dupNames = computed(() => new Set(duplicateNames(fields.value)));

function addField(): void {
  fields.value = [...fields.value, createBlankField(fields.value)];
}

function removeField(idx: number): void {
  fields.value = fields.value.filter((_, i) => i !== idx);
}

/**
 * 키보드 접근성 — 행 포커스 상태에서 Alt+↑/↓ 로 순서 변경.
 * 드래그(VueDraggable v-model)와 동일하게 reorder helper 로 새 배열을 만든다.
 */
function moveField(idx: number, dir: -1 | 1): void {
  fields.value = reorder(fields.value, idx, idx + dir);
}

/** 폼 너비(span) — 전체 폭(2)로. 이미 전체 폭이면 그대로. */
function setSpanFull(f: FieldMeta): void {
  if (f.span !== 2) f.span = toggleSpan(f.span);
}

/** 폼 너비(span) — 반 폭(1)로. 이미 반 폭이면 그대로. */
function setSpanHalf(f: FieldMeta): void {
  if (f.span === 2) f.span = toggleSpan(f.span);
}

function onTypeChange(f: FieldMeta, type: FieldType): void {
  f.type = type;
  // 옵션이 필요 없는 타입으로 바뀌면 options 정리.
  if (!needsOptions(type)) f.options = undefined;
  else if (!f.options) f.options = [];
}

function addOption(f: FieldMeta): void {
  f.options = [...(f.options ?? []), { value: '', label: '' }];
}

function removeOption(f: FieldMeta, oi: number): void {
  f.options = (f.options ?? []).filter((_, i) => i !== oi);
}

/** name 입력이 비었거나 식별자 규칙 위반이면 인라인 에러 표시. */
function nameError(f: FieldMeta): string | null {
  const n = f.name?.trim() ?? '';
  if (!n) return 'name 은 필수입니다.';
  if (!isValidFieldName(n)) return '영문 소문자/언더스코어로 시작하는 식별자만 가능합니다.';
  if (dupNames.value.has(n)) return 'name 이 중복됩니다.';
  return null;
}

defineExpose({ issues });
</script>

<template>
  <div class="space-y-3">
    <VueDraggable
      v-model="fields"
      tag="div"
      class="space-y-3"
      handle=".field-drag-handle"
      :animation="200"
      ghost-class="field-drag-ghost"
      chosen-class="field-drag-chosen"
    >
      <Card
        v-for="(f, idx) in fields"
        :key="idx"
      >
        <CardContent class="space-y-3 py-4">
          <div class="flex items-start justify-between gap-2">
            <div class="flex items-center gap-2">
              <button
                type="button"
                class="field-drag-handle cursor-grab text-foreground-subtle hover:text-foreground active:cursor-grabbing"
                aria-label="드래그하여 순서 변경"
                @keydown.alt.up.prevent="moveField(idx, -1)"
                @keydown.alt.down.prevent="moveField(idx, 1)"
              >
                <GripVerticalIcon class="size-4" />
              </button>
              <p class="text-sm font-semibold">
                필드 {{ idx + 1 }}
                <span class="font-mono text-xs text-foreground-muted">{{ f.name || '(name 미지정)' }}</span>
              </p>
            </div>
            <Button
              variant="ghost"
              size="icon"
              aria-label="필드 삭제"
              @click="removeField(idx)"
            >
              <Trash2Icon class="size-4" />
            </Button>
          </div>

          <div class="grid gap-x-4 gap-y-3 md:grid-cols-2">
            <!-- 라벨 -->
            <div class="space-y-1">
              <Label :for="`f-label-${idx}`">라벨 *</Label>
              <Input
                :id="`f-label-${idx}`"
                v-model="f.label"
                placeholder="예: 제목"
              />
            </div>

            <!-- name -->
            <div class="space-y-1">
              <Label :for="`f-name-${idx}`">name *</Label>
              <Input
                :id="`f-name-${idx}`"
                v-model="f.name"
                placeholder="예: title"
                autocomplete="off"
              />
              <p
                v-if="nameError(f)"
                class="text-[12px] text-danger"
              >
                {{ nameError(f) }}
              </p>
            </div>

            <!-- type -->
            <div class="space-y-1">
              <Label :for="`f-type-${idx}`">타입 *</Label>
              <select
                :id="`f-type-${idx}`"
                :value="f.type"
                class="h-9 w-full rounded-md border border-border bg-surface px-2 text-sm text-foreground"
                @change="onTypeChange(f, ($event.target as HTMLSelectElement).value as FieldType)"
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

            <!-- span -->
            <div class="space-y-1">
              <Label>폼 너비(span)</Label>
              <div class="inline-flex rounded-md border border-border p-0.5">
                <button
                  type="button"
                  class="rounded-[6px] px-3 py-1 text-sm"
                  :class="
                    f.span !== 2
                      ? 'bg-primary text-primary-foreground'
                      : 'text-foreground-muted hover:text-foreground'
                  "
                  @click="setSpanHalf(f)"
                >
                  반 폭
                </button>
                <button
                  type="button"
                  class="rounded-[6px] px-3 py-1 text-sm"
                  :class="
                    f.span === 2
                      ? 'bg-primary text-primary-foreground'
                      : 'text-foreground-muted hover:text-foreground'
                  "
                  @click="setSpanFull(f)"
                >
                  전체 폭
                </button>
              </div>
            </div>

            <!-- required -->
            <div class="flex items-center gap-2 pt-5">
              <Checkbox
                :id="`f-req-${idx}`"
                :model-value="f.required ?? false"
                @update:model-value="(val) => (f.required = val === true)"
              />
              <Label
                :for="`f-req-${idx}`"
                class="font-normal"
              >
                필수 입력
              </Label>
            </div>

            <!-- placeholder -->
            <div class="space-y-1">
              <Label :for="`f-ph-${idx}`">placeholder</Label>
              <Input
                :id="`f-ph-${idx}`"
                v-model="f.placeholder"
                placeholder="입력 안내 문구"
              />
            </div>

            <!-- helpText -->
            <div class="space-y-1 md:col-span-2">
              <Label :for="`f-help-${idx}`">도움말(helpText)</Label>
              <Input
                :id="`f-help-${idx}`"
                v-model="f.helpText"
                placeholder="필드 하단 보조 설명"
              />
            </div>

            <!-- maxLength (text/textarea) -->
            <div
              v-if="f.type === 'text' || f.type === 'textarea'"
              class="space-y-1"
            >
              <Label :for="`f-max-${idx}`">최대 길이(maxLength)</Label>
              <Input
                :id="`f-max-${idx}`"
                v-model.number="f.maxLength"
                type="number"
                min="1"
              />
            </div>

            <!-- min/max (number) -->
            <template v-if="f.type === 'number'">
              <div class="space-y-1">
                <Label :for="`f-min-${idx}`">최솟값(min)</Label>
                <Input
                  :id="`f-min-${idx}`"
                  v-model.number="f.min"
                  type="number"
                />
              </div>
              <div class="space-y-1">
                <Label :for="`f-maxn-${idx}`">최댓값(max)</Label>
                <Input
                  :id="`f-maxn-${idx}`"
                  v-model.number="f.max"
                  type="number"
                />
              </div>
            </template>
          </div>

          <!-- options (select/radio) -->
          <div
            v-if="needsOptions(f.type)"
            class="space-y-2 rounded-md border border-border-subtle p-3"
          >
            <div class="flex items-center justify-between">
              <p class="text-xs font-semibold">
                옵션 (value · label)
              </p>
              <Button
                variant="ghost"
                size="sm"
                @click="addOption(f)"
              >
                <PlusIcon class="mr-1 size-3.5" /> 옵션 추가
              </Button>
            </div>
            <p
              v-if="!(f.options && f.options.length)"
              class="text-[12px] text-warning"
            >
              옵션이 없습니다. select/radio 필드는 옵션을 1개 이상 추가하세요.
            </p>
            <div
              v-for="(o, oi) in (f.options ?? [])"
              :key="oi"
              class="flex items-center gap-2"
            >
              <Input
                v-model="o.value"
                placeholder="value (영문 코드)"
                class="flex-1"
              />
              <Input
                v-model="o.label"
                placeholder="label (표시 한글)"
                class="flex-1"
              />
              <Button
                variant="ghost"
                size="icon"
                aria-label="옵션 삭제"
                @click="removeOption(f, oi)"
              >
                <Trash2Icon class="size-4" />
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </VueDraggable>

    <p
      v-if="fields.length === 0"
      class="rounded-md border border-dashed border-border py-8 text-center text-sm text-foreground-muted"
    >
      아직 필드가 없습니다. 아래에서 필드를 추가하세요.
    </p>

    <Button
      variant="outline"
      class="w-full"
      @click="addField"
    >
      <PlusIcon class="mr-1 size-4" /> 필드 추가
    </Button>
  </div>
</template>

<style scoped>
/* 드래그 중인 원본 행 — 반투명 + primary 보더 (UI_GUIDE §9). */
.field-drag-chosen {
  opacity: 0.5;
}

/* 드롭 위치 placeholder — 파선 indicator. */
.field-drag-ghost {
  border: 1px dashed var(--color-primary);
  opacity: 0.6;
}
</style>
