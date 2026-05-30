# Step 4: kanban-board

## 읽어야 할 파일
- `/phases/16-design-system-v2/step0~3.md`
- `/phases/12-domain-depth-workflow-category/index.json` (WorkflowEngineService)
- `/frontend/src/types/ticket.ts`·`useWorkflow.ts`

## 작업
**ITSM 티켓 칸반 보드** — 상태 컬럼 4개(OPEN / IN_PROGRESS / RESOLVED / CLOSED) × 드래그 이동으로 상태 전이 트리거.

### 1. 컴포넌트 — `frontend/src/components/dynamic/DynamicKanban.vue`
- props: `tickets: TicketSummary[]`·`onMove: (ticket, fromStatus, toStatus) => Promise<void>`
- 4 컬럼 가로 배치 (flex). 각 컬럼:
  - 컬럼 헤더: 상태 라벨 + 개수 뱃지 (StatusBadge 재사용)
  - 카드 리스트 (vue-draggable-plus 사용)
  - 카드: ticketNo·title·priority 뱃지·assignee 아바타·SLA 잔여시간

- 드래그 이동 시 onMove 호출 → 백엔드 PATCH `/api/tickets/{id}/status` → 성공 시 reload, 실패 시 카드 원위치 + 에러 토스트.

### 2. 라우트 — `/itsm/board`
`pages/itsm/BoardPage.vue` — 티켓 목록 fetch + DynamicKanban 사용.
TopBar 의 ITSM 메뉴에 "보드 보기" 토글 또는 별도 메뉴 항목 시드 추가.

### 3. 카드 디테일
- ticketNo (mono font·12px)
- title (14px·600)
- priority 뱃지 (CRITICAL 만 red)
- assignee 아바타 (initials circle)
- 우상단 dropdown (편집·삭제 — 권한별)
- 카드 hover: shadow-hover + 우상단 quick action 아이콘 fade-in

### 4. 빈 컬럼 상태
- "이 상태에 항목이 없습니다" 회색 텍스트.

### 5. 접근성
- 카드 tab focusable + Enter → 상세 라우팅.
- 드래그 외 키보드 단축키 (Alt+→ 다음 상태로) — 선택.

## Acceptance Criteria
```bash
cd frontend
test -f src/components/dynamic/DynamicKanban.vue
test -f src/pages/itsm/BoardPage.vue
grep -q "/itsm/board" src/router/index.ts
pnpm type-check
pnpm lint
pnpm build
pnpm test
pnpm dev &
sleep 5
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/itsm/board)" = "200"
kill %1
```

## 금지사항
- 드래그 이동 시 백엔드 상태 전이 매트릭스를 frontend 에서 강제하지 마라 — 백엔드의 changeStatus 가 거부하면 토스트로 표시.
- 드래그 라이브러리 충돌 — phase 14 의 vue-draggable-plus 재사용.
- 컬럼 width hard-code 금지 — flex 1 또는 토큰.
- CLOSED 컬럼으로 드래그 시 사용자에게 확인 다이얼로그 (실수 방지) — 선택.
- 운영 코드 console.log 금지.
