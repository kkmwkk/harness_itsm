import { describe, it, expect } from 'vitest';
import {
  FIELD_TYPES,
  PINNED_OPTIONS,
  HIDE_AT_OPTIONS,
  createBlankColumn,
  duplicateFields,
  rendersAsBadge,
  validateGridColumns,
  hasBlockingColumnIssues,
  pinnedValue,
  setPinned,
  hideAtValue,
  setHideAt,
} from '@/composables/useGridColumnEditor';
import type { GridColumnMeta } from '@/types/meta-body';

/**
 * GridColumnEditor 의 순수 로직(useGridColumnEditor) 검증.
 * vitest 환경이 node 라 DOM 마운트 대신 컴포넌트가 위임하는 로직을 직접 검증한다
 * (FormFieldEditor.spec 와 동일 전략).
 */

describe('FIELD_TYPES — FieldType 12종 (form 과 공유)', () => {
  it('12종이며 status·priority 를 포함한다', () => {
    expect(FIELD_TYPES).toHaveLength(12);
    expect(FIELD_TYPES).toContain('status');
    expect(FIELD_TYPES).toContain('priority');
  });
});

describe('PINNED_OPTIONS / HIDE_AT_OPTIONS', () => {
  it('pinned 은 none/left/right 3종', () => {
    expect(PINNED_OPTIONS.map((o) => o.value)).toEqual(['none', 'left', 'right']);
  });
  it('hideAt 은 none/sm/md 3종', () => {
    expect(HIDE_AT_OPTIONS.map((o) => o.value)).toEqual(['none', 'sm', 'md']);
  });
});

describe('createBlankColumn — 컬럼 추가', () => {
  it('빈 목록에서 기본 type=text 컬럼을 만든다', () => {
    const c = createBlankColumn([]);
    expect(c.type).toBe('text');
    expect(c.field).toBe('col_1');
  });

  it('기존 field 와 충돌하지 않는 field 를 생성한다', () => {
    const existing: GridColumnMeta[] = [
      { field: 'col_1', label: 'A', type: 'text' },
      { field: 'col_2', label: 'B', type: 'text' },
    ];
    const c = createBlankColumn(existing);
    expect(existing.map((e) => e.field)).not.toContain(c.field);
  });
});

describe('컬럼 삭제 — filter 로 인덱스 제거', () => {
  it('지정 인덱스를 제외한 배열을 만든다', () => {
    const columns: GridColumnMeta[] = [
      { field: 'a', label: 'A', type: 'text' },
      { field: 'b', label: 'B', type: 'text' },
      { field: 'c', label: 'C', type: 'text' },
    ];
    const next = columns.filter((_, i) => i !== 1);
    expect(next.map((c) => c.field)).toEqual(['a', 'c']);
  });
});

describe('duplicateFields — 중복 field 검증', () => {
  it('중복된 field 를 집어낸다', () => {
    const columns: GridColumnMeta[] = [
      { field: 'title', label: 'A', type: 'text' },
      { field: 'title', label: 'B', type: 'text' },
      { field: 'status', label: 'C', type: 'status' },
    ];
    expect(duplicateFields(columns)).toEqual(['title']);
  });

  it('공백 field 는 중복 판정에서 제외한다', () => {
    const columns: GridColumnMeta[] = [
      { field: '', label: 'A', type: 'text' },
      { field: '', label: 'B', type: 'text' },
    ];
    expect(duplicateFields(columns)).toEqual([]);
  });
});

describe('rendersAsBadge — status/priority 셀 뱃지 렌더', () => {
  it('status·priority 만 뱃지로 렌더된다', () => {
    expect(rendersAsBadge('status')).toBe(true);
    expect(rendersAsBadge('priority')).toBe(true);
    expect(rendersAsBadge('text')).toBe(false);
  });
});

describe('pinned/hideAt — undefined ↔ none 변환', () => {
  it('미지정 컬럼은 none 으로 표시된다', () => {
    const c: GridColumnMeta = { field: 'a', label: 'A', type: 'text' };
    expect(pinnedValue(c)).toBe('none');
    expect(hideAtValue(c)).toBe('none');
  });

  it('none 설정 시 키를 제거하고, 값 설정 시 반영한다', () => {
    const c: GridColumnMeta = { field: 'a', label: 'A', type: 'text' };
    setPinned(c, 'left');
    expect(c.pinned).toBe('left');
    setPinned(c, 'none');
    expect(c.pinned).toBeUndefined();
    setHideAt(c, 'md');
    expect(c.hideAt).toBe('md');
    setHideAt(c, 'none');
    expect(c.hideAt).toBeUndefined();
  });
});

describe('validateGridColumns — 검증 이슈', () => {
  it('정상 컬럼은 이슈가 없다', () => {
    const columns: GridColumnMeta[] = [{ field: 'title', label: '제목', type: 'text' }];
    expect(validateGridColumns(columns)).toHaveLength(0);
  });

  it('field 누락·라벨 누락은 ERROR', () => {
    const columns: GridColumnMeta[] = [{ field: '', label: '', type: 'text' }];
    const issues = validateGridColumns(columns);
    expect(issues.some((i) => i.level === 'ERROR')).toBe(true);
  });

  it('중복 field 는 ERROR 로 저장을 막는다', () => {
    const columns: GridColumnMeta[] = [
      { field: 'dup', label: 'A', type: 'text' },
      { field: 'dup', label: 'B', type: 'text' },
    ];
    expect(hasBlockingColumnIssues(columns)).toBe(true);
  });

  it('form.fields 의 name 과 매칭 안되는 field 는 WARNING (저장은 가능)', () => {
    const columns: GridColumnMeta[] = [{ field: 'ticketNo', label: '티켓번호', type: 'text' }];
    const issues = validateGridColumns(columns, ['title', 'content']);
    expect(issues.some((i) => i.level === 'WARNING')).toBe(true);
    expect(hasBlockingColumnIssues(columns, ['title', 'content'])).toBe(false);
  });

  it('매칭되는 field 는 WARNING 이 없다', () => {
    const columns: GridColumnMeta[] = [{ field: 'title', label: '제목', type: 'text' }];
    expect(validateGridColumns(columns, ['title', 'content'])).toHaveLength(0);
  });

  it('formFieldNames 가 비면 매칭 WARNING 을 내지 않는다', () => {
    const columns: GridColumnMeta[] = [{ field: 'ticketNo', label: '티켓번호', type: 'text' }];
    expect(validateGridColumns(columns, [])).toHaveLength(0);
  });
});
