import { describe, it, expect } from 'vitest';
import { moduleVisual } from '@/lib/module-color';
import type { SystemType } from '@/types/meta';

/**
 * 모듈(systemType)별 시각 매핑(UI_GUIDE §3 v2) 검증.
 * 색 값 하드코딩 없이 토큰 CSS var·Tailwind 유틸만 노출하는지 확인한다.
 */
describe('moduleVisual', () => {
  const cases: Array<[SystemType, string, string]> = [
    ['ITSM', 'var(--color-itsm)', 'text-itsm'],
    ['ITAM', 'var(--color-itam)', 'text-itam'],
    ['PMS', 'var(--color-pms)', 'text-pms'],
    ['COMMON', 'var(--color-common)', 'text-common'],
    ['SYSTEM', 'var(--color-system)', 'text-system'],
  ];

  it.each(cases)('%s 매핑 — primaryVar·textClass', (sys, primaryVar, textClass) => {
    const v = moduleVisual(sys);
    expect(v.primaryVar).toBe(primaryVar);
    expect(v.textClass).toBe(textClass);
  });

  it('5종 모두 soft/border/icon 필드 채움', () => {
    (['ITSM', 'ITAM', 'PMS', 'COMMON', 'SYSTEM'] as SystemType[]).forEach((sys) => {
      const v = moduleVisual(sys);
      expect(v.softVar).toMatch(/^var\(--color-.+-soft\)$/);
      expect(v.bgSoftClass).toMatch(/^bg-.+-soft$/);
      expect(v.borderClass).toMatch(/^border-/);
      expect(v.icon.length).toBeGreaterThan(0);
    });
  });

  it('색 hex 값을 직접 노출하지 않는다 (토큰만)', () => {
    const v = moduleVisual('ITSM');
    const joined = Object.values(v).join(' ');
    expect(joined).not.toMatch(/#[0-9a-fA-F]{3,6}/);
  });

  it('비정상 입력은 SYSTEM 으로 방어', () => {
    const v = moduleVisual('UNKNOWN' as SystemType);
    expect(v).toEqual(moduleVisual('SYSTEM'));
  });
});
