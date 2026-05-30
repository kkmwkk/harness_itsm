-- Polestar10 ITG v2.1 — 알림(notification) 스키마 (phase 16 step 7, PRD §6 알림 컴포넌트)
-- 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약으로 파일명 사전순(19_* 다음) 자동 실행.
-- 멱등성: CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS.
-- ※ 통합 테스트(Testcontainers, ddl-auto=validate)용 DDL 은 src/test/resources/init/20_notification.sql 로 분리 복사.

-- ============================================================================
-- notification : 사용자별 알림 (워크플로우 단계 진입·티켓 상태 변경 등에서 자동 생성)
--   본문(body)은 plain text 만 — raw HTML 저장 금지(생성 시 NotificationService 가 텍스트로만 작성).
-- ============================================================================
CREATE TABLE IF NOT EXISTS notification (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,            -- 수신 사용자 (user_account.id soft FK)
    type        VARCHAR(40)  NOT NULL,            -- 'WORKFLOW_STEP_ASSIGNED' / 'TICKET_STATUS_CHANGED'
    title       VARCHAR(200) NOT NULL,
    body        TEXT,
    related_url VARCHAR(300),                     -- 클릭 시 이동할 앱 내 경로 (예: '/itsm/42')
    read_at     TIMESTAMP,                        -- NULL 이면 미읽음
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notification_user        ON notification(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_user_unread ON notification(user_id, read_at);
