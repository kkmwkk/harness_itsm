import type { GridColumnMeta, GridMeta, FieldType } from '@/types/meta-body';
import { FIELD_TYPES, FIELD_TYPE_LABELS, isValidFieldName } from '@/composables/useFormFieldEditor';

/**
 * 그리드 컬럼 편집기(GridColumnEditor.vue) 의 순수 로직.
 * FormFieldEditor 와 동일하게 UI 와 분리해 단위 테스트 가능하도록 둔다.
 * (ADR-016 1단계 — JSON 직접 편집 없이 GUI 로 grid.columns 를 다룬다.)
 * FieldType 12종·식별자 규칙은 useFormFieldEditor 와 공유한다.
 */

export { FIELD_TYPES, FIELD_TYPE_LABELS, isValidFieldName };

/** pinned 선택지 — GridColumnMeta.pinned 는 undefined(none)|left|right. */
export type PinnedOption = 'none' | 'left' | 'right';
export const PINNED_OPTIONS: ReadonlyArray<{ value: PinnedOption; label: string }> = [
  { value: 'none', label: '고정 안 함' },
  { value: 'left', label: '왼쪽 고정' },
  { value: 'right', label: '오른쪽 고정' },
];

/** hideAt 선택지 — GridColumnMeta.hideAt 는 undefined(none)|sm|md (UI_GUIDE §6-3). */
export type HideAtOption = 'none' | 'sm' | 'md';
export const HIDE_AT_OPTIONS: ReadonlyArray<{ value: HideAtOption; label: string }> = [
  { value: 'none', label: '항상 표시' },
  { value: 'sm', label: 'sm 이하 숨김' },
  { value: 'md', label: 'md 이하 숨김' },
];

/** type=status/priority 셀은 뱃지로 자동 렌더(useGridColumns 매핑). 가이드 안내용. */
export const BADGE_TYPES: readonly FieldType[] = ['status', 'priority'] as const;
export function rendersAsBadge(type: FieldType): boolean {
  return BADGE_TYPES.includes(type);
}

/** 기존 컬럼과 충돌하지 않는 신규 컬럼(기본 type=text)을 만든다. */
export function createBlankColumn(existing: GridColumnMeta[]): GridColumnMeta {
  const taken = new Set(existing.map((c) => c.field));
  let i = existing.length + 1;
  let field = `col_${i}`;
  while (taken.has(field)) {
    i += 1;
    field = `col_${i}`;
  }
  return { field, label: `컬럼 ${i}`, type: 'text' };
}

/** 중복된 field 목록(공백 field 는 제외). */
export function duplicateFields(columns: GridColumnMeta[]): string[] {
  const seen = new Set<string>();
  const dup = new Set<string>();
  for (const c of columns) {
    const f = c.field.trim();
    if (!f) continue;
    if (seen.has(f)) dup.add(f);
    seen.add(f);
  }
  return [...dup];
}

/**
 * 컬럼 폭 모드 — flex 사용 여부 (phase 14 step 2).
 * meta-body 주석대로 flex 가 지정되면 width 는 무시된다. 둘을 동시에 두지 않는다.
 */
export function usesFlex(col: GridColumnMeta): boolean {
  return col.flex !== undefined && col.flex !== null;
}
/** flex(채움) 모드로 전환 — flex=1 지정, width 제거. */
export function setFlexMode(col: GridColumnMeta): void {
  delete col.width;
  if (!usesFlex(col)) col.flex = 1;
}
/** px(고정 너비) 모드로 전환 — flex 제거(width 는 유지). */
export function setWidthMode(col: GridColumnMeta): void {
  delete col.flex;
}

/** pinned/hideAt 의 undefined ↔ 'none' 변환 헬퍼 (UI select 값과 메타 형태를 잇는다). */
export function pinnedValue(col: GridColumnMeta): PinnedOption {
  return col.pinned ?? 'none';
}
export function setPinned(col: GridColumnMeta, value: PinnedOption): void {
  if (value === 'none') delete col.pinned;
  else col.pinned = value;
}
export function hideAtValue(col: GridColumnMeta): HideAtOption {
  return col.hideAt ?? 'none';
}
export function setHideAt(col: GridColumnMeta, value: HideAtOption): void {
  if (value === 'none') delete col.hideAt;
  else col.hideAt = value;
}

export interface ColumnIssue {
  level: 'ERROR' | 'WARNING';
  message: string;
}

/**
 * 클라이언트 측 사전 검증.
 * - ERROR: field 필수·식별자 규칙·label 필수·type 필수·field 중복 → 저장 차단.
 * - WARNING: form.fields 의 name 과 매칭되지 않는 field → 안내만(서버 dry-run 도 동일, 차단 안 함).
 *   (응답 전용 컬럼 id·ticketNo·createdAt 등은 정상적으로 매칭되지 않을 수 있다.)
 */
export function validateGridColumns(
  columns: GridColumnMeta[],
  formFieldNames: string[] = [],
): ColumnIssue[] {
  const issues: ColumnIssue[] = [];
  const formNames = new Set(formFieldNames.map((n) => n.trim()).filter(Boolean));
  columns.forEach((c, idx) => {
    const field = c.field?.trim() ?? '';
    const label = c.label?.trim() || field || `#${idx + 1}`;
    if (!field) {
      issues.push({ level: 'ERROR', message: `${label}: field 는 필수입니다.` });
    } else if (!isValidFieldName(field)) {
      issues.push({
        level: 'ERROR',
        message: `${label}: field 는 영문 소문자로 시작하는 식별자여야 합니다.`,
      });
    } else if (formNames.size > 0 && !formNames.has(field)) {
      issues.push({
        level: 'WARNING',
        message: `${label}: field '${field}' 가 폼 필드(name)와 매칭되지 않습니다.`,
      });
    }
    if (!c.label?.trim()) {
      issues.push({ level: 'ERROR', message: `${field || `#${idx + 1}`}: 라벨은 필수입니다.` });
    }
    if (!c.type) {
      issues.push({ level: 'ERROR', message: `${label}: 타입은 필수입니다.` });
    }
  });
  for (const f of duplicateFields(columns)) {
    issues.push({ level: 'ERROR', message: `field '${f}' 이(가) 중복됩니다.` });
  }
  return issues;
}

/** ERROR 가 하나라도 있으면 저장 불가(WARNING 은 저장 가능). */
export function hasBlockingColumnIssues(
  columns: GridColumnMeta[],
  formFieldNames: string[] = [],
): boolean {
  return validateGridColumns(columns, formFieldNames).some((i) => i.level === 'ERROR');
}

/** 빈 grid 골격(편집 진입 시 grid 가 없을 때 기본값). */
export function emptyGrid(): GridMeta {
  return { columns: [] };
}
