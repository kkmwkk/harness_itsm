# Step 4: workflow-panel-frontend

## 읽어야 할 파일
- `/CLAUDE.md`·`/docs/PRD.md` §5-3·`/docs/ARCHITECTURE.md` §4·§15
- `/phases/12-domain-depth-workflow-category/step0~3.md`
- `/phases/10-menu-and-routing/index.json` (useAuthStore)
- `/frontend/src/components/dynamic/DynamicPage.vue`·`pages/itam/DetailPage.vue` (상세 패턴)

## 작업
티켓 상세 페이지 + `WorkflowPanel.vue` (단계 진행·액션 버튼·이력) + 요청 유형 선택 UI.

### 1. 타입 — `src/types/workflow.ts`
```ts
export type StepAction = 'APPROVE'|'REJECT'|'FORWARD'|'COMPLETE'|'CONFIRM'|'REOPEN';
export type WorkflowStatus = 'RUNNING'|'COMPLETED'|'CANCELED'|'REJECTED';

export interface WorkflowStep {
  index: number; name: string;
  assigneeRoleCode: string; slaMinutes: number | null;
  allowedActions: StepAction[];
}

export interface WorkflowInstanceStepHistory {
  id: number; stepIndex: number; stepName: string;
  assigneeRole: string | null; assignedToUserId: number | null;
  startedAt: string; completedAt: string | null; slaDueAt: string | null;
  action: StepAction | null; actionByUserId: number | null; actionComment: string | null;
}

export interface WorkflowInstanceView {
  id: number; workflowDefCode: string;
  ticketId: number; currentStepIndex: number;
  status: WorkflowStatus; startedAt: string; completedAt: string | null;
  steps: WorkflowStep[];                       // definition 의 steps 스냅샷
  history: WorkflowInstanceStepHistory[];
}
```

### 2. composable — `src/composables/useWorkflow.ts`
- `useWorkflow(ticketId)` → 인스턴스 + 단계 정의 + 이력 fetch.
- `executeAction(stepIndex, action, comment)` → POST.
- 성공 후 자체 reload + 상위 ticket 도 reload (페이지 차원에서).

### 3. `WorkflowPanel.vue` — `src/components/dynamic/WorkflowPanel.vue`
요구사항:
- 상단: 진행 단계 시각화 (steps[0..N] 가로 stepper, current_step_index 하이라이트).
- 중간: 현재 단계의 정보 (단계명·assignee role·SLA 잔여시간·`allowedActions` 버튼).
- 하단: 이력 테이블 (단계명·시작/종료·action·실행자·comment).
- 액션 버튼은 `useAuthStore.hasRole(currentStep.assigneeRoleCode)` 인 사용자에게만 표시.
- comment 입력 textarea + 액션 클릭 시 `executeAction`.
- 실 진행: 액션 성공 → 토스트 + reload + 상위 페이지 grid reload 트리거 (emit).

### 4. 티켓 상세 페이지 — `src/pages/itsm/TicketDetailPage.vue`
- 라우트 `/itsm/:id(\d+)` 추가 (라우터 등록).
- 자산 상세 패턴(`pages/itam/DetailPage.vue`) 동일:
  - 티켓 단건 fetch + 등록 메타(pageMetaIdAtRegistration) 로 폼 화면 복원.
  - WorkflowPanel 통합.
- 그리드 행 클릭 → `/itsm/{id}` 라우팅 (router itsm.meta.detailUrlTemplate).

### 5. 요청 유형 선택 (등록 다이얼로그 확장)
- `/itsm` 진입 → "등록" 버튼 → **먼저 요청 유형 5종 선택 다이얼로그** → 선택된 유형의 form_meta_group_id 로 DynamicPage 가 그 메타의 폼을 표시 → 제출.
- 또는: 등록 버튼이 드롭다운으로 5종 표시 → 선택 후 그 유형 메타로 폼.
- 본 step: **DynamicPage 의 `actions` 메타가 `type=dialog-form` 인 경우 폼 메타 ID 를 별도 지정 가능하게 확장**.
- 또는 단순화: 신규 라우트 `/itsm/new/:requestType` → 그 유형의 메타로 DynamicForm 렌더.

권장: 신규 라우트 방식. 단순.

### 6. 단위 테스트
- `useWorkflow.spec` — fetch · executeAction.
- `WorkflowPanel.spec` (vue-test-utils) — 현재 단계의 액션 버튼 권한 분기.

## Acceptance Criteria
```bash
cd frontend
test -f src/types/workflow.ts
test -f src/composables/useWorkflow.ts
test -f src/components/dynamic/WorkflowPanel.vue
test -f src/pages/itsm/TicketDetailPage.vue
grep -q "itsm/:id" src/router/index.ts
grep -q "itsm/new" src/router/index.ts

pnpm type-check
pnpm lint
pnpm build
pnpm test

pnpm dev &
sleep 6
for p in /itsm /itsm/1 /itsm/new/INCIDENT; do
  test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173$p)" = "200"
done
kill %1
```

## 금지사항
- 모든 사용자에게 모든 단계 액션 노출 금지 — role 매칭 사용자만.
- comment 필수 검증 누락 시 REJECT/REOPEN 같은 부정 액션 사고 — 본 phase 는 comment 옵션이지만 REJECT 시 권장.
- 백엔드 코드 수정 금지.
- WorkflowPanel 이 도메인-특수 분기(ticket vs change 등) 금지 — steps 메타로만.
- 운영 코드 console.log 금지.
