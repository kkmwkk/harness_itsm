import { describe, it, expect } from 'vitest';
import {
  decideRenderer,
  toDataTableColumns,
  toAgGridColDefs,
  AG_GRID_ROW_THRESHOLD,
} from '@/composables/useGridColumns';
import type { GridMeta } from '@/types/meta-body';

/** 가상 샘플 컬럼만 사용한다(ADR-011). */
function sampleMeta(overrides: Partial<GridMeta> = {}): GridMeta {
  return {
    columns: [
      { field: 'id', label: 'ID', type: 'text', width: 200 },
      { field: 'title', label: '제목', type: 'text', flex: 1 },
      { field: 'status', label: '상태', type: 'status', width: 120, pinned: 'left' },
    ],
    ...overrides,
  };
}

describe('decideRenderer', () => {
  it('decideRenderer_rows_1000_이하_data-table', () => {
    expect(decideRenderer(sampleMeta(), AG_GRID_ROW_THRESHOLD)).toBe('data-table');
    expect(decideRenderer(sampleMeta(), 35)).toBe('data-table');
  });

  it('decideRenderer_rows_1001_이상_ag-grid', () => {
    expect(decideRenderer(sampleMeta(), AG_GRID_ROW_THRESHOLD + 1)).toBe('ag-grid');
    expect(decideRenderer(sampleMeta(), 1500)).toBe('ag-grid');
  });

  it('decideRenderer_inlineEdit_true_면_rows_무관_ag-grid', () => {
    expect(decideRenderer(sampleMeta({ inlineEdit: true }), 1)).toBe('ag-grid');
  });

  it('decideRenderer_export_true_면_rows_무관_ag-grid', () => {
    expect(decideRenderer(sampleMeta({ export: true }), 1)).toBe('ag-grid');
  });
});

describe('toDataTableColumns', () => {
  it('toDataTableColumns_meta_컬럼_to_accessorKey_header_size_매핑', () => {
    const cols = toDataTableColumns(sampleMeta());
    expect(cols).toHaveLength(3);
    expect(cols[0]).toMatchObject({ accessorKey: 'id', header: 'ID', size: 200 });
    expect(cols[1]).toMatchObject({ accessorKey: 'title', header: '제목' });
    expect(cols[1]).toMatchObject({ size: undefined });
  });
});

describe('toAgGridColDefs', () => {
  it('toAgGridColDefs_meta_컬럼_to_field_headerName_width_flex_pinned_매핑', () => {
    const defs = toAgGridColDefs(sampleMeta());
    expect(defs).toHaveLength(3);
    expect(defs[0]).toMatchObject({ field: 'id', headerName: 'ID', width: 200 });
    expect(defs[1]).toMatchObject({ field: 'title', headerName: '제목', flex: 1 });
    expect(defs[2]).toMatchObject({ field: 'status', headerName: '상태', width: 120, pinned: 'left' });
    // 미지정 옵션은 키 자체가 없어야 한다(undefined 덮어쓰기 회피)
    expect('flex' in defs[0]!).toBe(false);
    expect('pinned' in defs[1]!).toBe(false);
  });
});
