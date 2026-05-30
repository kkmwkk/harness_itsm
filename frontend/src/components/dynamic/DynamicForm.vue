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
import DatePicker from '@/components/common/DatePicker.vue';
import DateRangePicker from '@/components/common/DateRangePicker.vue';
import UserPicker from '@/components/common/UserPicker.vue';
import FileUpload from '@/components/common/FileUpload.vue';
import MarkdownEditor from '@/components/common/MarkdownEditor.vue';
import SliderInput from '@/components/common/SliderInput.vue';
import { buildFormSchema } from '@/composables/useFormSchema';
import { widgetFor } from '@/lib/form-widget';
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

/** 폼 값을 컴포넌트 모델 타입으로 좁힌다 — 템플릿 안 union 캐스트(`|`)는 Vue 필터로 오인되므로 스크립트에서 처리. */
function strValue(name: string): string | number | undefined {
  const v = values[name];
  return typeof v === 'string' || typeof v === 'number' ? v : undefined;
}
/** string 전용 위젯(DatePicker·MarkdownEditor·UserPicker·FileUpload)용 — number 를 배제한다. */
function textValue(name: string): string | undefined {
  const v = values[name];
  return typeof v === 'string' ? v : undefined;
}
function numValue(name: string): number | undefined {
  const v = values[name];
  return typeof v === 'number' ? v : typeof v === 'string' && v !== '' ? Number(v) : undefined;
}
function boolValue(name: string): boolean | undefined {
  const v = values[name];
  return typeof v === 'boolean' ? v : undefined;
}
function rangeValue(name: string): { from?: string; to?: string } | null {
  const v = values[name];
  return v && typeof v === 'object' ? v : null;
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

        <!-- text / number / (options 없는 status·priority) -->
        <Input
          v-if="widgetFor(f) === 'input'"
          :id="f.name"
          :type="f.type === 'number' ? 'number' : 'text'"
          :placeholder="f.placeholder"
          :model-value="strValue(f.name)"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        />

        <!-- number + slider -->
        <SliderInput
          v-else-if="widgetFor(f) === 'slider'"
          :id="f.name"
          :model-value="numValue(f.name)"
          :min="f.min"
          :max="f.max"
          :step="f.step"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        />

        <!-- textarea + markdown -->
        <MarkdownEditor
          v-else-if="widgetFor(f) === 'markdown'"
          :id="f.name"
          :placeholder="f.placeholder"
          :model-value="textValue(f.name)"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        />

        <!-- textarea -->
        <Textarea
          v-else-if="widgetFor(f) === 'textarea'"
          :id="f.name"
          :placeholder="f.placeholder"
          :model-value="strValue(f.name)"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        />

        <!-- date -->
        <DatePicker
          v-else-if="widgetFor(f) === 'date'"
          :id="f.name"
          :placeholder="f.placeholder"
          :model-value="textValue(f.name)"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        />

        <!-- date-range -->
        <DateRangePicker
          v-else-if="widgetFor(f) === 'date-range'"
          :id="f.name"
          :model-value="rangeValue(f.name)"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        />

        <!-- user-picker -->
        <UserPicker
          v-else-if="widgetFor(f) === 'user-picker'"
          :id="f.name"
          :placeholder="f.placeholder"
          :model-value="textValue(f.name)"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        />

        <!-- file -->
        <FileUpload
          v-else-if="widgetFor(f) === 'file'"
          :id="f.name"
          :multiple="f.multiple"
          :accept="f.accept"
          :model-value="textValue(f.name)"
          @update:model-value="(val) => setFieldValue(f.name, val)"
        />

        <!-- select / (options 있는 status·priority) -->
        <Select
          v-else-if="widgetFor(f) === 'select'"
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
          v-else-if="widgetFor(f) === 'radio'"
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
          v-else-if="widgetFor(f) === 'checkbox'"
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
