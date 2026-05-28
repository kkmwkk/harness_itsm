# Step 1: docker-infra-and-schema

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "핵심 설계 사상" 섹션 (`systemType`·`packageType`·버전 그룹), "로컬 접속 정보"
- `/docs/ARCHITECTURE.md` — §3 메타 모델 (`page_meta` 스키마 핵심·뷰·트리거), §11 로컬 인프라
- `/docs/ADR.md` — ADR-003 (PostgreSQL + JSONB), ADR-005 (`systemType`·`packageType` 필수), ADR-006 (버전 그룹·자동 DEPRECATE), ADR-010 (Docker Desktop 표준)
- `/phases/0-meta-backend-m1/step0.md` — 이전 step 의 산출물·의도

이전 step 에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라. 특히:
- `backend/build.gradle` 의 의존성 (PostgreSQL JDBC, JPA starter)
- `backend/src/test/resources/application.yml` 의 자동 구성 제외 설정 (있다면 이 step 에서 정상화한다)
- `Application.java` 패키지·위치

## 작업

이 step 의 목적은 **PostgreSQL 16 컨테이너를 띄우고, `page_meta` 스키마 + 인덱스 + 뷰 + 자동 DEPRECATE 트리거를 컨테이너 최초 기동 시 자동 적용되도록 만들고, Spring Boot 가 그 DB 에 정상 접속하는 것**이다.

### 1. `docker-compose.yml` (루트)

`/Users/mwjeon/Projects/ai-work/harness_framework_ITSM/docker-compose.yml`:

```yaml
version: '3.9'

services:
  postgres:
    image: postgres:16
    container_name: itg-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: itgdb
      POSTGRES_USER: itg
      POSTGRES_PASSWORD: itg1234
    ports:
      - "5432:5432"
    volumes:
      - itg-pgdata:/var/lib/postgresql/data
      - ./sql/init:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U itg -d itgdb"]
      interval: 10s
      timeout: 5s
      retries: 5

  pgadmin:
    image: dpage/pgadmin4:latest
    container_name: itg-pgadmin
    restart: unless-stopped
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@itg.local
      PGADMIN_DEFAULT_PASSWORD: admin1234
    ports:
      - "5050:80"
    depends_on:
      - postgres

volumes:
  itg-pgdata:
```

### 2. `sql/init/01_schema.sql`

`/Users/mwjeon/Projects/ai-work/harness_framework_ITSM/sql/init/01_schema.sql` 을 작성한다. 본 SQL 은 **컨테이너 최초 기동 시 자동 실행**되며 (postgres 공식 이미지 `docker-entrypoint-initdb.d` 규약), 이미 데이터가 있는 볼륨에서는 실행되지 않는다. 따라서 멱등성(`IF NOT EXISTS`, `CREATE OR REPLACE`)을 반드시 유지한다.

스키마 요구사항(엄수):

```sql
-- 테이블 page_meta
CREATE TABLE IF NOT EXISTS page_meta (
  id              VARCHAR(100)  PRIMARY KEY,        -- '{groupId}-v{major}-{minor}' 권장
  title           VARCHAR(200)  NOT NULL,
  system_type     VARCHAR(20)   NOT NULL
                  CONSTRAINT chk_system_type
                  CHECK (system_type IN ('ITSM','ITAM','PMS','COMMON','SYSTEM')),
  package_type    VARCHAR(10)   NOT NULL
                  CONSTRAINT chk_package_type
                  CHECK (package_type IN ('PACKAGE','CUSTOM')),
  group_id        VARCHAR(100)  NOT NULL,
  major_version   INTEGER       NOT NULL DEFAULT 1
                  CONSTRAINT chk_major_version CHECK (major_version >= 1),
  minor_version   INTEGER       NOT NULL DEFAULT 1
                  CONSTRAINT chk_minor_version CHECK (minor_version >= 1),
  meta_status     VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                  CONSTRAINT chk_meta_status
                  CHECK (meta_status IN ('DRAFT','PUBLISHED','DEPRECATED','ARCHIVED')),
  meta_json       JSONB         NOT NULL,
  active          BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMP     NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_group_version UNIQUE (group_id, major_version, minor_version)
);

-- 인덱스
CREATE INDEX IF NOT EXISTS idx_page_meta_system          ON page_meta(system_type);
CREATE INDEX IF NOT EXISTS idx_page_meta_package         ON page_meta(package_type);
CREATE INDEX IF NOT EXISTS idx_page_meta_system_package  ON page_meta(system_type, package_type);
CREATE INDEX IF NOT EXISTS idx_page_meta_group           ON page_meta(group_id);
CREATE INDEX IF NOT EXISTS idx_page_meta_group_status    ON page_meta(group_id, meta_status);
CREATE INDEX IF NOT EXISTS idx_page_meta_json            ON page_meta USING GIN(meta_json);

-- 화면 노출용 뷰: groupId 별 PUBLISHED 최신 1건
CREATE OR REPLACE VIEW page_meta_active AS
SELECT DISTINCT ON (group_id) *
FROM   page_meta
WHERE  meta_status = 'PUBLISHED'
  AND  active      = TRUE
ORDER  BY group_id, major_version DESC, minor_version DESC;

-- 자동 DEPRECATE 트리거: 새 행이 PUBLISHED 로 전환되면 동일 group_id 의 기존 PUBLISHED → DEPRECATED
CREATE OR REPLACE FUNCTION fn_auto_deprecate_on_publish()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.meta_status = 'PUBLISHED'
     AND (OLD.meta_status IS DISTINCT FROM 'PUBLISHED') THEN
    UPDATE page_meta
       SET meta_status = 'DEPRECATED',
           updated_at  = NOW()
     WHERE group_id    = NEW.group_id
       AND id          <> NEW.id
       AND meta_status = 'PUBLISHED';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_auto_deprecate ON page_meta;
CREATE TRIGGER trg_auto_deprecate
AFTER UPDATE OF meta_status ON page_meta
FOR EACH ROW
EXECUTE FUNCTION fn_auto_deprecate_on_publish();

-- updated_at 자동 갱신 트리거 (옵션, 모든 UPDATE 에 적용)
CREATE OR REPLACE FUNCTION fn_touch_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_touch_updated_at ON page_meta;
CREATE TRIGGER trg_touch_updated_at
BEFORE UPDATE ON page_meta
FOR EACH ROW
EXECUTE FUNCTION fn_touch_updated_at();
```

> 트리거 핵심 규약:
> - `trg_auto_deprecate` 는 `AFTER UPDATE OF meta_status` — `INSERT` 에는 작동하지 않는다 (INSERT 시점에는 동일 그룹 기존 PUBLISHED 검사·갱신을 트리거가 아닌 Service 레이어에서 처리). 이유: INSERT 시점부터 자동 처리하면, 초기 데이터 시드 시 의도치 않게 다른 행이 DEPRECATE 되는 부작용이 생긴다.
> - `OLD.meta_status IS DISTINCT FROM 'PUBLISHED'` 로 같은 상태 재저장(no-op UPDATE) 시 트리거 비활성화.

### 3. Spring Boot 설정 분리

#### 3-1. `backend/src/main/resources/application.yml` (공통, profile 무관)

```yaml
spring:
  application:
    name: itg-backend
  jpa:
    open-in-view: false
  threads:
    virtual:
      enabled: true

server:
  port: 8080

springdoc:
  api-docs:
    path: /v3/api-docs
    enabled: true
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
    try-it-out-enabled: true
    display-request-duration: true
```

#### 3-2. `backend/src/main/resources/application-local.yml` (로컬 + Docker Postgres)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/itgdb
    username: itg
    password: itg1234
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          time_zone: Asia/Seoul

logging:
  level:
    org.hibernate.SQL: DEBUG
```

> `ddl-auto: validate` 강제: 엔티티 ↔ 스키마 불일치는 즉시 부팅 실패로 잡아낸다. `create`·`update` 사용 금지 (ADR-006 의 스키마 안전성 보장).

#### 3-3. 테스트 자동 구성 정상화

step 0 에서 `backend/src/test/resources/application.yml` 또는 별도 설정으로 DataSource/JPA 자동 구성을 제외했다면, 이 step 의 컨텍스트 로드 테스트가 의미를 갖도록 **테스트 전용 in-memory 설정** 또는 **Testcontainers** 로 전환한다. 가장 단순한 처리:

- `backend/src/test/resources/application.yml` 을 만들고, 테스트 시 별도 프로파일(`test`) 적용. 단, JPA·DataSource auto-configure 자체는 더 이상 제외하지 않는다 — Repository step(4) 에서 Testcontainers 가 들어오면서 정상 DataSource 가 필요해진다.
- 이 step 에서는 `@SpringBootTest` 가 DataSource 없이도 통과할 수 있도록, `ApplicationContextLoadTest` 에 `@AutoConfigureTestDatabase(replace = NONE)` 대신 **`@TestPropertySource` 로 `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration`** 를 주입하는 방법이 가장 깔끔하다. step 4 가 정식 통합 테스트로 대체한다.

### 4. `.gitignore` 보강

루트 `.gitignore` 에 다음을 추가 (이미 있으면 중복 추가하지 마라):

```
# Docker
**/pgadmin-data/
```

`sql/init/*.sql` 은 반드시 커밋된다 (컨테이너 초기화 자산).

## Acceptance Criteria

```bash
# 1) 컨테이너 기동 (백그라운드)
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
docker-compose ps                 # postgres 와 pgadmin 모두 healthy/running

# 2) 스키마 자동 적용 확인
docker exec -i itg-postgres psql -U itg -d itgdb -c "\d page_meta"
docker exec -i itg-postgres psql -U itg -d itgdb -c "\dv page_meta_active"
docker exec -i itg-postgres psql -U itg -d itgdb -c "\df fn_auto_deprecate_on_publish"
docker exec -i itg-postgres psql -U itg -d itgdb -c \
  "SELECT trigger_name FROM information_schema.triggers WHERE event_object_table='page_meta';"
# trg_auto_deprecate, trg_touch_updated_at 두 개 모두 출력되어야 한다.

# 3) 자동 DEPRECATE 트리거 동작 검증
docker exec -i itg-postgres psql -U itg -d itgdb <<'SQL'
INSERT INTO page_meta (id, title, system_type, package_type, group_id, major_version, minor_version, meta_status, meta_json)
VALUES ('itg-sample-v1-1', '샘플 페이지', 'ITSM', 'PACKAGE', 'itg-sample', 1, 1, 'PUBLISHED', '{}'::jsonb);

INSERT INTO page_meta (id, title, system_type, package_type, group_id, major_version, minor_version, meta_status, meta_json)
VALUES ('itg-sample-v1-2', '샘플 페이지', 'ITSM', 'PACKAGE', 'itg-sample', 1, 2, 'DRAFT', '{}'::jsonb);

-- PUBLISHED 전환: v1-1 이 자동으로 DEPRECATED 되어야 함
UPDATE page_meta SET meta_status = 'PUBLISHED' WHERE id = 'itg-sample-v1-2';

SELECT id, meta_status FROM page_meta WHERE group_id='itg-sample' ORDER BY minor_version;
-- 기대: itg-sample-v1-1 → DEPRECATED, itg-sample-v1-2 → PUBLISHED

-- 정리
DELETE FROM page_meta WHERE group_id='itg-sample';
SQL

# 4) Spring Boot DB 접속 + 엔티티 검증 부팅 (엔티티는 아직 없으므로 validate 통과 — 매핑 대상 0개)
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend
./gradlew build
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 8
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'
kill %1
```

## 검증 절차

1. 위 AC 커맨드를 순서대로 실행한다. 모든 단계가 통과해야 한다.
2. 아키텍처 체크리스트:
   - `page_meta` 의 CHECK 제약(`system_type`·`package_type`·`meta_status`·`major_version`·`minor_version`) 이 모두 포함되었는가? (ADR-005, ADR-006)
   - `UNIQUE (group_id, major_version, minor_version)` 가 강제되는가?
   - `page_meta_active` 뷰가 `PUBLISHED` + `active=TRUE` 만 반환하고 group_id 별 최신 1건만 반환하는가?
   - `trg_auto_deprecate` 가 INSERT 가 아닌 `AFTER UPDATE OF meta_status` 에서만 동작하는가?
   - `application-local.yml` 의 `ddl-auto` 가 `validate` 인가? (`create`/`update` 사용 금지)
   - Virtual Thread 활성화(`spring.threads.virtual.enabled: true`) 가 들어있는가?
3. 결과에 따라 `phases/0-meta-backend-m1/index.json` 의 step 1 을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "docker-compose.yml (postgres:16 + pgadmin), sql/init/01_schema.sql (page_meta + 인덱스 + page_meta_active 뷰 + 자동 DEPRECATE 트리거 + updated_at 트리거), application(-local).yml 분리, Virtual Thread 활성화, /actuator/health UP 확인"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`
   - 사용자 개입 필요 (Docker Desktop 미설치·5432 포트 충돌 등) → `"status": "blocked"`, `"blocked_reason": "<구체적 사유>"` 후 즉시 중단

## 금지사항

- `ddl-auto` 를 `create`/`create-drop`/`update` 로 설정하지 마라. 이유: 운영 안전성. 스키마 변경은 SQL 파일로 명시한다.
- `trg_auto_deprecate` 를 `BEFORE INSERT` / `AFTER INSERT` 로 설정하지 마라. 이유: 초기 시드 데이터가 의도치 않게 서로를 DEPRECATE 시키는 사고가 발생한다. 트리거는 UPDATE 전이에만 동작해야 한다.
- `sql/init/` 에 시드 데이터(`INSERT INTO ...`) 를 넣지 마라. 이유: 초기화 스크립트는 스키마/뷰/트리거만. 시드는 step 7 의 시나리오 검증에서 다룬다.
- `application.yml` 에 DB 비밀번호를 본 값 그대로 두는 것은 로컬 한정 허용이지만, 다른 프로파일(`dev`·`prod`) 설정 파일을 이 step 에서 추가하지 마라. 이유: scope 최소화.
- `PageMeta` Entity 를 미리 만들지 마라. 이유: step 2 의 책임. 이번 step 의 `ddl-auto: validate` 는 매핑된 엔티티가 0개이므로 통과한다.
- `frontend/` 디렉토리·파일을 만들지 마라. 이유: 다음 phase 의 범위.
