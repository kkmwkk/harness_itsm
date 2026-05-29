import { describe, it, expect } from 'vitest';
import { reorder } from '@/lib/drag';

describe('reorder', () => {
  it('reorder_정상_시_새_배열_반환_원본_불변', () => {
    const original = ['a', 'b', 'c', 'd'];
    const result = reorder(original, 0, 2);

    // 0번('a')이 2번 위치로 이동
    expect(result).toEqual(['b', 'c', 'a', 'd']);
    // 원본 불변
    expect(original).toEqual(['a', 'b', 'c', 'd']);
    // 새 배열(참조 다름)
    expect(result).not.toBe(original);
  });

  it('reorder_뒤에서_앞으로_이동', () => {
    const original = ['a', 'b', 'c', 'd'];
    expect(reorder(original, 3, 1)).toEqual(['a', 'd', 'b', 'c']);
  });

  it('reorder_범위_외_index_무시', () => {
    const original = ['a', 'b', 'c'];

    // 음수 index
    expect(reorder(original, -1, 1)).toEqual(['a', 'b', 'c']);
    // length 이상 index
    expect(reorder(original, 0, 5)).toEqual(['a', 'b', 'c']);
    expect(reorder(original, 9, 0)).toEqual(['a', 'b', 'c']);
    // 정수 아님
    expect(reorder(original, 0.5, 1)).toEqual(['a', 'b', 'c']);
    // 동일 index — no-op
    expect(reorder(original, 1, 1)).toEqual(['a', 'b', 'c']);

    // 무시되어도 원본 불변 + 새 배열 반환
    const result = reorder(original, -1, 1);
    expect(result).not.toBe(original);
  });
});
