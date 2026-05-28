import { computed, type Ref } from 'vue';
import type { ColumnDef } from '@tanstack/vue-table';
import type { ColDef } from 'ag-grid-community';
import type { GridMeta } from '@/types/meta-body';

export type GridRenderer = 'data-table' | 'ag-grid';

/** rows 임계치 — ADR-007. 매직넘버는 한 곳에. */
export const AG_GRID_ROW_THRESHOLD = 1000;

/**
 * 렌더러 자동 선택 — ARCHITECTURE §6 / ADR-007.
 * 인라인 편집·엑셀 export 는 행 수 무관 AG Grid, 1000행 초과도 AG Grid, 그 외 shadcn DataTable.
 */
export function decideRenderer(meta: GridMeta, rowCount: number): GridRenderer {
  if (meta.inlineEdit) return 'ag-grid';
  if (meta.export) return 'ag-grid';
  if (rowCount > AG_GRID_ROW_THRESHOLD) return 'ag-grid';
  return 'data-table';
}

export function toDataTableColumns<TData>(meta: GridMeta): ColumnDef<TData, unknown>[] {
  return meta.columns.map((c) => ({
    accessorKey: c.field,
    header: c.label,
    size: c.width,
    // pinned/flex/hideAt 등은 단순 매핑 — 상세 처리는 후속 phase
  }));
}

export function toAgGridColDefs(meta: GridMeta): ColDef[] {
  return meta.columns.map((c) => {
    const col: ColDef = {
      field: c.field,
      headerName: c.label,
    };
    if (c.width !== undefined) col.width = c.width;
    if (c.flex !== undefined) col.flex = c.flex;
    if (c.pinned !== undefined) col.pinned = c.pinned;
    return col;
  });
}

/** 단위 테스트 친화: 단일 진입 composable */
export function useGridColumns<TData>(meta: Ref<GridMeta>, rows: Ref<TData[]>) {
  const renderer = computed<GridRenderer>(() =>
    decideRenderer(meta.value, rows.value.length),
  );
  const dataTableColumns = computed(() => toDataTableColumns<TData>(meta.value));
  const agGridColDefs = computed(() => toAgGridColDefs(meta.value));
  return { renderer, dataTableColumns, agGridColDefs };
}
