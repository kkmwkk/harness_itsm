-- PMS(project/task) 스키마 — 통합 테스트(Testcontainers, ddl-auto=validate)용 DDL.
-- 운영 시드(sql/init/19_pms_seed.sql)의 CREATE TABLE 부분과 동일하게 유지한다(시드/메타 INSERT 제외).
CREATE TABLE IF NOT EXISTS project (
    id            BIGSERIAL    PRIMARY KEY,
    code          VARCHAR(40)  NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    owner_user_id BIGINT,
    dept_id       BIGINT,
    start_date    DATE,
    due_date      DATE,
    progress      INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS task (
    id               BIGSERIAL    PRIMARY KEY,
    project_id       BIGINT       NOT NULL REFERENCES project(id),
    title            VARCHAR(200) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'TODO',
    assignee_user_id BIGINT,
    start_date       DATE,
    due_date         DATE,
    progress         INT          NOT NULL DEFAULT 0,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_task_project_id ON task(project_id);
