<script setup lang="ts">
/**
 * 데이터 시각화 컴포넌트 검증 갤러리 (dev only).
 * KpiCard 8 + Sparkline + TrendIndicator + DonutChart + BarChart + LineChart 를 한 화면에 모은다.
 * 다크 토글(TopBar)에서 색 전환을 눈으로 확인하는 용도.
 */
import KpiCard from '@/components/dataviz/KpiCard.vue';
import Sparkline from '@/components/dataviz/Sparkline.vue';
import TrendIndicator from '@/components/dataviz/TrendIndicator.vue';
import DonutChart from '@/components/dataviz/DonutChart.vue';
import BarChart from '@/components/dataviz/BarChart.vue';
import LineChart from '@/components/dataviz/LineChart.vue';
import MetricRow from '@/components/dataviz/MetricRow.vue';
import type { SystemType } from '@/types/meta';
import type { DonutItem, SeriesInput } from '@/lib/chart-theme';

const spark = [4, 7, 5, 9, 6, 11, 8, 13, 10, 15];
const sparkDown = [15, 12, 13, 9, 10, 7, 8, 5, 6, 4];

const kpis: Array<{
  label: string;
  value: string | number;
  unit?: string;
  spark: number[];
  trend: number;
  trendInvert?: boolean;
  module?: SystemType;
}> = [
  { label: '미처리 티켓', value: 42, unit: '건', spark, trend: 12.3, trendInvert: true, module: 'ITSM' },
  { label: '평균 응답 시간', value: '3.2', unit: 'h', spark: sparkDown, trend: -8.1, trendInvert: true, module: 'ITSM' },
  { label: '등록 자산', value: '1,284', unit: '대', spark, trend: 4.7, module: 'ITAM' },
  { label: '폐기 예정', value: 18, unit: '대', spark: sparkDown, trend: -2.0, module: 'ITAM' },
  { label: '진행 프로젝트', value: 7, spark, trend: 0, module: 'PMS' },
  { label: '지연 태스크', value: 5, unit: '건', spark, trend: 25.0, trendInvert: true, module: 'PMS' },
  { label: '공통 코드', value: 312, spark, trend: 1.2, module: 'COMMON' },
  { label: '활성 사용자', value: 96, unit: '명', spark, trend: 3.4, module: 'SYSTEM' },
];

const donut: DonutItem[] = [
  { name: '장애', value: 38 },
  { name: '서비스요청', value: 52 },
  { name: '변경', value: 21 },
  { name: '문제', value: 12 },
  { name: '문의', value: 30 },
];

const months = ['1월', '2월', '3월', '4월', '5월', '6월'];
const barSeries: SeriesInput[] = [
  { name: '접수', data: [120, 132, 101, 134, 90, 110] },
  { name: '처리', data: [110, 120, 95, 130, 88, 105] },
];
const lineSeries: SeriesInput[] = [
  { name: 'CPU', data: [42, 55, 48, 63, 58, 72] },
  { name: 'MEM', data: [60, 58, 65, 70, 68, 75] },
];
</script>

<template>
  <main class="mx-auto max-w-6xl space-y-10 p-6">
    <section>
      <h1>Charts &amp; KPI Gallery</h1>
      <p class="text-foreground-muted">
        데이터 시각화 컴포넌트 검증용 (dev only). TopBar 다크 토글로 색 전환을 확인하세요.
      </p>
    </section>

    <section>
      <h2>KpiCard ×8</h2>
      <div class="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard
          v-for="k in kpis"
          :key="k.label"
          :label="k.label"
          :value="k.value"
          :unit="k.unit"
          :spark="k.spark"
          :trend="k.trend"
          :trend-invert="k.trendInvert"
          :module="k.module"
        />
      </div>
    </section>

    <section>
      <h2>Sparkline &amp; TrendIndicator</h2>
      <div class="mt-3 flex flex-wrap items-center gap-6 rounded-lg border border-border bg-surface p-5">
        <Sparkline
          :data="spark"
          module="ITSM"
        />
        <Sparkline
          :data="sparkDown"
          module="ITAM"
        />
        <Sparkline
          :data="spark"
          module="PMS"
          :width="120"
          :height="32"
        />
        <TrendIndicator :value="12.3" />
        <TrendIndicator :value="-8.1" />
        <TrendIndicator
          :value="5.0"
          :invert="true"
        />
        <TrendIndicator :value="0" />
      </div>
    </section>

    <section>
      <h2>MetricRow</h2>
      <div class="mt-3 max-w-md divide-y divide-border-subtle rounded-lg border border-border bg-surface px-5">
        <MetricRow
          label="미처리 티켓"
          :value="42"
          unit="건"
          :trend="12.3"
          :trend-invert="true"
          module="ITSM"
        />
        <MetricRow
          label="등록 자산"
          value="1,284"
          unit="대"
          :trend="4.7"
          module="ITAM"
        />
        <MetricRow
          label="진행 프로젝트"
          :value="7"
          :trend="0"
          module="PMS"
        />
      </div>
    </section>

    <section class="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <div class="rounded-lg border border-border bg-surface p-5">
        <h3>DonutChart — 요청 유형</h3>
        <DonutChart
          :items="donut"
          :height="260"
          show-legend
          center-label="153"
        />
      </div>
      <div class="rounded-lg border border-border bg-surface p-5">
        <h3>BarChart — 월별 접수/처리</h3>
        <BarChart
          :categories="months"
          :series="barSeries"
          :height="260"
          show-legend
        />
      </div>
      <div class="rounded-lg border border-border bg-surface p-5">
        <h3>LineChart — 리소스 추세</h3>
        <LineChart
          :categories="months"
          :series="lineSeries"
          :height="260"
          area
          show-legend
        />
      </div>
    </section>
  </main>
</template>
