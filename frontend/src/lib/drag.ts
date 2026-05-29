import { z } from 'zod';

/**
 * 드래그앤드롭(M10 / ADR-016 2단계) 공통 helper.
 *
 * vue-draggable-plus 의 drag end 이벤트(oldIndex/newIndex)를 받아
 * 배열을 재정렬한다. 원본 배열은 변경하지 않고 새 배열을 반환한다.
 *
 * 정책(phase 14 step 0):
 * - 같은 리스트 안에서만 순서 변경(필드·컬럼 reorder). 외부 리스트로 이동 X.
 * - 드래그 트리거는 grip 핸들(Lucide.GripVertical)만 — 행 전체 드래그 금지.
 */

/** 0 이상의 정수 index 스키마. */
const indexSchema = z.number().int().nonnegative();

/**
 * `list` 의 `oldIndex` 항목을 `newIndex` 위치로 이동한 새 배열을 반환한다.
 *
 * - 원본 `list` 는 불변(새 배열 반환).
 * - `oldIndex` / `newIndex` 가 정수·범위(0 ~ length-1)를 벗어나면
 *   재정렬 없이 원본의 얕은 복사본을 그대로 반환한다.
 */
export function reorder<T>(list: T[], oldIndex: number, newIndex: number): T[] {
  const next = list.slice();

  const oldOk = indexSchema.safeParse(oldIndex).success && oldIndex < list.length;
  const newOk = indexSchema.safeParse(newIndex).success && newIndex < list.length;
  if (!oldOk || !newOk || oldIndex === newIndex) {
    return next;
  }

  next.splice(newIndex, 0, ...next.splice(oldIndex, 1));
  return next;
}
