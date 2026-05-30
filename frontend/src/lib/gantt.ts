/**
 * 간트(Gantt) 차트의 순수 로직 (phase 16 step 5).
 * 컴포넌트(GanttChart.vue)와 단위 테스트가 공유한다 — DOM/ECharts 인스턴스 없이 검증 가능.
 *
 * 무거운 간트 전용 라이브러리(frappe-gantt 등) 대신 ECharts custom series 로 그린다(금지사항).
 * 본 모듈은 시간 도메인 산정·진행률 클램프·데이터 변환·옵션 조립만 담당하며,
 * 색은 chart-theme 의 토큰 팔레트(ChartColors)에서만 가져온다(UI_GUIDE §2 — 하드코딩 금지).
 */
import type { EChartsOption } from 'echarts';
import type { ChartColors } from '@/lib/chart-theme';

/** 간트 막대 한 개. PMS 프로젝트/태스크·SLA 등 시간 범위가 있는 무엇이든 표현한다. */
export interface GanttBar {
  id: string;
  label: string;
  /** ISO 날짜(YYYY-MM-DD) 또는 일시. */
  start: string;
  end: string;
  /** 0~100. 채움 비율. */
  progress: number;
  /** 미지정 시 colors.series 팔레트에서 인덱스로 자동 배정. */
  color?: string;
  /** 분류 라벨(프로젝트명 등). 툴팁 보조 표시용. */
  category?: string;
}

const DAY_MS = 86_400_000;

/** 진행률을 0~100 으로 강제. NaN 은 0, ±Infinity 는 경계로 클램프. */
export function clampProgress(p: number): number {
  if (Number.isNaN(p)) return 0;
  return Math.max(0, Math.min(100, Math.round(p)));
}

export interface GanttDomain {
  min: number;
  max: number;
}

/**
 * 모든 막대의 start/end 에서 시간 도메인(epoch ms)을 산정한다. 양끝에 1일 패딩.
 * 파싱 불가한 값은 무시하며, 유효 시각이 하나도 없으면 {0,0}.
 */
export function ganttTimeDomain(bars: GanttBar[]): GanttDomain {
  const times: number[] = [];
  for (const b of bars) {
    const s = Date.parse(b.start);
    const e = Date.parse(b.end);
    if (!Number.isNaN(s)) times.push(s);
    if (!Number.isNaN(e)) times.push(e);
  }
  if (times.length === 0) return { min: 0, max: 0 };
  return { min: Math.min(...times) - DAY_MS, max: Math.max(...times) + DAY_MS };
}

/** YYYY-MM-DD → 'M/D' (축·툴팁 라벨용). 파싱 불가 시 원문 반환. */
export function formatGanttDate(iso: string): string {
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return iso;
  const d = new Date(t);
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

/** custom series 한 항목 — value=[rowIndex, startMs, endMs, progress]. */
export interface GanttDatum {
  value: [number, number, number, number];
  bar: GanttBar;
}

/** 막대 배열 → (yAxis 카테고리 라벨 목록, custom series 데이터). 입력 순서를 행 순서로 보존. */
export function toGanttData(bars: GanttBar[]): {
  categories: string[];
  data: GanttDatum[];
} {
  const categories = bars.map((b) => b.label);
  const data: GanttDatum[] = bars.map((b, i) => {
    const s = Date.parse(b.start);
    const e = Date.parse(b.end);
    return {
      value: [
        i,
        Number.isNaN(s) ? 0 : s,
        Number.isNaN(e) ? 0 : e,
        clampProgress(b.progress),
      ],
      bar: b,
    };
  });
  return { categories, data };
}

export interface GanttOptions {
  /** 행 높이(px) — 막대 두께 계산용. 기본 36. */
  rowHeight?: number;
  /** 좌측 라벨 컬럼 폭(px). 기본 180. */
  labelWidth?: number;
}

function pickBarColor(bar: GanttBar, index: number, colors: ChartColors): string {
  if (bar.color) return bar.color;
  return colors.series[index % colors.series.length] ?? colors.primary;
}

/**
 * 간트 옵션 조립 — ECharts custom series. renderItem 은 막대(트랙) + 진행률 채움 두 사각형을 그린다.
 * 색은 ChartColors 팔레트에서만, 축·격자선은 토큰 색으로. renderItem/포매터는 bars·colors 를 클로저로 참조한다.
 */
export function buildGanttOption(
  bars: GanttBar[],
  colors: ChartColors,
  opts: GanttOptions = {},
): EChartsOption {
  const { categories, data } = toGanttData(bars);
  const labelWidth = opts.labelWidth ?? 180;
  const rowHeightRatio = 0.56;

  /* eslint-disable @typescript-eslint/no-explicit-any */
  // ECharts custom renderItem 의 api 는 라이브러리 콜백 계약상 정적 타입이 없다 — 국소적으로만 any 사용.
  const renderItem = (_params: unknown, api: any) => {
    const rowIndex = api.value(0) as number;
    const startMs = api.value(1) as number;
    const endMs = api.value(2) as number;
    const progress = api.value(3) as number;

    const startPoint = api.coord([startMs, rowIndex]) as [number, number];
    const endPoint = api.coord([endMs, rowIndex]) as [number, number];
    const bandHeight = (api.size([0, 1]) as [number, number])[1];
    const barHeight = bandHeight * rowHeightRatio;

    const x = startPoint[0];
    const y = startPoint[1] - barHeight / 2;
    const fullWidth = Math.max(endPoint[0] - startPoint[0], 2);
    const fillWidth = (fullWidth * progress) / 100;
    const fill = pickBarColor(bars[rowIndex] ?? ({} as GanttBar), rowIndex, colors);

    return {
      type: 'group',
      children: [
        {
          type: 'rect',
          shape: { x, y, width: fullWidth, height: barHeight, r: 4 },
          style: { fill, opacity: 0.22 },
        },
        {
          type: 'rect',
          shape: { x, y, width: fillWidth, height: barHeight, r: 4 },
          style: { fill },
        },
      ],
    };
  };
  /* eslint-enable @typescript-eslint/no-explicit-any */

  return {
    grid: { left: labelWidth, right: 24, top: 28, bottom: 24, containLabel: false },
    tooltip: {
      trigger: 'item',
      backgroundColor: colors.surface,
      borderColor: colors.border,
      borderWidth: 1,
      textStyle: { color: colors.text, fontSize: 12 },
      extraCssText: 'box-shadow:0 8px 24px rgba(0,0,0,0.12);border-radius:8px;',
      formatter: (p: unknown) => {
        const dt = (p as { data?: GanttDatum }).data;
        if (!dt) return '';
        const b = dt.bar;
        const range = `${formatGanttDate(b.start)} ~ ${formatGanttDate(b.end)}`;
        const cat = b.category ? `<div style="opacity:.7">${b.category}</div>` : '';
        return `<strong>${b.label}</strong>${cat}<div>${range}</div><div>진행률 ${clampProgress(
          b.progress,
        )}%</div>`;
      },
    },
    xAxis: {
      type: 'time',
      position: 'top',
      axisLine: { lineStyle: { color: colors.border } },
      axisTick: { show: false },
      axisLabel: { color: colors.textMuted, fontSize: 12 },
      splitLine: { show: true, lineStyle: { color: colors.borderSubtle } },
    },
    yAxis: {
      type: 'category',
      data: categories,
      inverse: true,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: colors.text, fontSize: 13, width: labelWidth - 12, overflow: 'truncate' },
      splitLine: { show: false },
    },
    series: [
      {
        type: 'custom',
        renderItem: renderItem as never,
        encode: { x: [1, 2], y: 0 },
        data,
      },
    ],
  };
}
