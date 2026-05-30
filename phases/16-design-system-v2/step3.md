# Step 3: dashboard-redesign

## 읽어야 할 파일
- `/phases/16-design-system-v2/step0~2.md`
- `/frontend/src/pages/HomePage.vue` (전면 교체 대상)
- `/frontend/src/stores/useAuthStore.ts` (사용자 정보)

## 작업
**HomePage 를 본격 운영 대시보드로 전면 재설계**. 5 모듈 카드 그리드 → 위젯 그리드 (KPI·차트·활동 피드·할 일·시스템 상태).

### 1. 백엔드 — 대시보드 데이터 API
`/api/dashboard/summary` 신규 endpoint:
- 응답: `{ openTickets, ticketsByPriority, ticketsByStatus, slaBreachCount, myOpenTickets, assetsByCategory, recentActivities[], myWorkflowSteps[] }`
- 권한: 인증만 — 결과는 현재 사용자 기준.
- 구현: 기존 ticket/asset/workflow 데이터 집계.

### 2. 프런트 — `HomePage.vue` 전면 재설계
레이아웃 (1920×1080 데스크탑 기준):

```
┌─────────────────────────────────────────────────────────────┐
│ 환영 헤더 — 사용자명 + 오늘 날짜 + 빠른 액션 (티켓 등록·자산 등록)│
├─────────────────────────────────────────────────────────────┤
│ KPI 그리드 (4 column)                                        │
│  [열린 티켓 27] [SLA 임박 3] [내 작업 8] [전체 자산 127]      │
├─────────────────────────────────────────────────────────────┤
│ 좌(2/3): 차트 카드 그리드                                    │
│  • 우선순위별 도넛 (priority distribution)                   │
│  • 상태별 막대 (status by week)                              │
│ 우(1/3): 최근 활동 피드 (실시간 느낌)                        │
├─────────────────────────────────────────────────────────────┤
│ 좌(1/2): 자산 분류별 도넛 + 라이프사이클 이벤트 sparkline    │
│ 우(1/2): 내 워크플로우 작업 리스트 (단계·SLA·바로 처리 버튼) │
└─────────────────────────────────────────────────────────────┘
```

### 3. 컴포넌트 분해 — `frontend/src/components/dashboard/`
- `DashboardWelcome.vue` — 환영 헤더
- `KpiGrid.vue` — KPI 4 카드
- `TicketByPriorityCard.vue` — DonutChart wrapper
- `TicketByStatusCard.vue` — BarChart wrapper
- `RecentActivityFeed.vue` — 시간순 활동 리스트 (Avatar + 텍스트 + 시각)
- `AssetByCategoryCard.vue` — DonutChart
- `MyWorkflowQueueCard.vue` — 본인 처리 대기 단계 리스트

### 4. composable
- `useDashboard()` — `/api/dashboard/summary` 호출 + reactive 갱신 + 30초 폴링 (선택, 옵션).

### 5. 모듈별 컬러 적용
- KPI 카드 좌측 보더 4px : 티켓=itsm, 자산=itam, 작업=common.
- 차트 시리즈 색: 토큰 색 사용.

### 6. 권한별 표시
- `useAuthStore.hasPermission('TICKET_*')` 없으면 티켓 위젯 숨김.
- 시스템 관리자는 추가 위젯 (사용자 수·메뉴 수·메타 그룹 수).

### 7. 빈 상태
- 데이터 0건 카드는 풍부한 empty state (lucide 아이콘 + "최근 활동이 없습니다 + 첫 항목 등록하기" 행동 유도).

## Acceptance Criteria
```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM

# 백엔드 endpoint
grep -RIn "/api/dashboard/summary" backend/src/main/java/

# 프런트 컴포넌트
cd frontend
test -f src/components/dashboard/DashboardWelcome.vue
test -f src/components/dashboard/KpiGrid.vue
test -f src/components/dashboard/TicketByPriorityCard.vue
test -f src/components/dashboard/TicketByStatusCard.vue
test -f src/components/dashboard/RecentActivityFeed.vue
test -f src/components/dashboard/AssetByCategoryCard.vue
test -f src/components/dashboard/MyWorkflowQueueCard.vue
test -f src/composables/useDashboard.ts

# HomePage 가 5 모듈 카드 grid 가 아닌 위젯 grid
! grep -q "modules\[" src/pages/HomePage.vue
grep -q "DashboardWelcome\|KpiGrid" src/pages/HomePage.vue

pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- HomePage 의 5 모듈 카드 그리드를 단순 위치 이동만 하지 마라 — 위젯 그리드로 전면 교체.
- KPI 카드를 평면 텍스트로 만들지 마라 — `KpiCard` 컴포넌트(step 2) 강제 사용.
- 데이터 0건일 때 빈 카드 그대로 두지 마라 — 풍부한 empty state.
- 차트가 다크 모드에서 색 박살 X (chart-theme 사용).
- 백엔드 새 endpoint 가 인증 가드 누락하지 마라.
- 운영 코드 console.log 금지.
