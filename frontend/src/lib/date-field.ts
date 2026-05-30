/**
 * 날짜 위젯(DatePicker·DateRangePicker)의 순수 변환 헬퍼.
 * - 폼 모델은 ISO 8601 날짜 문자열('yyyy-MM-dd')로 통일한다(useFormSchema 와 동일 계약).
 * - reka-ui Calendar 는 @internationalized/date 의 CalendarDate(DateValue)를 쓴다.
 * - 표시 문자열은 ko-KR 로 포맷한다.
 * 함수는 부수효과 없이 입력→출력만 한다(단위 테스트 대상).
 */
import { parseDate, DateFormatter } from '@internationalized/date';
import type { CalendarDate } from '@internationalized/date';

/** 'yyyy-MM-dd' 형태인지(엄격) 검사. */
export function isIsoDate(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value);
}

/** ISO 날짜 문자열 → CalendarDate. 비정상 입력은 undefined. */
export function toCalendarDate(iso?: string | null): CalendarDate | undefined {
  if (!isIsoDate(iso)) return undefined;
  try {
    return parseDate(iso);
  } catch {
    return undefined;
  }
}

/** CalendarDate → 'yyyy-MM-dd'. null/undefined 면 빈 문자열. */
export function fromCalendarDate(d?: CalendarDate | null): string {
  if (!d) return '';
  const mm = String(d.month).padStart(2, '0');
  const dd = String(d.day).padStart(2, '0');
  return `${d.year}-${mm}-${dd}`;
}

const koFormatter = new DateFormatter('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
});

/** ISO 날짜 문자열 → ko-KR 표시('2026년 5월 30일'). 비정상이면 빈 문자열. */
export function formatKo(iso?: string | null): string {
  const d = toCalendarDate(iso);
  if (!d) return '';
  return koFormatter.format(d.toDate('UTC'));
}

/** from·to ISO 두 칸을 '시작 ~ 끝' 표시 문자열로. 둘 다 비어 있으면 빈 문자열. */
export function formatRangeKo(from?: string | null, to?: string | null): string {
  const f = formatKo(from);
  const t = formatKo(to);
  if (!f && !t) return '';
  return `${f || '…'} ~ ${t || '…'}`;
}
