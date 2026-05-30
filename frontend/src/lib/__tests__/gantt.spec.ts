import { describe, it, expect } from 'vitest';
import {
  clampProgress,
  ganttTimeDomain,
  formatGanttDate,
  toGanttData,
  buildGanttOption,
  type GanttBar,
} from '@/lib/gantt';
import { FALLBACK_CHART_COLORS } from '@/lib/chart-theme';

function bar(id: string, start: string, end: string, progress = 50): GanttBar {
  return { id, label: `작업 ${id}`, start, end, progress };
}

describe('clampProgress', () => {
  it('0~100 범위로 강제하고 반올림한다', () => {
    expect(clampProgress(-5)).toBe(0);
    expect(clampProgress(150)).toBe(100);
    expect(clampProgress(33.6)).toBe(34);
  });
  it('비유한 값은 0', () => {
    expect(clampProgress(NaN)).toBe(0);
    expect(clampProgress(Infinity)).toBe(100);
  });
});

describe('ganttTimeDomain', () => {
  it('최소 시작·최대 종료에 1일 패딩을 더한 도메인을 만든다', () => {
    const d = ganttTimeDomain([bar('a', '2026-05-01', '2026-05-10'), bar('b', '2026-05-05', '2026-05-20')]);
    expect(d.min).toBe(Date.parse('2026-05-01') - 86_400_000);
    expect(d.max).toBe(Date.parse('2026-05-20') + 86_400_000);
  });
  it('유효 시각이 없으면 0,0', () => {
    expect(ganttTimeDomain([])).toEqual({ min: 0, max: 0 });
    expect(ganttTimeDomain([bar('x', 'bad', 'worse')])).toEqual({ min: 0, max: 0 });
  });
});

describe('formatGanttDate', () => {
  it('M/D 로 포맷한다', () => {
    expect(formatGanttDate('2026-05-09')).toBe('5/9');
  });
  it('파싱 불가 시 원문', () => {
    expect(formatGanttDate('n/a')).toBe('n/a');
  });
});

describe('toGanttData', () => {
  it('입력 순서를 행 인덱스로 보존하며 value=[row,start,end,progress] 를 만든다', () => {
    const { categories, data } = toGanttData([bar('a', '2026-05-01', '2026-05-10', 40)]);
    expect(categories).toEqual(['작업 a']);
    const d0 = data[0];
    expect(d0).toBeDefined();
    expect(d0?.value).toEqual([0, Date.parse('2026-05-01'), Date.parse('2026-05-10'), 40]);
    expect(d0?.bar.id).toBe('a');
  });
});

describe('buildGanttOption', () => {
  it('time x축 + category y축 + custom series 를 만든다', () => {
    const opt = buildGanttOption([bar('a', '2026-05-01', '2026-05-10')], FALLBACK_CHART_COLORS);
    const xAxis = opt.xAxis as { type: string };
    const yAxis = opt.yAxis as { type: string; data: string[] };
    const series = opt.series as { type: string }[];
    expect(xAxis.type).toBe('time');
    expect(yAxis.type).toBe('category');
    expect(yAxis.data).toEqual(['작업 a']);
    expect(series[0]?.type).toBe('custom');
  });
});
