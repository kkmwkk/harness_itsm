import { describe, it, expect } from 'vitest';
import {
  isIsoDate,
  toCalendarDate,
  fromCalendarDate,
  formatKo,
  formatRangeKo,
} from '@/lib/date-field';
import { CalendarDate } from '@internationalized/date';

/**
 * DatePicker·DateRangePicker 가 의존하는 날짜 변환 순수 로직 검증.
 * 폼 모델(ISO 'yyyy-MM-dd') ↔ CalendarDate ↔ ko-KR 표시.
 */
describe('isIsoDate', () => {
  it.each([
    ['2026-05-30', true],
    ['2026-5-30', false],
    ['2026/05/30', false],
    ['', false],
    [null, false],
    [undefined, false],
  ])('%s → %s', (input, expected) => {
    expect(isIsoDate(input)).toBe(expected);
  });
});

describe('toCalendarDate / fromCalendarDate 왕복', () => {
  it('ISO → CalendarDate → ISO 동일', () => {
    const d = toCalendarDate('2026-05-30');
    expect(d).toBeInstanceOf(CalendarDate);
    expect(fromCalendarDate(d)).toBe('2026-05-30');
  });
  it('한 자리 월/일도 0 패딩', () => {
    expect(fromCalendarDate(new CalendarDate(2026, 1, 9))).toBe('2026-01-09');
  });
  it('비정상 입력은 undefined / 빈 문자열', () => {
    expect(toCalendarDate('nope')).toBeUndefined();
    expect(toCalendarDate(null)).toBeUndefined();
    expect(fromCalendarDate(undefined)).toBe('');
    expect(fromCalendarDate(null)).toBe('');
  });
});

describe('formatKo', () => {
  it('ko-KR 연·월·일 포함', () => {
    const out = formatKo('2026-05-30');
    expect(out).toContain('2026');
    expect(out).toContain('5');
    expect(out).toContain('30');
  });
  it('비정상은 빈 문자열', () => {
    expect(formatKo('')).toBe('');
    expect(formatKo('bad')).toBe('');
  });
});

describe('formatRangeKo', () => {
  it('양쪽 모두 있으면 ~ 로 연결', () => {
    const out = formatRangeKo('2026-05-01', '2026-05-30');
    expect(out).toContain('~');
    expect(out).toContain('2026');
  });
  it('한쪽만 있으면 … 플레이스홀더', () => {
    expect(formatRangeKo('2026-05-01', '')).toContain('…');
  });
  it('둘 다 비면 빈 문자열', () => {
    expect(formatRangeKo('', '')).toBe('');
    expect(formatRangeKo(null, null)).toBe('');
  });
});
