import { describe, it, expect } from 'vitest';
import { buildMockRows, MOCK_ROW_COUNT } from '@/composables/useMetaPreview';
import type { GridColumnMeta } from '@/types/meta-body';

/**
 * MetaPreview 의 순수 로직(useMetaPreview.buildMockRows) 검증.
 * vitest 환경이 node 라 DOM 마운트 대신 mockRows 생성 로직을 직접 검증한다.
 */

describe('buildMockRows — 가짜 미리보기 데이터', () => {
  const columns: GridColumnMeta[] = [
    { field: 'ticketNo', label: '티켓 번호', type: 'text' },
    { field: 'amount', label: '금액', type: 'number' },
  ];

  it('기본 5건을 만든다', () => {
    expect(buildMockRows(columns)).toHaveLength(MOCK_ROW_COUNT);
    expect(MOCK_ROW_COUNT).toBe(5);
  });

  it('count 인자로 행 수를 조절한다', () => {
    expect(buildMockRows(columns, 3)).toHaveLength(3);
  });

  it('각 컬럼 field 를 key 로, "샘플 {label} {n}" 값을 만든다', () => {
    const rows = buildMockRows(columns, 2);
    expect(rows).toEqual([
      { ticketNo: '샘플 티켓 번호 1', amount: 1 },
      { ticketNo: '샘플 티켓 번호 2', amount: 2 },
    ]);
  });

  it('number 타입은 숫자(n), 나머지는 샘플 문자열', () => {
    const rows = buildMockRows(columns, 1);
    expect(rows.map((r) => typeof r.amount)).toEqual(['number']);
    expect(rows.map((r) => typeof r.ticketNo)).toEqual(['string']);
    expect(rows.map((r) => String(r.ticketNo).startsWith('샘플 '))).toEqual([true]);
  });

  it('field 가 비어 있는 컬럼은 건너뛴다', () => {
    const cols: GridColumnMeta[] = [
      { field: '', label: '빈 컬럼', type: 'text' },
      { field: 'title', label: '제목', type: 'text' },
    ];
    const rows = buildMockRows(cols, 1);
    expect(rows.map((r) => Object.keys(r))).toEqual([['title']]);
  });

  it('컬럼이 없으면 빈 객체 행을 만든다', () => {
    const rows = buildMockRows([], 2);
    expect(rows).toEqual([{}, {}]);
  });

  it('label 이 없으면 field 로 대체한다', () => {
    const cols = [{ field: 'status', type: 'status' } as GridColumnMeta];
    const rows = buildMockRows(cols, 1);
    expect(rows.map((r) => r.status)).toEqual(['샘플 status 1']);
  });
});
