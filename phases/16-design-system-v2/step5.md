# Step 5: gantt-timeline

## 읽어야 할 파일
- `/phases/16-design-system-v2/step0~4.md`
- `/phases/12-domain-depth-workflow-category/index.json` (PMS·자산 이력)

## 작업
**Gantt 차트 + Timeline 컴포넌트** — PMS 프로젝트/태스크 간트 + 자산 라이프사이클 타임라인 + 티켓 워크플로우 단계 타임라인.

### 1. Gantt 컴포넌트 — `frontend/src/components/dataviz/GanttChart.vue`
ECharts 의 custom series 활용 (또는 frappe-gantt 도입은 의존성 큼 — ECharts custom 권장).

```ts
interface GanttBar {
  id: string;
  label: string;
  start: string;     // ISO date
  end: string;
  progress: number;  // 0~100
  color?: string;
  category?: string;
}
```

- 좌측: label 컬럼 (200px 고정).
- 우측: 시간축 (일 단위 또는 주 단위 — props).
- 각 bar: progress 비율로 채움 + tooltip (start·end·progress).
- 다크 모드 색 자동.

PMS `/pms` 페이지에서 사용 (프로젝트 1 개당 1 row + 태스크 row).

### 2. Timeline 컴포넌트 — `frontend/src/components/dataviz/EventTimeline.vue`
세로축 타임라인:
- 각 이벤트: 아이콘 + 시각 + 라벨 + 설명 + 사용자
- 연결선 (보더 1px)
- 이벤트 타입별 색 (acquired=success, disposed=neutral 등)

사용:
- 자산 상세의 라이프사이클 이벤트 (`/itam/:id` 의 기존 dl 형태를 EventTimeline 로 교체)
- 워크플로우 단계 이력 (WorkflowPanel 의 이력 테이블 옆 시각화 옵션)

### 3. PMS 페이지 — `frontend/src/pages/pms/IndexPage.vue`
현재 메타 미존재로 notPublished 카드. 본 step 에서:
- `sql/init/19_pms_seed.sql` 추가 — itg-project 메타 + 샘플 프로젝트 3개
- IndexPage 에서 프로젝트 그리드 + 선택 시 GanttChart 사용
- 백엔드의 Project/Task entity 는 phase 12 의 PMS 가 단순 placeholder 였으므로, 본 step 에서 최소 백엔드 (Project·Task entity·service·controller) 정착도 같이.

> 백엔드 분량이 작아지지 않으면 별도 step 으로 분리. 본 step 에서는 최소한 frontend Gantt + 자산 EventTimeline 까지.

### 4. 라우트
- `/pms` (기존)
- `/itam/:id` 의 라이프사이클 카드 → EventTimeline 교체.

## Acceptance Criteria
```bash
cd frontend
test -f src/components/dataviz/GanttChart.vue
test -f src/components/dataviz/EventTimeline.vue
pnpm type-check
pnpm lint
pnpm build
pnpm test
pnpm dev &
sleep 5
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/pms)" = "200"
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/itam/1)" = "200"
kill %1
```

## 금지사항
- frappe-gantt·gantt-schedule 같은 무거운 라이브러리 도입 금지 — ECharts custom 또는 자체.
- 자산 이력 타임라인의 dl 형태 그대로 두지 마라 — EventTimeline 교체.
- PMS 백엔드 기존 placeholder 가 깨지지 않게.
- 운영 코드 console.log 금지.
