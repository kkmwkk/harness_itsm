import type { GridColumnMeta } from '@/types/meta-body';

/**
 * MetaPreview.vue 의 순수 로직 — 그리드 컬럼 정의에서 가짜(샘플) 행을 만든다.
 * 미리보기 전용이므로 실 운영 데이터는 절대 쓰지 않는다(CLAUDE.md — "샘플 " 접두사).
 */

/** 미리보기 기본 행 수. */
export const MOCK_ROW_COUNT = 5;

/** 컬럼 타입별 샘플 셀 값 — number 만 숫자, 나머지는 "샘플 {label} {n}". */
function mockCellValue(col: GridColumnMeta, n: number): unknown {
  const label = col.label?.trim() || col.field;
  switch (col.type) {
    case 'number':
      return n;
    default:
      return `샘플 ${label} ${n}`;
  }
}

/**
 * grid.columns 에서 추론한 가짜 데이터 행을 만든다.
 * 각 행은 컬럼 field 를 key 로, "샘플 {label} {n}"(number 는 n) 을 값으로 갖는다.
 * field 가 비어 있는 컬럼은 건너뛴다.
 */
export function buildMockRows(
  columns: GridColumnMeta[],
  count: number = MOCK_ROW_COUNT,
): Record<string, unknown>[] {
  const rows: Record<string, unknown>[] = [];
  for (let n = 1; n <= count; n += 1) {
    const row: Record<string, unknown> = {};
    for (const c of columns) {
      const field = c.field?.trim();
      if (!field) continue;
      row[field] = mockCellValue(c, n);
    }
    rows.push(row);
  }
  return rows;
}
