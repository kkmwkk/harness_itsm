-- 통합 테스트(Testcontainers, ddl-auto=validate)용 notification DDL.
-- 운영 sql/init/20_notification.sql 의 스키마 부분과 동일하게 유지한다.
CREATE TABLE IF NOT EXISTS notification (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        TEXT,
    related_url VARCHAR(300),
    read_at     TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notification_user        ON notification(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_user_unread ON notification(user_id, read_at);
