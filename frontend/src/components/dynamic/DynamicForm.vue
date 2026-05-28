<script setup lang="ts">
import { computed } from 'vue';
import { useForm } from 'vee-validate';
import { toTypedSchema } from '@vee-validate/zod';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectTrigger,
  SelectValue,
  SelectContent,
  SelectItem,
} from '@/components/ui/select';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { buildFormSchema } from '@/composables/useFormSchema';
import type { FormMeta, FieldMeta } from '@/types/meta-body';

interface Props {
  meta: FormMeta;
  initialValues?: Record<string, unknown>;
}
const props = defineProps<Props>();
const emit = defineEmits<{
  submit: [values: Record<string, unknown>];
  cancel: [];
}>();

const schema = computed(() => toTypedSchema(buildFormSchema(props.meta)));
const { handleSubmit, errors, values, setFieldValue } = useForm({
  validationSchema: schema,
  initialValues: props.initialValues,
});

/** plain Input 으로 렌더링하는 타입(코어 text/number + 다음 phase 정식 구현 전 placeholder). */
const TEXT_LIKE: ReadonlyArray<FieldMeta['type']> = [
  'text',
  'number',
  'user-picker',
  'file',
  'status',
  'priority',
];

/** 폼 값을 컴포넌트 모델 타입으로 좁힌다 — 템플릿 안 union 캐스트(`|`)는 Vue 필터로 오인되므로 스크립트에서 처리. */
function strValue(name: string): string | number | undefined {
  const v = values[name];
  return typeof v === 'string' || typeof v === 'number' ? v : undefined;
}
function boolValue(name: string): boolean | undefined {
  const v = values[name];
  return typeof v === 'boolean' ? v : undefined;
}

function fieldSpanClass(f: FieldMeta): string {
  return f.span === 2 ? 'md:col-span-2' : '';
}
function gridLayoutClass(): string {
  return props.meta.layout === 'two-column'
    ? 'grid gap-x-6 gap-y-4 md:grid-cols-2'
    : 'grid gap-4';
}

const onSubmit = handleSubmit((v) => emit('submit', v));
</script>

<template>
  <form
    class="space-y-6"
    @submit="onSubmit"
  >
    <div :class="gridLayoutClass()">
      <div
        v-for="f in meta.fields"
        :key="f.name"
        :class="fieldSpanClass(f)"
        class="space-y-1"
      >
        <Label
          :for="f.name"
          class="text-[14px] font-medium"
        >
          {{ f.label }}<span
            v-if="f.required"
            class="text-danger ml-0.5"
          >*</span>
        </Label>

        <!-- text / number / (placeholder: user-picker·file·status·priority — 다음 phase 정식 구현) -->
        <Input
          v-if="TEXT_LIKE.includes(f.type)"
          :id="f.name"
          :type="f.type === 'number' ? 'number' : 'text'"
          :placeholder="f.placeholder"
          :model-value="strValue(f.name)"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        />

        <!-- textarea -->
        <Textarea
          v-else-if="f.type === 'textarea'"
          :id="f.name"
          :placeholder="f.placeholder"
          :model-value="strValue(f.name)"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        />

        <!-- select -->
        <Select
          v-else-if="f.type === 'select'"
          :model-value="strValue(f.name)"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        >
          <SelectTrigger :id="f.name">
            <SelectValue :placeholder="f.placeholder ?? '선택'" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem
              v-for="o in (f.options ?? [])"
              :key="o.value"
              :value="o.value"
            >
              {{ o.label }}
            </SelectItem>
          </SelectContent>
        </Select>

        <!-- radio -->
        <RadioGroup
          v-else-if="f.type === 'radio'"
          :model-value="strValue(f.name)"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        >
          <div
            v-for="o in (f.options ?? [])"
            :key="o.value"
            class="flex items-center gap-2"
          >
            <RadioGroupItem
              :id="`${f.name}-${o.value}`"
              :value="o.value"
            />
            <Label
              :for="`${f.name}-${o.value}`"
              class="text-[14px] font-normal"
            >{{ o.label }}</Label>
          </div>
        </RadioGroup>

        <!-- checkbox -->
        <div
          v-else-if="f.type === 'checkbox'"
          class="flex items-center gap-2"
        >
          <Checkbox
            :id="f.name"
            :model-value="boolValue(f.name)"
            @update:model-value="(val) => setFieldValue(f.name, val)"
          />
          <Label
            :for="f.name"
            class="text-[14px] font-normal"
          >{{ f.placeholder ?? '동의' }}</Label>
        </div>

        <!-- date / date-range 등 (다음 phase 에서 DatePicker·DateRangePicker 정식 구현) -->
        <Input
          v-else
          :id="f.name"
          :type="f.type === 'date' ? 'date' : 'text'"
          :placeholder="`(${f.type}) ${f.placeholder ?? ''}`"
          :model-value="strValue(f.name)"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        />

        <p
          v-if="f.helpText && !errors[f.name]"
          class="text-[12px] text-foreground-muted"
        >
          {{ f.helpText }}
        </p>
        <p
          v-if="errors[f.name]"
          class="text-[12px] text-danger"
        >
          {{ errors[f.name] }}
        </p>
      </div>
    </div>

    <div class="flex justify-end gap-2">
      <Button
        type="button"
        variant="outline"
        @click="emit('cancel')"
      >
        취소
      </Button>
      <Button type="submit">
        저장
      </Button>
    </div>
  </form>
</template>
