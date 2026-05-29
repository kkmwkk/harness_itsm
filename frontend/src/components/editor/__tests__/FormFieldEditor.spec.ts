import { describe, it, expect } from 'vitest';
import {
  FIELD_TYPES,
  createBlankField,
  duplicateNames,
  isValidFieldName,
  needsOptions,
  validateFormFields,
  hasBlockingIssues,
} from '@/composables/useFormFieldEditor';
import type { FieldMeta } from '@/types/meta-body';

/**
 * FormFieldEditor 의 순수 로직(useFormFieldEditor) 검증.
 * vitest 환경이 node 라 DOM 마운트 대신 컴포넌트가 위임하는 로직을 직접 검증한다
 * (AssetCategoryTree.spec 와 동일 전략).
 */

describe('FIELD_TYPES — FieldType 12종', () => {
  it('12종이며 select·radio·checkbox 등을 포함한다', () => {
    expect(FIELD_TYPES).toHaveLength(12);
    expect(FIELD_TYPES).toContain('select');
    expect(FIELD_TYPES).toContain('user-picker');
  });
});

describe('createBlankField — 필드 추가', () => {
  it('빈 목록에서 기본 type=text 필드를 만든다', () => {
    const f = createBlankField([]);
    expect(f.type).toBe('text');
    expect(f.name).toBe('field_1');
    expect(f.span).toBe(1);
  });

  it('기존 name 과 충돌하지 않는 name 을 생성한다', () => {
    const existing: FieldMeta[] = [
      { name: 'field_1', label: 'A', type: 'text' },
      { name: 'field_2', label: 'B', type: 'text' },
    ];
    const f = createBlankField(existing);
    expect(existing.map((e) => e.name)).not.toContain(f.name);
  });
});

describe('필드 삭제 — filter 로 인덱스 제거', () => {
  it('지정 인덱스를 제외한 배열을 만든다', () => {
    const fields: FieldMeta[] = [
      { name: 'a', label: 'A', type: 'text' },
      { name: 'b', label: 'B', type: 'text' },
      { name: 'c', label: 'C', type: 'text' },
    ];
    const next = fields.filter((_, i) => i !== 1);
    expect(next.map((f) => f.name)).toEqual(['a', 'c']);
  });
});

describe('isValidFieldName — name 식별자 규칙', () => {
  it('snake_case·camelCase 를 허용한다', () => {
    expect(isValidFieldName('assignee_id')).toBe(true);
    expect(isValidFieldName('assigneeId')).toBe(true);
  });

  it('숫자 시작·하이픈·공백은 거부한다', () => {
    expect(isValidFieldName('1field')).toBe(false);
    expect(isValidFieldName('my-field')).toBe(false);
    expect(isValidFieldName('my field')).toBe(false);
    expect(isValidFieldName('')).toBe(false);
  });
});

describe('duplicateNames — 중복 name 검증', () => {
  it('중복된 name 을 집어낸다', () => {
    const fields: FieldMeta[] = [
      { name: 'title', label: 'A', type: 'text' },
      { name: 'title', label: 'B', type: 'text' },
      { name: 'content', label: 'C', type: 'text' },
    ];
    expect(duplicateNames(fields)).toEqual(['title']);
  });

  it('공백 name 은 중복 판정에서 제외한다', () => {
    const fields: FieldMeta[] = [
      { name: '', label: 'A', type: 'text' },
      { name: '', label: 'B', type: 'text' },
    ];
    expect(duplicateNames(fields)).toEqual([]);
  });
});

describe('needsOptions — select/radio 옵션 필요', () => {
  it('select·radio 만 옵션이 필요하다', () => {
    expect(needsOptions('select')).toBe(true);
    expect(needsOptions('radio')).toBe(true);
    expect(needsOptions('text')).toBe(false);
  });
});

describe('validateFormFields — 검증 이슈', () => {
  it('정상 필드는 이슈가 없다', () => {
    const fields: FieldMeta[] = [{ name: 'title', label: '제목', type: 'text' }];
    expect(validateFormFields(fields)).toHaveLength(0);
  });

  it('name 누락·라벨 누락은 ERROR', () => {
    const fields: FieldMeta[] = [{ name: '', label: '', type: 'text' }];
    const issues = validateFormFields(fields);
    expect(issues.some((i) => i.level === 'ERROR')).toBe(true);
  });

  it('중복 name 은 ERROR 로 저장을 막는다', () => {
    const fields: FieldMeta[] = [
      { name: 'dup', label: 'A', type: 'text' },
      { name: 'dup', label: 'B', type: 'text' },
    ];
    expect(hasBlockingIssues(fields)).toBe(true);
  });

  it('옵션 없는 select 는 WARNING (저장은 가능)', () => {
    const fields: FieldMeta[] = [{ name: 'cat', label: '분류', type: 'select' }];
    const issues = validateFormFields(fields);
    expect(issues.some((i) => i.level === 'WARNING')).toBe(true);
    expect(hasBlockingIssues(fields)).toBe(false);
  });

  it('옵션 있는 select 는 경고 없음', () => {
    const fields: FieldMeta[] = [
      { name: 'cat', label: '분류', type: 'select', options: [{ value: 'A', label: '에이' }] },
    ];
    expect(validateFormFields(fields)).toHaveLength(0);
  });
});
