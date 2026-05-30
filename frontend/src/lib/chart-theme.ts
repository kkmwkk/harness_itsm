/**
 * 차트 공통 테마 — ECharts option 의 색·축·툴팁을 디자인 토큰에서 가져와 조립한다.
 *
 * 설계 원칙 (UI_GUIDE §5 v2 / step 2 금지사항):
 * - 컴포넌트는 ECharts option 을 직접 hard-code 하지 않는다 — 본 파일의 빌더(`build*Option`)만 쓴다.
 * - 임의 색 hex 를 컴포넌트에 박지 않는다 — 색은 `readChartColors()` 가 CSS 변수(`--color-*`)에서
 *   `getComputedStyle` 로 읽어온다. 다크/라이트 전환은 토큰이 자동 처리한다.
 * - 옵션 빌더는 순수 함수다(색 팔레트를 인자로 받음) — node 환경에서 단위 테스트 가능.
 *
 * 다크 모드 반응은 `useChartColors()` 컴포저블이 `useThemeStore.effective()` 변화를 watch 해
 * `colors` 를 다시 읽는 방식으로 처리한다(컴포넌트가 그 colors 로 옵션을 재계산).
 */
import { ref, watch, type Ref } from 'vue';
import type { EChartsOption } from 'echarts';
import type { SystemType } from '@/types/meta';
import { useThemeStore } from '@/stores/useThemeStore';

/** 차트가 사용하는 토큰 색 묶음 (모두 resolved 색 문자열). */
export interface ChartColors {
  text: string;
  textMuted: string;
  border: string;
  borderSubtle: string;
  surface: string;
  /** 다중 시리즈 기본 팔레트 (모듈 컬러 + 시맨틱 보조). */
  series: string[];
  success: string;
  danger: string;
  warning: string;
  info: string;
  neutral: string;
  primary: string;
}

/**
 * SSR/node(테스트) 또는 토큰 미해석 환경용 폴백. tokens.css 라이트 테마 기준값을 미러링한다.
 * 실 브라우저에서는 항상 `readChartColors()` 의 getComputedStyle 결과로 대체된다.
 */
// 시리즈 팔레트 폴백 — tokens.css 라이트 테마 모듈 색 순서(itsm·itam·pms·common·system·primary).
const F_ITSM = '#4f46e5';
const F_ITAM = '#0d9488';
const F_PMS = '#7c3aed';
const F_COMMON = '#d97706';
const F_SYSTEM = '#475569';
const F_PRIMARY = '#0066cc';

export const FALLBACK_CHART_COLORS: ChartColors = {
  text: '#1d1d1f',
  textMuted: '#5a6270',
  border: '#e3e6ea',
  borderSubtle: '#eef0f3',
  surface: '#ffffff',
  series: [F_ITSM, F_ITAM, F_PMS, F_COMMON, F_SYSTEM, F_PRIMARY],
  success: '#16a34a',
  danger: '#dc2626',
  warning: '#d97706',
  info: '#0284c7',
  neutral: '#525252',
  primary: F_PRIMARY,
};

function readCssVar(name: string, fallback: string): string {
  if (typeof document === 'undefined' || typeof getComputedStyle === 'undefined') {
    return fallback;
  }
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

/** 현재 적용된 테마(라이트/다크)의 토큰 색을 CSS 변수에서 읽어온다. */
export function readChartColors(): ChartColors {
  const f = FALLBACK_CHART_COLORS;
  return {
    text: readCssVar('--color-foreground', f.text),
    textMuted: readCssVar('--color-foreground-muted', f.textMuted),
    border: readCssVar('--color-border', f.border),
    borderSubtle: readCssVar('--color-border-subtle', f.borderSubtle),
    surface: readCssVar('--color-surface', f.surface),
    series: [
      readCssVar('--color-itsm', F_ITSM),
      readCssVar('--color-itam', F_ITAM),
      readCssVar('--color-pms', F_PMS),
      readCssVar('--color-common', F_COMMON),
      readCssVar('--color-system', F_SYSTEM),
      readCssVar('--color-primary', F_PRIMARY),
    ],
    success: readCssVar('--color-success', f.success),
    danger: readCssVar('--color-danger', f.danger),
    warning: readCssVar('--color-warning', f.warning),
    info: readCssVar('--color-info', f.info),
    neutral: readCssVar('--color-neutral', f.neutral),
    primary: readCssVar('--color-primary', f.primary),
  };
}

/**
 * 테마 변화에 반응하는 색 묶음. 컴포넌트는 이 ref 로 옵션을 computed 한다.
 * `useThemeStore.effective()` 가 바뀌면(라이트↔다크) colors 를 다시 읽는다.
 */
export function useChartColors(): Ref<ChartColors> {
  const theme = useThemeStore();
  const colors = ref<ChartColors>(readChartColors());
  watch(
    () => theme.effective(),
    () => {
      colors.value = readChartColors();
    },
  );
  return colors;
}

/** 시리즈 입력 — 카테고리형(line/bar) 차트용. */
export interface SeriesInput {
  name: string;
  data: number[];
  /** 미지정 시 colors.series 팔레트에서 인덱스로 자동 배정. */
  color?: string;
}

/** 원형(donut) 차트 항목. */
export interface DonutItem {
  name: string;
  value: number;
  color?: string;
}

function pickColor(colors: ChartColors, index: number, override?: string): string {
  if (override) return override;
  return colors.series[index % colors.series.length] ?? colors.primary;
}

/** colors.series 팔레트에서 모듈별 시리즈 색을 고른다(KpiCard·Sparkline 모듈 prop 용). */
const MODULE_SERIES_INDEX: Record<SystemType, number> = {
  ITSM: 0,
  ITAM: 1,
  PMS: 2,
  COMMON: 3,
  SYSTEM: 4,
};

export function moduleChartColor(module: SystemType, colors: ChartColors): string {
  return colors.series[MODULE_SERIES_INDEX[module] ?? 4] ?? colors.primary;
}

export type TrendDirection = 'up' | 'down' | 'flat';
export type TrendTone = 'success' | 'danger' | 'neutral';

export interface TrendInfo {
  direction: TrendDirection;
  /** 표시용 화살표 글리프. */
  arrow: string;
  /** 시맨틱 톤 — `invert` 면 상승을 위험으로 본다(예: 응답시간 증가는 나쁨). */
  tone: TrendTone;
  /** 부호 없는 절대값(라벨 표시는 화살표가 방향을 전달). */
  magnitude: number;
}

/**
 * 변화량(%) → 방향·화살표·톤. 순수 함수(단위 테스트 대상).
 * 기본은 상승=success, 하락=danger. `invert` 면 반대(낮을수록 좋은 지표).
 */
export function trendInfo(value: number, invert = false): TrendInfo {
  if (!Number.isFinite(value) || value === 0) {
    return { direction: 'flat', arrow: '→', tone: 'neutral', magnitude: 0 };
  }
  const direction: TrendDirection = value > 0 ? 'up' : 'down';
  const good = invert ? value < 0 : value > 0;
  return {
    direction,
    arrow: value > 0 ? '↑' : '↓',
    tone: good ? 'success' : 'danger',
    magnitude: Math.abs(value),
  };
}

function baseGrid() {
  return { left: 8, right: 12, top: 24, bottom: 8, containLabel: true };
}

function baseTooltip(colors: ChartColors) {
  return {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderWidth: 1,
    textStyle: { color: colors.text, fontSize: 12 },
    extraCssText: 'box-shadow:0 8px 24px rgba(0,0,0,0.12);border-radius:8px;',
  };
}

function categoryAxis(categories: string[], colors: ChartColors) {
  return {
    type: 'category' as const,
    data: categories,
    axisLine: { lineStyle: { color: colors.border } },
    axisTick: { show: false },
    axisLabel: { color: colors.textMuted, fontSize: 12 },
  };
}

function valueAxis(colors: ChartColors) {
  return {
    type: 'value' as const,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: colors.textMuted, fontSize: 12 },
    splitLine: { lineStyle: { color: colors.borderSubtle } },
  };
}

export interface LineOptions {
  smooth?: boolean;
  area?: boolean;
  showLegend?: boolean;
}

/** 선형(추세) 차트 옵션. */
export function buildLineOption(
  categories: string[],
  series: SeriesInput[],
  colors: ChartColors,
  opts: LineOptions = {},
): EChartsOption {
  return {
    color: colors.series,
    grid: baseGrid(),
    tooltip: { trigger: 'axis', ...baseTooltip(colors) },
    legend: opts.showLegend
      ? { show: true, textStyle: { color: colors.textMuted }, top: 0 }
      : { show: false },
    xAxis: categoryAxis(categories, colors),
    yAxis: valueAxis(colors),
    series: series.map((s, i) => {
      const color = pickColor(colors, i, s.color);
      return {
        name: s.name,
        type: 'line' as const,
        data: s.data,
        smooth: opts.smooth ?? true,
        showSymbol: false,
        lineStyle: { width: 2, color },
        itemStyle: { color },
        ...(opts.area
          ? { areaStyle: { color, opacity: 0.12 } }
          : {}),
      };
    }),
  };
}

export interface BarOptions {
  stack?: boolean;
  showLegend?: boolean;
}

/** 막대 차트 옵션. */
export function buildBarOption(
  categories: string[],
  series: SeriesInput[],
  colors: ChartColors,
  opts: BarOptions = {},
): EChartsOption {
  return {
    color: colors.series,
    grid: baseGrid(),
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...baseTooltip(colors) },
    legend: opts.showLegend
      ? { show: true, textStyle: { color: colors.textMuted }, top: 0 }
      : { show: false },
    xAxis: categoryAxis(categories, colors),
    yAxis: valueAxis(colors),
    series: series.map((s, i) => {
      const color = pickColor(colors, i, s.color);
      return {
        name: s.name,
        type: 'bar' as const,
        data: s.data,
        ...(opts.stack ? { stack: 'total' } : {}),
        barMaxWidth: 28,
        itemStyle: { color, borderRadius: [4, 4, 0, 0] },
      };
    }),
  };
}

export interface DonutOptions {
  showLegend?: boolean;
  /** 가운데 라벨 텍스트 (예: 합계). */
  centerLabel?: string;
}

/** 도넛(원형) 차트 옵션. */
export function buildDonutOption(
  items: DonutItem[],
  colors: ChartColors,
  opts: DonutOptions = {},
): EChartsOption {
  return {
    tooltip: { trigger: 'item', ...baseTooltip(colors) },
    legend: opts.showLegend
      ? { show: true, bottom: 0, textStyle: { color: colors.textMuted } }
      : { show: false },
    series: [
      {
        type: 'pie' as const,
        radius: ['58%', '80%'],
        avoidLabelOverlap: true,
        padAngle: 2,
        itemStyle: { borderRadius: 4, borderColor: colors.surface, borderWidth: 2 },
        label: opts.centerLabel
          ? {
              show: true,
              position: 'center',
              formatter: opts.centerLabel,
              color: colors.text,
              fontSize: 16,
              fontWeight: 700,
            }
          : { show: false },
        data: items.map((item, i) => ({
          name: item.name,
          value: item.value,
          itemStyle: { color: pickColor(colors, i, item.color) },
        })),
      },
    ],
  };
}

/** 스파크라인(미니 라인) 옵션 — 축·그리드·툴팁 없이 라인만. */
export function buildSparklineOption(
  data: number[],
  color: string,
  colors: ChartColors,
): EChartsOption {
  const line = color || colors.primary;
  return {
    grid: { left: 1, right: 1, top: 2, bottom: 2 },
    xAxis: { type: 'category', show: false, data: data.map((_, i) => i), boundaryGap: false },
    yAxis: { type: 'value', show: false, scale: true },
    series: [
      {
        type: 'line' as const,
        data,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 1.5, color: line },
        areaStyle: { color: line, opacity: 0.14 },
      },
    ],
  };
}
