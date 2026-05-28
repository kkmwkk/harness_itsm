import { z, type ZodTypeAny } from 'zod';
import type { FieldMeta, FormMeta } from '@/types/meta-body';

/**
 * FieldMeta 한 칸 → Zod 스키마.
 * ARCHITECTURE §5 의 필드 타입 매핑과 step0 의 FieldType 12종을 모두 다룬다.
 * - 날짜류는 ISO 8601 문자열로 통일한다(변환은 호출자 책임).
 * - select/radio 의 옵션 매칭은 UI 책임이며 스키마는 string 으로만 검증한다.
 */
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
      if (f.pattern) base = (base as z.ZodString).regex(new RegExp(f.pattern));
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
      base = z.string(); // ISO 8601 문자열로 통일. 변환은 호출자 책임.
      break;
    case 'date-range':
      base = z.object({ from: z.string(), to: z.string() });
      break;
  }

  // required 가 아니면 optional. required + string 은 빈 문자열을 거르기 위해 min(1).
  if (f.required) {
    if (base instanceof z.ZodString) base = base.min(1);
    return base;
  }
  return base.optional();
}

/** FormMeta → Zod object 스키마. fields 의 name 을 key 로 shape 를 구성한다. */
export function buildFormSchema(meta: FormMeta) {
  const shape: Record<string, ZodTypeAny> = {};
  for (const f of meta.fields) shape[f.name] = buildFieldSchema(f);
  return z.object(shape);
}
