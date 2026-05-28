# Step 0: ticket-schema-and-entity

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "기술 스택"·"절대 규칙"(Backend Java 21 전용·Enum 사용·`ddl-auto: validate`)
- `/docs/ARCHITECTURE.md` — §2-3 백엔드 패키지 구조 (`itsm/` 모듈 위치), §11 로컬 인프라 (`sql/init/` 마운트)
- `/docs/PRD.md` — §4 ITSM 모듈 범위 (티켓·변경·문제·SLA·워크플로우), §9 M3
- `/docs/ADR.md` — ADR-003 (PostgreSQL JSONB), ADR-013 (TDD)
- `/sql/init/01_schema.sql` — 기존 `page_meta` 스키마 (트리거 회피 패턴 참고)
- `/backend/src/main/java/com/nkia/itg/meta/entity/PageMeta.java` — Entity 작성 스타일 (Lombok·`@Builder`·도메인 메서드 패턴)
- `/backend/src/main/java/com/nkia/itg/meta/domain/MetaStatus.java` — Enum 작성 스타일
- `/backend/src/main/resources/application-local.yml` — `ddl-auto: validate` 강제

`page_meta` 의 Entity·SQL·도메인 메서드 패턴을 그대로 `Ticket` 에 적용한다.

## 작업

이 step 의 목적은 **ITSM 티켓 도메인의 DB 스키마 + Entity + Enum + 도메인 메서드를 만들고, 상태 전이 규칙을 단위 테스트로 박는 것**이다. Repository·Service 는 다음 step.

### 1. DB 스키마 — `sql/init/02_ticket.sql`

`/Users/mwjeon/Projects/ai-work/harness_framework_ITSM/sql/init/02_ticket.sql`. 컨테이너 최초 기동 시 `01_schema.sql` 다음에 자동 실행된다 (파일명 사전순). 멱등성(`IF NOT EXISTS`).

```sql
-- ================================================================
-- ticket : ITSM 티켓 (PRD §4 M3 대상)
-- 컬럼:
--   id           BIGSERIAL  PK
--   ticket_no    TEXT       UNIQUE 표시용 (예: 'ITSM-00001')
--   title        TEXT       NOT NULL
--   content      TEXT       NULL
--   priority     TEXT       NOT NULL CHECK (LOW/MEDIUM/HIGH/CRITICAL)
--   status       TEXT       NOT NULL DEFAULT 'OPEN'
--                                   CHECK (OPEN/IN_PROGRESS/RESOLVED/CLOSED)
--   category     TEXT       NULL (BUG/REQ/QNA 등 가벼운 코드 문자열)
--   assignee_id  TEXT       NULL (사용자 모듈은 다음 phase — FK 없음)
--   created_at   TIMESTAMP  NOT NULL DEFAULT NOW()
--   updated_at   TIMESTAMP  NOT NULL DEFAULT NOW()
--   closed_at    TIMESTAMP  NULL  (status=CLOSED 전이 시 set)
-- ================================================================
CREATE TABLE IF NOT EXISTS ticket (
  id           BIGSERIAL PRIMARY KEY,
  ticket_no    VARCHAR(32)  NOT NULL,
  title        VARCHAR(200) NOT NULL,
  content      TEXT,
  priority     VARCHAR(10)  NOT NULL
                CONSTRAINT chk_ticket_priority
                CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
  status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
                CONSTRAINT chk_ticket_status
                CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
  category     VARCHAR(40),
  assignee_id  VARCHAR(60),
  created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
  closed_at    TIMESTAMP,
  CONSTRAINT uq_ticket_no UNIQUE (ticket_no)
);

CREATE INDEX IF NOT EXISTS idx_ticket_status     ON ticket(status);
CREATE INDEX IF NOT EXISTS idx_ticket_priority   ON ticket(priority);
CREATE INDEX IF NOT EXISTS idx_ticket_assignee   ON ticket(assignee_id);
CREATE INDEX IF NOT EXISTS idx_ticket_created_at ON ticket(created_at DESC);

-- updated_at 자동 갱신 트리거 (page_meta 의 fn_touch_updated_at 재사용 또는 동등)
-- 01_schema.sql 에 이미 정의된 fn_touch_updated_at() 을 그대로 사용한다.
DROP TRIGGER IF EXISTS trg_ticket_touch_updated_at ON ticket;
CREATE TRIGGER trg_ticket_touch_updated_at
BEFORE UPDATE ON ticket
FOR EACH ROW
EXECUTE FUNCTION fn_touch_updated_at();
```

> `01_schema.sql` 의 `fn_touch_updated_at()` 함수를 재사용한다. 그 함수가 없으면 step 0 의 검증이 실패 — 그때만 본 파일에 별도 함수 정의 (멱등성 `CREATE OR REPLACE`).

### 2. Enum 2종 — `com.nkia.itg.itsm.ticket.domain`

```
backend/src/main/java/com/nkia/itg/itsm/ticket/
  ├── domain/
  │   ├── Priority.java
  │   └── TicketStatus.java
  ├── entity/
  │   └── Ticket.java
  ├── repository/   (step 1 에서 작성)
  ├── service/      (step 1 에서 작성)
  ├── controller/   (step 2 에서 작성)
  └── dto/          (step 1·2 에서 작성)
```

```java
package com.nkia.itg.itsm.ticket.domain;
public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }
```

```java
package com.nkia.itg.itsm.ticket.domain;
public enum TicketStatus { OPEN, IN_PROGRESS, RESOLVED, CLOSED }
```

### 3. `Ticket` Entity — `com.nkia.itg.itsm.ticket.entity.Ticket`

요구사항:
- `@Entity @Table(name = "ticket")`.
- 필드 ↔ 컬럼 매핑 (snake_case ↔ camelCase, `@Column(name=...)` 명시).
- PK: `Long id` (`@GeneratedValue(strategy = GenerationType.IDENTITY)` — `BIGSERIAL`).
- 모든 컬럼 nullable 여부 정확히 매핑.
- Enum 은 `@Enumerated(EnumType.STRING)`.
- `@PrePersist` 로 `createdAt`/`updatedAt` 초기화 (DB 기본값과 중복되지만 명시적). `updatedAt` 의 DB 트리거 갱신은 신뢰 — Entity 에서 `@PreUpdate` 로도 갱신해도 무방하지만 둘 다 동작해도 문제 없음.
- Lombok: `@Getter`·`@Builder`·`@NoArgsConstructor(access=AccessLevel.PROTECTED)`·`@AllArgsConstructor(access=AccessLevel.PRIVATE)`. `@Setter` 금지.

#### 도메인 메서드 (Entity 본문)

```java
/**
 * 상태 전이. 허용 매트릭스:
 *   OPEN         → IN_PROGRESS / RESOLVED / CLOSED
 *   IN_PROGRESS  → RESOLVED / CLOSED
 *   RESOLVED     → CLOSED / IN_PROGRESS
 *   CLOSED       → 전이 불가 (IllegalStateException → 400)
 *
 * CLOSED 전이 시 closedAt = now.
 * CLOSED 에서 재오픈 거부.
 */
public void changeStatus(TicketStatus next) {
    if (this.status == TicketStatus.CLOSED) {
        throw new IllegalStateException(
            "CLOSED 티켓은 상태를 변경할 수 없습니다. (티켓: " + this.ticketNo + ")");
    }
    if (this.status == next) return;  // no-op
    final boolean allowed = switch (this.status) {
        case OPEN        -> EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED).contains(next);
        case IN_PROGRESS -> EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CLOSED).contains(next);
        case RESOLVED    -> EnumSet.of(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS).contains(next);
        case CLOSED      -> false;
    };
    if (!allowed) {
        throw new IllegalStateException(
            "허용되지 않은 상태 전이: " + this.status + " → " + next);
    }
    this.status = next;
    if (next == TicketStatus.CLOSED) this.closedAt = LocalDateTime.now();
}

public void changePriority(Priority next) {
    if (this.status == TicketStatus.CLOSED) {
        throw new IllegalStateException(
            "CLOSED 티켓은 우선순위를 변경할 수 없습니다.");
    }
    this.priority = next;
}

public void assign(String assigneeId) {
    if (this.status == TicketStatus.CLOSED) {
        throw new IllegalStateException(
            "CLOSED 티켓은 담당자 할당이 불가합니다.");
    }
    this.assigneeId = assigneeId;
}

public void updateContent(String title, String content, String category) {
    if (this.status == TicketStatus.CLOSED) {
        throw new IllegalStateException(
            "CLOSED 티켓은 내용을 수정할 수 없습니다.");
    }
    if (title    != null) this.title    = title;
    if (content  != null) this.content  = content;
    if (category != null) this.category = category;
}
```

> 외부 의존성(시계 등) 은 Entity 에서 직접 사용. 테스트 시 `LocalDateTime.now()` 의 정확한 값 검증은 회피하고 `isNotNull` 정도만.

### 4. 단위 테스트 — `com.nkia.itg.itsm.ticket.entity.TicketTest`

@DisplayName 한글 허용. 케이스:

1. `changeStatus_OPEN_에서_IN_PROGRESS_허용`.
2. `changeStatus_OPEN_에서_RESOLVED_허용`.
3. `changeStatus_OPEN_에서_CLOSED_허용_시_closedAt_set`.
4. `changeStatus_IN_PROGRESS_에서_OPEN_불허_IllegalStateException` — IN_PROGRESS 에서 OPEN 으로 되돌리기는 명시 매트릭스 외 — **disallowed** 로 정의.
5. `changeStatus_IN_PROGRESS_에서_RESOLVED_허용`.
6. `changeStatus_RESOLVED_에서_IN_PROGRESS_허용` (재오픈 케이스).
7. `changeStatus_CLOSED_에서_어떤_전이도_불허`.
8. `changeStatus_같은_상태_재호출_시_no-op` (예외 없이 통과, 그러나 closedAt 은 변경 안 됨).
9. `changePriority_CLOSED_불허`.
10. `assign_CLOSED_불허`.
11. `updateContent_CLOSED_불허`.
12. `updateContent_부분_업데이트_null_은_변경_없음`.

> 위 매트릭스 외 전이(예: OPEN → OPEN, IN_PROGRESS → OPEN) 는 모두 disallowed. 4번 케이스로 명시 검증.

### 5. 부팅 확인 (ddl-auto: validate)

`Ticket` Entity 가 스키마와 일치해야 부팅 성공.

## Acceptance Criteria

```bash
# 1) SQL 파일 존재 + 멱등성
test -f /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/sql/init/02_ticket.sql
grep -q "CREATE TABLE IF NOT EXISTS ticket"        sql/init/02_ticket.sql
grep -q "CHECK (priority IN"                       sql/init/02_ticket.sql
grep -q "CHECK (status IN"                         sql/init/02_ticket.sql
grep -q "UNIQUE (ticket_no)"                       sql/init/02_ticket.sql

# 2) Java 파일 존재
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
test -f backend/src/main/java/com/nkia/itg/itsm/ticket/domain/Priority.java
test -f backend/src/main/java/com/nkia/itg/itsm/ticket/domain/TicketStatus.java
test -f backend/src/main/java/com/nkia/itg/itsm/ticket/entity/Ticket.java

# 3) 단위 테스트
cd backend
./gradlew test --tests "com.nkia.itg.itsm.ticket.entity.TicketTest"
./gradlew build

# 4) Postgres 컨테이너 + 스키마 적용 (기존 컨테이너에는 수동 적용)
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
docker exec -i itg-postgres psql -U itg -d itgdb -f /docker-entrypoint-initdb.d/02_ticket.sql 2>/dev/null \
  || docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/02_ticket.sql
docker exec -i itg-postgres psql -U itg -d itgdb -c "\d ticket" | grep -q ticket_no

# 5) Spring Boot validate 부팅 통과 (Entity ↔ 스키마 일치)
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'
kill %1
```

> 기존 컨테이너 볼륨이 살아있으면 `/docker-entrypoint-initdb.d/02_ticket.sql` 자동 실행은 발생하지 않는다. 그래서 수동 `psql` 적용. 차후 컨테이너 새 볼륨에서 부팅하면 자동 적용된다.

## 검증 절차

1. 위 AC 모두 통과.
2. 아키텍처 체크리스트:
   - Enum 값이 스키마 CHECK 제약과 1:1 일치하는가?
   - `Ticket` Entity 의 필드 매핑이 컬럼명·타입·nullable 과 정확히 일치?
   - 도메인 메서드가 Entity 본문에 있고 Setter 가 없는가?
   - 상태 전이 매트릭스가 ADR/PRD 와 일치(`OPEN → IN_PROGRESS/RESOLVED/CLOSED`, `RESOLVED → IN_PROGRESS` 재오픈 허용, `CLOSED → 전이 불가`)?
   - 단위 테스트 12 케이스가 모두 통과?
   - `ddl-auto: validate` 로 부팅 성공? (Entity 매핑 미일치 시 즉시 부팅 실패)
3. index.json step 0 업데이트:
   - 성공 → `"summary": "sql/init/02_ticket.sql (ticket 테이블 + 인덱스 4종 + updated_at 트리거) + Enum 2종(Priority/TicketStatus) + Ticket Entity (@Enumerated.STRING, 도메인 메서드 changeStatus/changePriority/assign/updateContent, CLOSED 가드) + 단위 테스트 12 케이스. ddl-auto: validate 부팅 통과."`

## 금지사항

- `Ticket` Entity 에 `@Setter` 추가 금지. 이유: 도메인 메서드로만 변경. CLOSED 가드를 우회할 수 있다.
- `Ticket` 의 `assignee` 를 `User` 엔티티 연관(`@ManyToOne`)으로 정의하지 마라. 이유: 사용자 모듈은 다음 phase. 단순 `String assigneeId`.
- `Priority`·`TicketStatus` 를 `@Enumerated(EnumType.ORDINAL)` 로 매핑 금지. 이유: 스키마는 문자열 CHECK. STRING 강제.
- `ddl-auto` 를 `update`·`create` 로 바꾸지 마라. 이유: validate 강제 — 스키마는 SQL 파일이 정답.
- 상태 전이 매트릭스를 코드 안에 매직 if 흐트러트리지 마라. 본문 `switch` + EnumSet 으로 한 곳에 모은다.
- `IN_PROGRESS → OPEN` 같은 역방향 전이를 허용하지 마라. 이유: 워크플로우 정합성. 재오픈은 `RESOLVED → IN_PROGRESS` 로만.
- 프런트엔드 코드 수정 금지.
- 메타(`page_meta`) 시드를 이 step 에서 추가하지 마라. 이유: step 3 의 책임.
- 컬럼명에 한글·camelCase 사용 금지 (`ticket_no`·`assignee_id` 처럼 snake_case).
- 단위 테스트에 실 운영 데이터(사번·이메일·서버명) 넣지 마라. 가상 샘플(`샘플 티켓`, `assignee-sample-1`).
