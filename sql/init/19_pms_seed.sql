-- Polestar10 ITG v2.1 — PMS(프로젝트·태스크) 스키마 + 시드 (phase 16 step 5, PRD §4-4)
-- 컨테이너 최초 기동 시 docker-entrypoint-initdb.d 규약으로 파일명 사전순(18_* 다음) 자동 실행.
-- 멱등성: CREATE TABLE IF NOT EXISTS + ON CONFLICT DO NOTHING.
-- ※ 통합 테스트(Testcontainers, ddl-auto=validate)용 DDL 은 src/test/resources/init/19_pms.sql 로 분리 복사.

-- ============================================================================
-- 1) 스키마 — project / task
-- ============================================================================
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

-- ============================================================================
-- 2) itg-project PageMeta (PMS 모듈 — /system/meta viewer·문서용). 즉시 PUBLISHED.
-- ============================================================================
INSERT INTO page_meta (id, title, system_type, package_type, group_id,
                       major_version, minor_version, meta_status, meta_json)
VALUES ('itg-project-v1-1', '프로젝트', 'PMS', 'PACKAGE', 'itg-project',
        1, 1, 'PUBLISHED', '
{
  "api": "/api/projects",
  "grid": {
    "columns": [
      { "field": "code",      "label": "코드",       "type": "text", "width": 120 },
      { "field": "name",      "label": "프로젝트명", "type": "text", "flex": 1 },
      { "field": "status",    "label": "상태",       "type": "status", "width": 110 },
      { "field": "startDate", "label": "시작일",     "type": "date", "width": 120 },
      { "field": "dueDate",   "label": "마감일",     "type": "date", "width": 120 },
      { "field": "progress",  "label": "진행률",     "type": "number", "width": 90 }
    ]
  },
  "form": {
    "layout": "two-column",
    "fields": [
      { "name": "code",      "label": "코드",       "type": "text", "required": true },
      { "name": "name",      "label": "프로젝트명", "type": "text", "required": true, "span": 2 },
      { "name": "status",    "label": "상태",       "type": "select", "options": [
          { "value": "PLANNED",     "label": "계획" },
          { "value": "IN_PROGRESS", "label": "진행 중" },
          { "value": "DONE",        "label": "완료" },
          { "value": "ON_HOLD",     "label": "보류" }
      ] },
      { "name": "startDate", "label": "시작일", "type": "date" },
      { "name": "dueDate",   "label": "마감일", "type": "date" }
    ]
  },
  "actions": [ { "id": "create", "label": "등록", "type": "dialog-form" } ]
}
'::jsonb)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 3) 샘플 프로젝트 3종 + 태스크 (가상 샘플 — ADR-011)
-- ============================================================================
INSERT INTO project (code, name, status, start_date, due_date, progress)
VALUES
    ('PRJ-2026-001', 'ITSM 워크플로우 고도화', 'IN_PROGRESS', DATE '2026-05-01', DATE '2026-07-15', 45),
    ('PRJ-2026-002', '자산 실사 캠페인',       'PLANNED',     DATE '2026-06-10', DATE '2026-08-31', 0),
    ('PRJ-2026-003', '디자인 시스템 v2 적용',  'IN_PROGRESS', DATE '2026-05-20', DATE '2026-06-30', 70)
ON CONFLICT (code) DO NOTHING;

-- 각 프로젝트의 태스크 (project code 로 project_id 조회)
INSERT INTO task (project_id, title, status, start_date, due_date, progress, sort_order)
SELECT p.id, t.title, t.status, t.start_date, t.due_date, t.progress, t.sort_order
FROM project p
JOIN (
    VALUES
        ('PRJ-2026-001', '요구사항 분석',   'DONE',        DATE '2026-05-01', DATE '2026-05-12', 100, 0),
        ('PRJ-2026-001', '엔진 설계',       'IN_PROGRESS', DATE '2026-05-13', DATE '2026-06-10', 60,  1),
        ('PRJ-2026-001', '통합 테스트',     'TODO',        DATE '2026-06-11', DATE '2026-07-15', 0,   2),
        ('PRJ-2026-002', '대상 자산 선정',   'TODO',        DATE '2026-06-10', DATE '2026-06-30', 0,   0),
        ('PRJ-2026-002', '현장 실사',       'TODO',        DATE '2026-07-01', DATE '2026-08-15', 0,   1),
        ('PRJ-2026-003', '토큰·다크모드',   'DONE',        DATE '2026-05-20', DATE '2026-06-02', 100, 0),
        ('PRJ-2026-003', '대시보드·차트',   'IN_PROGRESS', DATE '2026-06-03', DATE '2026-06-20', 80,  1),
        ('PRJ-2026-003', '간트·타임라인',   'IN_PROGRESS', DATE '2026-06-21', DATE '2026-06-30', 30,  2)
) AS t(pcode, title, status, start_date, due_date, progress, sort_order)
  ON t.pcode = p.code
WHERE NOT EXISTS (
    SELECT 1 FROM task x WHERE x.project_id = p.id AND x.title = t.title
);
