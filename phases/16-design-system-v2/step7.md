# Step 7: notification-center

## 읽어야 할 파일
- `/docs/UI_GUIDE.md` v2 §5 알림 컴포넌트
- `/phases/16-design-system-v2/step0~6.md`
- `/frontend/src/components/layout/TopBar.vue`

## 작업
**알림 센터 + 활동 피드** — TopBar 종 아이콘 + 드롭다운 패널 + 미읽음 개수·시각·링크.

### 1. 백엔드 — 알림 모델 (간단)
- `notification` 테이블: id, user_id, type, title, body, related_url, read_at, created_at.
- `/api/notifications?unreadOnly=true&page&size` — 본인 알림 목록.
- `PATCH /api/notifications/{id}/read` — 읽음 처리.
- `POST /api/notifications/read-all` — 전체 읽음.
- `GET /api/notifications/unread-count`.

생성 트리거 (자동 발생):
- 워크플로우 단계 진입 시 assignee_role 사용자에게 알림 생성 (WorkflowEngineService 확장).
- 본인 티켓 상태 변경 시 requester 에게 알림.

### 2. 프런트 — `frontend/src/components/notification/`
- `NotificationBell.vue` — 종 아이콘 + 미읽음 뱃지 (개수). TopBar 에 통합.
- `NotificationPanel.vue` — 드롭다운 (Popover): 미읽음 리스트 + "모두 읽음" + "전체 보기" 링크.
- `NotificationItem.vue` — 시각·아이콘·제목·본문·링크.
- `useNotifications.ts` — fetch + 30초 폴링 + 읽음 처리 + 미읽음 카운트.

### 3. 활동 피드 (대시보드 통합)
HomePage 의 RecentActivityFeed (step 3 의 component) 와 별개로 NotificationItem 재사용.

### 4. 라우트
- `/notifications` — 전체 알림 목록 페이지 (페이지네이션).

### 5. 단위 테스트
- `useNotifications.spec` — fetch·markRead·unreadCount.

## Acceptance Criteria
```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM

# 백엔드
grep -RIn "/api/notifications" backend/src/main/java/
grep -RIn "CREATE TABLE IF NOT EXISTS notification" sql/init/

# 프런트
cd frontend
test -f src/components/notification/NotificationBell.vue
test -f src/components/notification/NotificationPanel.vue
test -f src/components/notification/NotificationItem.vue
test -f src/composables/useNotifications.ts
grep -q "NotificationBell" src/components/layout/TopBar.vue
grep -q "/notifications" src/router/index.ts

pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- 실시간 websocket 도입 금지 — 30초 폴링 stub 만.
- 알림 자동 생성을 워크플로우 외 모듈에 흩뿌리지 마라 — NotificationService 통일.
- 알림 본문에 raw HTML 허용 금지 — sanitize 또는 plain text.
- 운영 코드 console.log 금지.
