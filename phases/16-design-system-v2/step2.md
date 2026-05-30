# Step 2: charts-and-kpi-components

## 읽어야 할 파일
- `/phases/16-design-system-v2/step0~1.md` — 토큰·다크 모드
- `/docs/UI_GUIDE.md` §5 컴포넌트 — KpiCard·차트 추가

## 작업
**ECharts 도입 + Vue 어댑터 + KpiCard·Sparkline·TrendIndicator·DonutChart·BarChart·LineChart 등 데이터 시각화 컴포넌트 풀세트**.

### 1. 의존성
```bash
cd frontend
pnpm add echarts vue-echarts
```

### 2. ECharts 글로벌 셋업 — `frontend/src/lib/echarts.ts`
```ts
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import {
  BarChart, LineChart, PieChart,
} from 'echarts/charts';
import {
  GridComponent, TooltipComponent, LegendComponent,
  TitleComponent, DatasetComponent, MarkLineComponent,
} from 'echarts/components';

use([
  CanvasRenderer,
  BarChart, LineChart, PieChart,
  GridComponent, TooltipComponent, LegendComponent,
  TitleComponent, DatasetComponent, MarkLineComponent,
]);
```

`main.ts` 에서 부팅 시 import.

### 3. 차트 공통 테마 — `frontend/src/lib/chart-theme.ts`
다크 모드 자동 반응: useThemeStore 의 effective 값을 watch 해서 ECharts option 의 textStyle·tooltip·axisLine 색을 토큰 변수에서 가져와 전환.

### 4. 컴포넌트
- `KpiCard.vue` — 큰 숫자(36px/700 tabular-nums) + label + sparkline(우측) + TrendIndicator(아래)
- `Sparkline.vue` — `<v-chart>` 미니 line, 60×24px
- `TrendIndicator.vue` — `↑ 12.3%` 화살표·색 (success/danger), tabular-nums
- `DonutChart.vue`·`BarChart.vue`·`LineChart.vue` — vue-echarts wrapper, props (data·colors·height)
- `MetricRow.vue` — 라벨 + 큰 숫자 + 변화량 (KpiCard 의 인라인 변형)

각 컴포넌트:
- Tailwind v4 토큰 사용 (직접 hex 박지 않음)
- 모듈 컬러 prop 지원 (`module="ITSM"` → 색 자동)
- 다크 모드 자동
- TypeScript generic·strict

### 5. 검증용 디자인 갤러리 — `/_dev/charts`
KpiCard 8개 + Sparkline + TrendIndicator + DonutChart + BarChart + LineChart 한 화면.

### 6. 단위 테스트
- 컴포넌트 props 매핑·옵션 변환 함수 분리해서 spec 작성.

## Acceptance Criteria
```bash
cd frontend
grep -qE '"echarts"|"vue-echarts"' package.json
test -f src/lib/echarts.ts
test -f src/lib/chart-theme.ts
test -f src/components/dataviz/KpiCard.vue
test -f src/components/dataviz/Sparkline.vue
test -f src/components/dataviz/TrendIndicator.vue
test -f src/components/dataviz/DonutChart.vue
test -f src/components/dataviz/BarChart.vue
test -f src/components/dataviz/LineChart.vue
test -f src/views/_dev/ChartsGallery.vue
grep -q "/_dev/charts" src/router/index.ts

pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- ECharts 옵션을 컴포넌트 내부에 hard-code 하지 마라 — chart-theme.ts 로 분리.
- 임의 색 hex 직접 사용 금지 — `getComputedStyle(document.documentElement).getPropertyValue('--color-itsm')` 같은 패턴 또는 chart-theme 헬퍼.
- chart 라이브러리 두 개 동시 도입 금지.
- 모든 차트에 다크 모드 watch 누락 시 다크 토글 시 색 박살.
- `console.log` 금지.
