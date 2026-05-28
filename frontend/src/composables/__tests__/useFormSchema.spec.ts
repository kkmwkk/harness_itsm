import { describe, it, expect } from 'vitest';
import { buildFormSchema } from '@/composables/useFormSchema';
import type { FieldMeta, FormMeta } from '@/types/meta-body';

/** 단일 필드 FormMeta 헬퍼 (가상 샘플만 사용 — ADR-011). */
function single(field: FieldMeta): FormMeta {
  return { layout: 'single-column', fields: [field] };
}

describe('buildFormSchema', () => {
  it('text_required_빈문자_invalid', () => {
    const schema = buildFormSchema(
      single({ name: 'title', label: '제목', type: 'text', required: true }),
    );
    expect(schema.safeParse({ title: '' }).success).toBe(false);
    expect(schema.safeParse({ title: '내용' }).success).toBe(true);
  });

  it('text_optional_빈문자_valid', () => {
    const schema = buildFormSchema(
      single({ name: 'memo', label: '메모', type: 'text' }),
    );
    expect(schema.safeParse({ memo: '' }).success).toBe(true);
    expect(schema.safeParse({}).success).toBe(true);
  });

  it('text_maxLength_초과_invalid', () => {
    const schema = buildFormSchema(
      single({ name: 'code', label: '코드', type: 'text', required: true, maxLength: 5 }),
    );
    expect(schema.safeParse({ code: 'abcde' }).success).toBe(true);
    expect(schema.safeParse({ code: 'abcdef' }).success).toBe(false);
  });

  it('number_min_max_경계_검증', () => {
    const schema = buildFormSchema(
      single({ name: 'qty', label: '수량', type: 'number', required: true, min: 1, max: 10 }),
    );
    expect(schema.safeParse({ qty: 0 }).success).toBe(false);
    expect(schema.safeParse({ qty: 1 }).success).toBe(true);
    expect(schema.safeParse({ qty: 10 }).success).toBe(true);
    expect(schema.safeParse({ qty: 11 }).success).toBe(false);
    // z.coerce.number — 문자열도 강제 변환
    expect(schema.safeParse({ qty: '5' }).success).toBe(true);
  });

  it('select_options_없어도_스키마_string_으로_생성', () => {
    const schema = buildFormSchema(
      single({ name: 'category', label: '분류', type: 'select', required: true }),
    );
    // 옵션 매칭은 UI 책임 — 스키마는 string 으로만 검증한다.
    expect(schema.safeParse({ category: 'ANY_VALUE' }).success).toBe(true);
    expect(schema.safeParse({ category: '' }).success).toBe(false);
  });

  it('checkbox_boolean_타입', () => {
    const schema = buildFormSchema(
      single({ name: 'agree', label: '동의', type: 'checkbox', required: true }),
    );
    expect(schema.safeParse({ agree: true }).success).toBe(true);
    expect(schema.safeParse({ agree: false }).success).toBe(true);
    expect(schema.safeParse({ agree: 'yes' }).success).toBe(false);
  });

  it('date-range_from_to_object_required', () => {
    const schema = buildFormSchema(
      single({ name: 'period', label: '기간', type: 'date-range', required: true }),
    );
    expect(
      schema.safeParse({ period: { from: '2026-01-01', to: '2026-01-31' } }).success,
    ).toBe(true);
    expect(schema.safeParse({ period: { from: '2026-01-01' } }).success).toBe(false);
  });

  it('buildFormSchema_field_여러개_object_생성', () => {
    const meta: FormMeta = {
      layout: 'two-column',
      fields: [
        { name: 'title', label: '제목', type: 'text', required: true },
        { name: 'category', label: '분류', type: 'select' },
        { name: 'qty', label: '수량', type: 'number' },
        { name: 'agree', label: '동의', type: 'checkbox' },
      ],
    };
    const schema = buildFormSchema(meta);
    expect(Object.keys(schema.shape)).toEqual(['title', 'category', 'qty', 'agree']);
    expect(Object.keys(schema.shape).length).toBe(meta.fields.length);
  });
});
