import type { FieldMeta, GridColumnMeta } from '@/types/meta-body';

/**
 * WYSIWYG PoC(ADR-016 3단계 — Stretch) 의 인플레이스 편집 순수 로직.
 *
 * 실 화면 미리보기에서 라벨을 클릭해 직접 고치거나(인플레이스), 필드·컬럼을
 * 삭제하는 동작을 배열 변환 함수로 분리한다. 컴포넌트는 이 헬퍼만 호출하고
 * 결과 배열을 body draft 에 다시 배정한다 — 좌측 패널과 같은 배열을 공유하므로
 * 한쪽 편집이 즉시 양쪽에 반영된다.
 *
 * 정책:
 * - 모든 함수는 원본 배열을 변경하지 않고 새 배열을 반환한다(reorder 와 동일 규약).
 * - 범위를 벗어난 index 는 원본의 얕은 복사본을 그대로 반환한다(no-op).
 * - 본격 page builder 가 아닌 PoC 수준 — 라벨 인플레이스 편집 + 폭 + 삭제만 다룬다.
 */

/** index 가 0 이상이고 배열 범위 안인지. */
function inRange(len: number, idx: number): boolean {
  return Number.isInteger(idx) && idx >= 0 && idx < len;
}

/** 폼 필드(idx)의 라벨을 인플레이스로 교체한 새 배열. */
export function setFieldLabel(fields: FieldMeta[], idx: number, label: string): FieldMeta[] {
  const next = fields.slice();
  const target = next[idx];
  if (!target) return next;
  next[idx] = { ...target, label };
  return next;
}

/** 폼 필드(idx)를 삭제한 새 배열. */
export function removeFieldAt(fields: FieldMeta[], idx: number): FieldMeta[] {
  if (!inRange(fields.length, idx)) return fields.slice();
  return fields.filter((_, i) => i !== idx);
}

/** 그리드 컬럼(idx)의 헤더 라벨을 인플레이스로 교체한 새 배열. */
export function setColumnLabel(
  columns: GridColumnMeta[],
  idx: number,
  label: string,
): GridColumnMeta[] {
  const next = columns.slice();
  const target = next[idx];
  if (!target) return next;
  next[idx] = { ...target, label };
  return next;
}

/**
 * 그리드 컬럼(idx)의 폭(width, px)을 인플레이스로 교체한 새 배열.
 * px 너비를 지정하면 flex 모드를 해제한다(둘 중 하나, GridColumnEditor 와 동일 규칙).
 * 1 미만·비유한수는 무시(no-op 복사본 반환).
 */
export function setColumnWidth(
  columns: GridColumnMeta[],
  idx: number,
  width: number,
): GridColumnMeta[] {
  const next = columns.slice();
  const target = next[idx];
  if (!target) return next;
  if (!Number.isFinite(width) || width < 1) return next;
  // px 너비 지정 시 flex 모드 해제(둘 중 하나).
  const updated: GridColumnMeta = { ...target, width: Math.round(width) };
  delete updated.flex;
  next[idx] = updated;
  return next;
}

/** 그리드 컬럼(idx)을 삭제한 새 배열. */
export function removeColumnAt(columns: GridColumnMeta[], idx: number): GridColumnMeta[] {
  if (!inRange(columns.length, idx)) return columns.slice();
  return columns.filter((_, i) => i !== idx);
}
