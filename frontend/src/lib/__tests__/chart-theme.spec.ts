import { describe, it, expect } from 'vitest';
import {
  FALLBACK_CHART_COLORS,
  buildLineOption,
  buildBarOption,
  buildDonutOption,
  buildSparklineOption,
  moduleChartColor,
  trendInfo,
  type ChartColors,
  type SeriesInput,
  type DonutItem,
} from '@/lib/chart-theme';

const colors: ChartColors = FALLBACK_CHART_COLORS;

// ECharts series 배열은 타입상 단일/배열 union 이라 테스트에서 배열로 좁힌다.
function seriesOf(option: { series?: unknown }): Record<string, unknown>[] {
  return option.series as Record<string, unknown>[];
}

// noUncheckedIndexedAccess 대응 — 인덱스 접근이 정의됐음을 단언한다.
function at<T>(arr: readonly T[], i: number): T {
  const v = arr[i];
  if (v === undefined) throw new Error(`index ${i} is undefined`);
  return v;
}

describe('trendInfo', () => {
  it('양수는 상승(up)·success', () => {
    const t = trendInfo(12.3);
    expect(t.direction).toBe('up');
    expect(t.arrow).toBe('↑');
    expect(t.tone).toBe('success');
    expect(t.magnitude).toBeCloseTo(12.3);
  });

  it('음수는 하락(down)·danger', () => {
    const t = trendInfo(-8.1);
    expect(t.direction).toBe('down');
    expect(t.arrow).toBe('↓');
    expect(t.tone).toBe('danger');
    expect(t.magnitude).toBeCloseTo(8.1);
  });

  it('invert 면 상승을 위험(danger)으로 본다', () => {
    expect(trendInfo(5, true).tone).toBe('danger');
    expect(trendInfo(-5, true).tone).toBe('success');
  });

  it('0 또는 비유한값은 flat·neutral', () => {
    expect(trendInfo(0).direction).toBe('flat');
    expect(trendInfo(0).tone).toBe('neutral');
    expect(trendInfo(Number.NaN).direction).toBe('flat');
  });
});

describe('moduleChartColor', () => {
  it('모듈별로 series 팔레트의 고정 인덱스를 고른다', () => {
    expect(moduleChartColor('ITSM', colors)).toBe(at(colors.series, 0));
    expect(moduleChartColor('ITAM', colors)).toBe(at(colors.series, 1));
    expect(moduleChartColor('PMS', colors)).toBe(at(colors.series, 2));
    expect(moduleChartColor('COMMON', colors)).toBe(at(colors.series, 3));
    expect(moduleChartColor('SYSTEM', colors)).toBe(at(colors.series, 4));
  });
});

describe('buildLineOption', () => {
  const categories = ['1월', '2월', '3월'];
  const series: SeriesInput[] = [{ name: 'CPU', data: [1, 2, 3] }];

  it('카테고리·데이터를 옵션에 매핑한다', () => {
    const option = buildLineOption(categories, series, colors);
    expect((option.xAxis as { data: string[] }).data).toEqual(categories);
    const s0 = at(seriesOf(option), 0);
    expect(s0.type).toBe('line');
    expect(s0.data).toEqual([1, 2, 3]);
  });

  it('미지정 시리즈 색은 팔레트 인덱스로 자동 배정', () => {
    const option = buildLineOption(categories, [at(series, 0), { name: 'MEM', data: [4, 5, 6] }], colors);
    const s = seriesOf(option);
    expect((at(s, 0).lineStyle as { color: string }).color).toBe(at(colors.series, 0));
    expect((at(s, 1).lineStyle as { color: string }).color).toBe(at(colors.series, 1));
  });

  it('area 옵션이면 areaStyle 추가, 아니면 없음', () => {
    expect(at(seriesOf(buildLineOption(categories, series, colors, { area: true })), 0).areaStyle).toBeDefined();
    expect(at(seriesOf(buildLineOption(categories, series, colors, { area: false })), 0).areaStyle).toBeUndefined();
  });

  it('툴팁/축 색은 토큰 색에서 가져온다(하드코딩 아님)', () => {
    const option = buildLineOption(categories, series, colors);
    expect((option.tooltip as { backgroundColor: string }).backgroundColor).toBe(colors.surface);
    expect((option.xAxis as { axisLabel: { color: string } }).axisLabel.color).toBe(colors.textMuted);
  });
});

describe('buildBarOption', () => {
  it('stack 옵션이면 series.stack 부여', () => {
    const s0 = at(seriesOf(buildBarOption(['a'], [{ name: 'x', data: [1] }], colors, { stack: true })), 0);
    expect(s0.stack).toBe('total');
  });

  it('stack 미지정이면 stack 없음', () => {
    const s0 = at(seriesOf(buildBarOption(['a'], [{ name: 'x', data: [1] }], colors)), 0);
    expect(s0.stack).toBeUndefined();
  });
});

describe('buildDonutOption', () => {
  const items: DonutItem[] = [
    { name: 'A', value: 10 },
    { name: 'B', value: 20, color: '#abcdef' },
  ];

  it('항목을 pie data 로 매핑하고 색을 배정한다', () => {
    const data = at(seriesOf(buildDonutOption(items, colors)), 0).data as Array<{
      name: string;
      value: number;
      itemStyle: { color: string };
    }>;
    expect(data).toHaveLength(2);
    expect(at(data, 0).value).toBe(10);
    expect(at(data, 0).itemStyle.color).toBe(at(colors.series, 0));
    expect(at(data, 1).itemStyle.color).toBe('#abcdef');
  });

  it('centerLabel 이면 가운데 라벨 표시', () => {
    const s0 = at(seriesOf(buildDonutOption(items, colors, { centerLabel: '30' })), 0);
    expect((s0.label as { show: boolean; formatter: string }).formatter).toBe('30');
  });
});

describe('buildSparklineOption', () => {
  it('축·툴팁 없는 단일 라인을 만든다', () => {
    const option = buildSparklineOption([1, 2, 3], at(colors.series, 0), colors);
    expect(option.tooltip).toBeUndefined();
    expect((option.xAxis as { show: boolean }).show).toBe(false);
    const s0 = at(seriesOf(option), 0);
    expect(s0.data).toEqual([1, 2, 3]);
    expect((s0.lineStyle as { color: string }).color).toBe(at(colors.series, 0));
  });

  it('색 미지정 시 primary 로 폴백', () => {
    const s0 = at(seriesOf(buildSparklineOption([1, 2], '', colors)), 0);
    expect((s0.lineStyle as { color: string }).color).toBe(colors.primary);
  });
});
