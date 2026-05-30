/**
 * ECharts 글로벌 셋업 — tree-shaking 을 위해 사용하는 차트·컴포넌트·렌더러만 등록한다.
 *
 * vue-echarts 의 `<v-chart>` 는 여기서 `use()` 로 등록된 모듈만 렌더링할 수 있다.
 * `main.ts` 부팅 시 1회 import 한다. dataviz 컴포넌트는 이 파일에 등록된 차트만 사용한다.
 */
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DatasetComponent,
  MarkLineComponent,
} from 'echarts/components';

use([
  CanvasRenderer,
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DatasetComponent,
  MarkLineComponent,
]);
