import { describe, it, expect } from 'vitest';
import {
  AVATAR_PALETTE,
  avatarInitials,
  avatarColorIndex,
  avatarColor,
} from '@/lib/avatar';

/**
 * 아바타 순수 로직(UI_GUIDE §5-2) — 이니셜 추출과 결정적 해시 색 매핑 검증.
 * 색 값 하드코딩 없이 토큰 기반 Tailwind 유틸만 팔레트로 쓰는지 확인한다.
 */
describe('avatarInitials', () => {
  it.each([
    ['user-sample-1', 'US'],
    ['admin', 'AD'],
    ['it support', 'IS'],
    ['홍길동', '홍길'],
    ['a.b.c', 'AB'],
  ])('%s → %s', (input, expected) => {
    expect(avatarInitials(input)).toBe(expected);
  });

  it('빈 값·null·undefined 는 ?', () => {
    expect(avatarInitials('')).toBe('?');
    expect(avatarInitials('   ')).toBe('?');
    expect(avatarInitials(null)).toBe('?');
    expect(avatarInitials(undefined)).toBe('?');
  });
});

describe('avatarColorIndex / avatarColor', () => {
  it('같은 이름은 항상 같은 인덱스(결정적)', () => {
    expect(avatarColorIndex('admin')).toBe(avatarColorIndex('admin'));
    expect(avatarColor('user-1')).toEqual(avatarColor('user-1'));
  });

  it('인덱스는 팔레트 범위 안', () => {
    ['admin', 'it-support', 'team-lead', '홍길동', 'x'].forEach((n) => {
      const idx = avatarColorIndex(n);
      expect(idx).toBeGreaterThanOrEqual(0);
      expect(idx).toBeLessThan(AVATAR_PALETTE.length);
    });
  });

  it('빈 값은 인덱스 0', () => {
    expect(avatarColorIndex('')).toBe(0);
    expect(avatarColorIndex(null)).toBe(0);
  });

  it('팔레트는 토큰 기반 Tailwind 유틸만(하드코딩 색 없음)', () => {
    AVATAR_PALETTE.forEach((c) => {
      expect(c.bg).toMatch(/^bg-.+-soft$/);
      expect(c.text).toMatch(/^text-/);
    });
  });
});
