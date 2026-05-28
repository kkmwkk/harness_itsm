# Step 4: dynamic-form

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "절대 규칙" (검증 스키마는 Zod + `toTypedSchema`)
- `/docs/UI_GUIDE.md` — §5-6 폼 레이아웃 (라벨 필드 위, 14px/500, 필수 `*` red, 검증 메시지 하단 12px/400 red, 액션 우측 정렬 취소→저장)
- `/docs/ARCHITECTURE.md` — §5 필드 타입 매핑 (`text → Input`, `textarea → Textarea`, `select → SelectField`, ...)
- `/docs/ADR.md` — ADR-004 (No-code)
- `/phases/2-dynamic-render/step0.md` — `FieldMeta`·`FormMeta`·`FieldType` 정의

## 작업

이 step 의 목적은 **`FormMeta` 를 받아 Zod 스키마를 생성하고 VeeValidate 와 결합해 동적 폼을 렌더링하는 `DynamicForm` 컴포넌트를 만드는 것**이다. 필드 타입 12종 중 가장 흔한 6종(`text`/`textarea`/`number`/`select`/`radio`/`checkbox`)을 우선 지원. 나머지 6종(`date`/`date-range`/`user-picker`/`file`/`status`/`priority`) 은 placeholder 컴포넌트로 골격만.

### 1. 의존성

```bash
cd frontend
pnpm add vee-validate @vee-validate/zod zod
pnpm dlx shadcn-vue@latest add textarea select radio-group checkbox label
```

> `Label`/`Select`/`RadioGroup`/`Checkbox`/`Textarea` 5종 추가. 기존 `Input`·`Button` 은 phase 1.

### 2. composable — `src/composables/useFormSchema.ts`

`FormMeta` → Zod schema 생성:

```ts
import { z, type ZodTypeAny } from 'zod';
import type { FieldMeta, FormMeta } from '@/types/meta-body';

function buildFieldSchema(f: FieldMeta): ZodTypeAny {
  let base: ZodTypeAny;
  switch (f.type) {
    case 'text':
    case 'textarea':
    case 'select':
    case 'radio':
    case 'status':
    case 'priority':
    case 'user-picker':
    case 'file':
      base = z.string();
      if (f.maxLength) base = (base as z.ZodString).max(f.maxLength);
      if (f.pattern)   base = (base as z.ZodString).regex(new RegExp(f.pattern));
      break;
    case 'number':
      base = z.coerce.number();
      if (f.min !== undefined) base = (base as z.ZodNumber).min(f.min);
      if (f.max !== undefined) base = (base as z.ZodNumber).max(f.max);
      break;
    case 'checkbox':
      base = z.boolean();
      break;
    case 'date':
      base = z.string();   // ISO 8601 문자열로 통일. 변환은 호출자 책임.
      break;
    case 'date-range':
      base = z.object({ from: z.string(), to: z.string() });
      break;
  }

  if (!f.required) base = base.optional();
  return base;
}

export function buildFormSchema(meta: FormMeta) {
  const shape: Record<string, ZodTypeAny> = {};
  for (const f of meta.fields) shape[f.name] = buildFieldSchema(f);
  return z.object(shape);
}
```

### 3. 새 컴포넌트 — `src/components/dynamic/DynamicForm.vue`

Props:
- `meta: FormMeta`
- `initialValues?: Record<string, unknown>`

Emits:
- `submit`: `(values: Record<string, unknown>) => void`
- `cancel`: `() => void`

UI_GUIDE §5-6 준수:
- 단일 컬럼 또는 2 컬럼 그리드 (`meta.layout`). 2 컬럼 데스크탑(`md` 이상), 모바일 단일.
- 라벨 14px / 500 위에 배치, 필수 표시 `*` (text-danger).
- 검증 메시지 하단 12px / 400 text-danger. 도움말 text-foreground-muted 동일 위치.
- 액션 우측 정렬, 취소(outline) → 저장(default).

스켈레톤:

```vue
<script setup lang="ts">
import { computed } from 'vue';
import { useForm } from 'vee-validate';
import { toTypedSchema } from '@vee-validate/zod';
import { Input }       from '@/components/ui/input';
import { Textarea }    from '@/components/ui/textarea';
import { Checkbox }    from '@/components/ui/checkbox';
import { Label }       from '@/components/ui/label';
import { Button }      from '@/components/ui/button';
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem }
  from '@/components/ui/select';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { buildFormSchema } from '@/composables/useFormSchema';
import type { FormMeta, FieldMeta } from '@/types/meta-body';

interface Props {
  meta: FormMeta;
  initialValues?: Record<string, unknown>;
}
const props = defineProps<Props>();
const emit  = defineEmits<{
  submit: [values: Record<string, unknown>];
  cancel: [];
}>();

const schema = computed(() => toTypedSchema(buildFormSchema(props.meta)));
const { handleSubmit, errors, defineField, values } = useForm({
  validationSchema: schema,
  initialValues: props.initialValues,
});

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
  <form @submit="onSubmit" class="space-y-6">
    <div :class="gridLayoutClass()">
      <div v-for="f in meta.fields" :key="f.name" :class="fieldSpanClass(f)" class="space-y-1">
        <Label :for="f.name" class="text-[14px] font-medium">
          {{ f.label }}<span v-if="f.required" class="text-danger ml-0.5">*</span>
        </Label>

        <!-- text / number -->
        <Input v-if="['text','number','user-picker','file','status','priority'].includes(f.type)"
               :id="f.name" :type="f.type === 'number' ? 'number' : 'text'"
               :placeholder="f.placeholder"
               :modelValue="values[f.name]"
               @update:modelValue="(val) => (values as Record<string, unknown>)[f.name] = val" />

        <!-- textarea -->
        <Textarea v-else-if="f.type === 'textarea'"
                  :id="f.name" :placeholder="f.placeholder"
                  :modelValue="values[f.name]"
                  @update:modelValue="(val) => (values as Record<string, unknown>)[f.name] = val" />

        <!-- select -->
        <Select v-else-if="f.type === 'select'"
                :modelValue="values[f.name]"
                @update:modelValue="(val) => (values as Record<string, unknown>)[f.name] = val">
          <SelectTrigger :id="f.name">
            <SelectValue :placeholder="f.placeholder ?? '선택'" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem v-for="o in (f.options ?? [])" :key="o.value" :value="o.value">
              {{ o.label }}
            </SelectItem>
          </SelectContent>
        </Select>

        <!-- radio -->
        <RadioGroup v-else-if="f.type === 'radio'"
                    :modelValue="values[f.name]"
                    @update:modelValue="(val) => (values as Record<string, unknown>)[f.name] = val">
          <div v-for="o in (f.options ?? [])" :key="o.value" class="flex items-center gap-2">
            <RadioGroupItem :id="`${f.name}-${o.value}`" :value="o.value" />
            <Label :for="`${f.name}-${o.value}`" class="text-[14px] font-normal">{{ o.label }}</Label>
          </div>
        </RadioGroup>

        <!-- checkbox -->
        <div v-else-if="f.type === 'checkbox'" class="flex items-center gap-2">
          <Checkbox :id="f.name"
                    :modelValue="values[f.name]"
                    @update:modelValue="(val) => (values as Record<string, unknown>)[f.name] = val" />
          <Label :for="f.name" class="text-[14px] font-normal">{{ f.placeholder ?? '동의' }}</Label>
        </div>

        <!-- date / date-range / user-picker / file 등 (다음 phase 에서 정식 구현) -->
        <Input v-else
               :id="f.name" :type="f.type === 'date' ? 'date' : 'text'"
               :placeholder="`(${f.type}) ${f.placeholder ?? ''}`"
               :modelValue="values[f.name]"
               @update:modelValue="(val) => (values as Record<string, unknown>)[f.name] = val" />

        <p v-if="f.helpText && !errors[f.name]" class="text-[12px] text-foreground-muted">
          {{ f.helpText }}
        </p>
        <p v-if="errors[f.name]" class="text-[12px] text-danger">
          {{ errors[f.name] }}
        </p>
      </div>
    </div>

    <div class="flex justify-end gap-2">
      <Button type="button" variant="outline" @click="emit('cancel')">취소</Button>
      <Button type="submit">저장</Button>
    </div>
  </form>
</template>
```

> `defineField` 를 모든 필드마다 호출하는 정형화도 가능하지만, `values` reactive 객체 직접 바인딩 방식이 동적 필드 수에서 더 단순. ESLint 의 `consistent-type-imports` 와 type-check 통과 시 채택.

### 4. 단위 테스트 — `src/composables/__tests__/useFormSchema.spec.ts`

Vitest 케이스:

1. `text_required_빈문자_invalid`.
2. `text_optional_빈문자_valid`.
3. `text_maxLength_초과_invalid`.
4. `number_min_max_경계_검증`.
5. `select_options_없어도_스키마_string_으로_생성` — 실제 옵션 매칭은 UI 책임.
6. `checkbox_boolean_타입`.
7. `date-range_from_to_object_required`.
8. `buildFormSchema_field_여러개_object_생성` — `z.object` 의 shape 와 fields.length 일치.

### 5. 검증 페이지 — `src/views/_dev/DynamicFormSampler.vue`

라우트 `/_dev/dynamic-form`. 8 필드 mock (ARCHITECTURE §3-4 의 itg-ticket-v1-2 폼 + 추가):

```vue
<script setup lang="ts">
import { DynamicForm } from '@/components/dynamic/DynamicForm.vue';
import type { FormMeta } from '@/types/meta-body';

const meta: FormMeta = {
  layout: 'two-column',
  fields: [
    { name: 'title',    label: '제목',     type: 'text',     required: true, span: 2 },
    { name: 'category', label: '분류',     type: 'select',   options: [
        { value: 'BUG', label: '버그' }, { value: 'REQ', label: '요청' },
        { value: 'QNA', label: '문의' },
    ] },
    { name: 'priority', label: '우선순위', type: 'radio',    options: [
        { value: 'LOW',     label: '낮음' },
        { value: 'MEDIUM',  label: '보통' },
        { value: 'HIGH',    label: '높음' },
        { value: 'CRITICAL',label: '긴급' },
    ] },
    { name: 'dueDate',  label: '마감일',   type: 'date' },
    { name: 'assignee', label: '담당자',   type: 'user-picker' },
    { name: 'public',   label: '공개 여부', type: 'checkbox', placeholder: '외부에 공개' },
    { name: 'content',  label: '내용',     type: 'textarea', span: 2, helpText: '최대 2000자' },
  ],
};

function onSubmit(v: Record<string, unknown>) {
  console.warn('[DynamicFormSampler] submit', v);   // ESLint: warn 만 허용
}
</script>

<template>
  <main class="mx-auto max-w-3xl p-6 space-y-4">
    <h1>Dynamic Form Sampler</h1>
    <DynamicForm :meta="meta" @submit="onSubmit" />
  </main>
</template>
```

라우트:

```ts
{ path: '/_dev/dynamic-form', component: () => import('@/views/_dev/DynamicFormSampler.vue') },
```

### 6. common placeholder 컴포넌트 (선택)

`src/components/common/UserPicker.vue`·`DatePicker.vue` 를 placeholder 로 만들어 두면 다음 phase 의 정식 구현 위치가 명확. **본 step 에서는 생략 가능** — `Input` 으로 대체된 자리에 TODO 주석만 남긴다.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 의존성
grep -q '"vee-validate"' package.json
grep -q '"@vee-validate/zod"' package.json
grep -q '"zod"' package.json

# 2) shadcn 컴포넌트 5종 추가
test -d src/components/ui/textarea
test -d src/components/ui/select
test -d src/components/ui/radio-group
test -d src/components/ui/checkbox
test -d src/components/ui/label

# 3) 파일 존재
test -f src/composables/useFormSchema.ts
test -f src/components/dynamic/DynamicForm.vue
test -f src/composables/__tests__/useFormSchema.spec.ts
test -f src/views/_dev/DynamicFormSampler.vue
grep -q '/_dev/dynamic-form' src/router/index.ts

# 4) 정적 + 테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 5) dev 부팅 + 라우트 200
pnpm dev &
sleep 6
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/_dev/dynamic-form)" = "200"
kill %1
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크리스트:
   - `buildFormSchema` 가 ARCHITECTURE §5 + step 0 의 `FieldType` 12종을 모두 다루는가?
   - VeeValidate `toTypedSchema` + Zod 가 결합되었는가? (CLAUDE.md 절대 규칙)
   - UI_GUIDE §5-6 의 규칙(라벨 위·필수 `*`·검증 메시지 위치·액션 우측 정렬·취소→저장 순) 준수?
   - 필드 타입에 따라 shadcn 컴포넌트가 정확히 매핑되는가?
   - `meta.layout === 'two-column'` 시 `md` 이상에서 2열, 그 이하 단일?
   - 빨강(`text-danger`)은 필수 표시·에러 메시지에만 사용?
3. step 4 업데이트:
   - 성공 → `"summary": "vee-validate + @vee-validate/zod + zod 도입. shadcn 5종(textarea·select·radio-group·checkbox·label) 추가. useFormSchema(buildFormSchema FieldType 12종 → Zod) + DynamicForm.vue (layout single/two-column, 라벨 위 14px/500 + 필수 *, 검증 메시지 12px/400 danger, 액션 우측 정렬 취소→저장). 단위 테스트 8 케이스. DynamicFormSampler 8필드 mock."`

## 금지사항

- yup·joi·custom 검증을 도입하지 마라. 이유: CLAUDE.md 절대 규칙 — Zod 통일.
- `defineField` 의 자동 ref 분할 대신 `values[...]` 직접 바인딩 방식을 선택했더라도, 두 패턴을 한 컴포넌트 안에 혼용하지 마라. 일관성 우선.
- 빨강·노랑·초록을 인터랙션 액센트(`hover`·`focus`)에 사용하지 마라. UI_GUIDE — 시맨틱 색은 상태 전용.
- date/date-range 필드의 풀 구현(달력 위젯) 을 이 step 에서 만들지 마라. placeholder 텍스트 input 으로 충분. 다음 phase ADR.
- user-picker 의 실 사용자 검색 API 호출을 만들지 마라. text input placeholder 로 충분.
- 폼 submit 시 backend 호출 코드를 넣지 마라. emit 만. 호출은 DynamicPage(step 5) 또는 호출자.
- backend 코드 수정 금지.
- mock 데이터에 실 운영 이름·이메일·서버명 넣지 마라.
