import { describe, it, expect } from 'vitest';
import {
  isFieldMeta,
  isFormMeta,
  isGridMeta,
  isPageMetaBody,
  asPageMetaBody,
  MetaBodyShapeError,
} from '@/lib/meta-body';
import type { FieldType } from '@/types/meta-body';

const ALL_FIELD_TYPES: readonly FieldType[] = [
  'text', 'textarea', 'number',
  'select', 'radio', 'checkbox',
  'date', 'date-range',
  'user-picker', 'file',
  'status', 'priority',
];

/**
 * 정상 PageMetaBody 샘플 — ARCHITECTURE §3-4 의 itg-ticket-v1-2 형태.
 * 가상 샘플만 사용한다(ADR-011).
 */
function sampleBody(): unknown {
  return {
    api: '/api/tickets',
    grid: {
      columns: [
        { field: 'ticketNo', label: '티켓번호', type: 'text', width: 140, pinned: 'left' },
        { field: 'title', label: '제목', type: 'text', flex: 1 },
        { field: 'status', label: '상태', type: 'status', width: 120 },
        { field: 'priority', label: '우선순위', type: 'priority', width: 120, hideAt: 'md' },
        { field: 'createdAt', label: '등록일', type: 'date', width: 160 },
      ],
    },
    form: {
      layout: 'two-column',
      fields: [
        { name: 'title', label: '제목', type: 'text', required: true, span: 2, maxLength: 200 },
        { name: 'content', label: '내용', type: 'textarea', span: 2 },
        {
          name: 'priority',
          label: '우선순위',
          type: 'select',
          required: true,
          options: [
            { value: 'LOW', label: '낮음' },
            { value: 'MEDIUM', label: '보통' },
            { value: 'HIGH', label: '높음' },
            { value: 'CRITICAL', label: '긴급' },
          ],
        },
        { name: 'assignee', label: '담당자', type: 'user-picker' },
      ],
    },
    actions: [
      { id: 'create', label: '신규 등록', type: 'dialog-form', to: 'itg-ticket-form' },
      { id: 'export', label: '엑셀 내보내기', type: 'export' },
    ],
  };
}

describe('isFieldType (간접 검증)', () => {
  it('isFieldType_지원되는_12종_true', () => {
    for (const t of ALL_FIELD_TYPES) {
      expect(isFieldMeta({ name: 'f', label: 'L', type: t })).toBe(true);
    }
  });

  it('isFieldType_지원되지_않는_문자열_false', () => {
    expect(isFieldMeta({ name: 'f', label: 'L', type: 'email' })).toBe(false);
    expect(isFieldMeta({ name: 'f', label: 'L', type: '' })).toBe(false);
    expect(isFieldMeta({ name: 'f', label: 'L', type: null })).toBe(false);
  });
});

describe('isFieldMeta', () => {
  it('isFieldMeta_name_label_type_있어야_true', () => {
    expect(isFieldMeta({ name: 'title', label: '제목', type: 'text' })).toBe(true);
  });

  it('isFieldMeta_type_없거나_unknown_false', () => {
    expect(isFieldMeta({ name: 'title', label: '제목' })).toBe(false);
    expect(isFieldMeta({ name: 'title', label: '제목', type: 'unknown' })).toBe(false);
    expect(isFieldMeta(null)).toBe(false);
    expect(isFieldMeta('text')).toBe(false);
  });
});

describe('isFormMeta', () => {
  it('isFormMeta_layout_과_fields_배열_검증', () => {
    expect(
      isFormMeta({
        layout: 'single-column',
        fields: [{ name: 'a', label: 'A', type: 'text' }],
      }),
    ).toBe(true);
    expect(
      isFormMeta({
        layout: 'two-column',
        fields: [{ name: 'a', label: 'A', type: 'text' }],
      }),
    ).toBe(true);
  });

  it('isFormMeta_layout_잘못_false', () => {
    expect(isFormMeta({ layout: 'three-column', fields: [] })).toBe(false);
    expect(isFormMeta({ fields: [] })).toBe(false);
    expect(isFormMeta({ layout: 'single-column', fields: 'nope' })).toBe(false);
    expect(
      isFormMeta({ layout: 'single-column', fields: [{ name: 'a', label: 'A' }] }),
    ).toBe(false);
  });
});

describe('isGridMeta', () => {
  it('isGridMeta_columns_배열_검증', () => {
    expect(
      isGridMeta({ columns: [{ field: 'a', label: 'A', type: 'text' }] }),
    ).toBe(true);
    expect(isGridMeta({ columns: 'nope' })).toBe(false);
    expect(isGridMeta({})).toBe(false);
    expect(
      isGridMeta({ columns: [{ field: 'a', label: 'A', type: 'bogus' }] }),
    ).toBe(false);
  });
});

describe('isPageMetaBody', () => {
  it('isPageMetaBody_정상_샘플_true', () => {
    expect(isPageMetaBody(sampleBody())).toBe(true);
  });

  it('isPageMetaBody_actions_누락_허용', () => {
    const body = sampleBody() as Record<string, unknown>;
    delete body.actions;
    expect(isPageMetaBody(body)).toBe(true);
  });

  it('isPageMetaBody_actions_있으면_각_요소_ActionMeta_검증', () => {
    const body = sampleBody() as Record<string, unknown>;
    body.actions = [{ id: 'x', label: 'X' }]; // type 누락
    expect(isPageMetaBody(body)).toBe(false);

    const body2 = sampleBody() as Record<string, unknown>;
    body2.actions = 'nope';
    expect(isPageMetaBody(body2)).toBe(false);
  });
});

describe('asPageMetaBody', () => {
  it('asPageMetaBody_실패시_MetaBodyShapeError_metaId_포함', () => {
    expect(() => asPageMetaBody('itg-ticket-v1-2', {})).toThrow(MetaBodyShapeError);
    try {
      asPageMetaBody('itg-ticket-v1-2', {});
    } catch (e) {
      expect(e).toBeInstanceOf(MetaBodyShapeError);
      expect((e as MetaBodyShapeError).metaId).toBe('itg-ticket-v1-2');
      expect((e as MetaBodyShapeError).message).toContain('itg-ticket-v1-2');
    }
  });

  it('asPageMetaBody_정상시_그대로_반환', () => {
    const body = sampleBody();
    expect(asPageMetaBody('itg-ticket-v1-2', body)).toBe(body);
  });
});
