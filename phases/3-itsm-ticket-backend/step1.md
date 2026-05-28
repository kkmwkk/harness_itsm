# Step 1: ticket-repository-and-service

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "절대 규칙"(트랜잭션 경계는 Service, `ApiResponse<T>` 래퍼)
- `/docs/ARCHITECTURE.md` — §7 API 설계 일반 패턴, §9 트랜잭션·동시성
- `/docs/ADR.md` — ADR-009 (`ApiResponse<T>`), ADR-012 (TestFixture), ADR-013 (TDD)
- `/phases/3-itsm-ticket-backend/step0.md` — Ticket Entity·Enum·상태 전이 매트릭스
- `/backend/src/main/java/com/nkia/itg/meta/service/MetaService.java` — Service 작성 스타일 (`@Transactional` 클래스 레벨·read-only 메서드·Entity 도메인 메서드 호출)
- `/backend/src/main/java/com/nkia/itg/meta/repository/MetaRepository.java` — Repository 작성 스타일 (Derived Query)
- `/backend/src/main/java/com/nkia/itg/common/exception/ITGException.java` — 예외 던지기 패턴
- `/backend/src/test/java/com/nkia/itg/fixture/MetaFixture.java` — Fixture 패턴

## 작업

이 step 의 목적은 **`TicketRepository`·`TicketService`·DTO Record 를 만들고, Mockito 단위 테스트로 비즈니스 규칙(상태 전이·CLOSED 가드·페이지 조회)을 박는 것**이다. Controller·시드는 step 2·3.

### 1. 식별자 발급 정책 — `ticket_no`

스키마의 `id` 는 BIGSERIAL 자동. `ticket_no` 는 표시용 (예: `ITSM-00001`) — Service 에서 생성:

- 생성 규칙: `"ITSM-" + String.format("%05d", id)` — `save` 후 id 가 채워지면 그 id 를 기준으로 `ticket_no` 를 생성하여 한 번 더 update. 또는 `@PostPersist` 콜백.
- **권장**: Service 에서 `save` 한 뒤 `entity.assignTicketNo("ITSM-" + ...)` 호출 → 한 트랜잭션 안에서 같은 영속 객체에 적용 (flush 시 update). 도메인 메서드를 Entity 에 추가:

```java
// Ticket.java 에 추가 (이 step 에서 한 메서드 추가만 — 다른 부분은 step 0 그대로)
/** ticket_no 한 번만 부여 (이미 set 되어 있으면 IllegalStateException). */
public void assignTicketNo(String ticketNo) {
    if (this.ticketNo != null && !this.ticketNo.isBlank()) {
        throw new IllegalStateException("ticket_no 는 이미 부여되었습니다: " + this.ticketNo);
    }
    this.ticketNo = ticketNo;
}
```

> Entity 도메인 메서드 한 개 추가 = step 0 의 산출물에 작은 patch. 본 step 의 변경 범위에 포함된다고 명시.

### 2. DTO Record — `com.nkia.itg.itsm.ticket.dto`

#### `TicketCreateRequest`

```java
public record TicketCreateRequest(
    @Schema(example = "샘플 티켓 제목")
    @NotBlank @Size(max = 200) String title,

    @Schema(example = "샘플 본문")
    String content,

    @Schema(example = "MEDIUM")
    @NotNull Priority priority,

    @Schema(example = "BUG")
    String category,

    @Schema(example = "assignee-sample-1")
    String assigneeId
) {}
```

#### `TicketUpdateRequest`

```java
public record TicketUpdateRequest(
    @Size(max = 200) String title,
    String           content,
    String           category
) {}
// priority / status / assignee 변경은 별도 전용 엔드포인트 (step 2)
```

#### `TicketStatusChangeRequest`

```java
public record TicketStatusChangeRequest(
    @Schema(example = "IN_PROGRESS")
    @NotNull TicketStatus next
) {}
```

#### `TicketAssignRequest`

```java
public record TicketAssignRequest(
    @Schema(example = "assignee-sample-2")
    String assigneeId   // null/blank 허용 — 담당자 해제 의미
) {}
```

#### `TicketPriorityChangeRequest`

```java
public record TicketPriorityChangeRequest(
    @Schema(example = "HIGH")
    @NotNull Priority next
) {}
```

#### `TicketResponse` (응답)

```java
public record TicketResponse(
    Long           id,
    String         ticketNo,
    String         title,
    String         content,
    Priority       priority,
    TicketStatus   status,
    String         category,
    String         assigneeId,
    LocalDateTime  createdAt,
    LocalDateTime  updatedAt,
    LocalDateTime  closedAt
) {
    public static TicketResponse from(Ticket e) { /* ... */ }
}
```

#### `TicketSummary` (목록용 경량 — content 제외)

```java
public record TicketSummary(
    Long          id,
    String        ticketNo,
    String        title,
    Priority      priority,
    TicketStatus  status,
    String        assigneeId,
    LocalDateTime createdAt
) {
    public static TicketSummary from(Ticket e) { /* ... */ }
}
```

### 3. `TicketRepository` — `com.nkia.itg.itsm.ticket.repository`

`extends JpaRepository<Ticket, Long>`.

메서드 시그니처:

```java
Optional<Ticket> findByTicketNo(String ticketNo);

/** 페이지 + 정렬. status·priority·assigneeId 필터 옵션. */
@Query("""
    select t from Ticket t
     where (:status     is null or t.status     = :status)
       and (:priority   is null or t.priority   = :priority)
       and (:assigneeId is null or t.assigneeId = :assigneeId)
    """)
Page<Ticket> search(
    @Param("status")     TicketStatus status,
    @Param("priority")   Priority     priority,
    @Param("assigneeId") String       assigneeId,
    Pageable             pageable
);
```

> 페이지·정렬은 `Pageable` 인자로 외부 위임. Service 에서 `PageRequest.of(page, size, Sort.by("createdAt").descending())` 같은 표준 사용.

### 4. `TicketService` — `com.nkia.itg.itsm.ticket.service`

```java
@Service
@Transactional
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    /* ... */
}
```

#### 메서드 시그니처

```java
/** 신규 생성. ticket_no 는 ITSM-{id5} 패턴으로 부여. */
TicketResponse create(TicketCreateRequest req);

/** 단건 조회. 없으면 ITGException("TICKET_NOT_FOUND", 404). */
@Transactional(readOnly = true)
TicketResponse getById(Long id);

@Transactional(readOnly = true)
TicketResponse getByTicketNo(String ticketNo);

/** 목록 (필터 + 페이지) */
@Transactional(readOnly = true)
Page<TicketSummary> search(
    TicketStatus status, Priority priority, String assigneeId,
    int page, int size
);

/** 본문 수정 (title/content/category 부분). CLOSED 면 도메인 예외. */
TicketResponse update(Long id, TicketUpdateRequest req);

/** 상태 전이 (도메인 메서드 호출). 매트릭스 위반 시 IllegalStateException → 400. */
TicketResponse changeStatus(Long id, TicketStatusChangeRequest req);

/** 우선순위 변경. CLOSED 면 도메인 예외. */
TicketResponse changePriority(Long id, TicketPriorityChangeRequest req);

/** 담당자 변경(또는 해제). CLOSED 면 도메인 예외. */
TicketResponse assign(Long id, TicketAssignRequest req);
```

> 모든 비즈니스 규칙은 Entity 의 도메인 메서드에 박혀 있으므로 Service 는 호출만. Service 에 if/else 분기 최소화.

#### `create` 의사코드

```java
public TicketResponse create(TicketCreateRequest req) {
    Ticket t = Ticket.builder()
        .ticketNo(null)                      // 일단 null, 저장 후 부여
        .title(req.title())
        .content(req.content())
        .priority(req.priority())
        .status(TicketStatus.OPEN)
        .category(req.category())
        .assigneeId(req.assigneeId())
        .build();
    Ticket saved = ticketRepository.save(t);   // id 채워짐
    saved.assignTicketNo("ITSM-" + String.format("%05d", saved.getId()));
    return TicketResponse.from(saved);          // flush 시 update
}
```

> `ticket_no` 컬럼이 NOT NULL UNIQUE 이므로 한 트랜잭션 안에서 insert + update 가 필요. JPA 의 dirty checking 으로 처리. 만약 `NOT NULL` 위반이 발생하면 `nullable = true` 로 두고 `BEFORE INSERT` 트리거에서 자동 채움도 검토 — 단, 이번 step 은 Service 책임으로 시작. 위반 시 step 0 의 SQL 을 보강.

### 5. `TicketFixture` — `src/test/java/com/nkia/itg/fixture/TicketFixture.java`

```java
public final class TicketFixture {
    private TicketFixture() {}

    public static Ticket.TicketBuilder baseBuilder() {
        return Ticket.builder()
            .ticketNo("ITSM-99999")           // 테스트용 (실 시퀀스 회피)
            .title("샘플 티켓")
            .content("샘플 본문")
            .priority(Priority.MEDIUM)
            .status(TicketStatus.OPEN)
            .category("BUG")
            .assigneeId("assignee-sample-1");
    }

    public static Ticket open()       { return baseBuilder().status(TicketStatus.OPEN).build(); }
    public static Ticket inProgress() { return baseBuilder().status(TicketStatus.IN_PROGRESS).build(); }
    public static Ticket resolved()   { return baseBuilder().status(TicketStatus.RESOLVED).build(); }
    public static Ticket closed()     {
        Ticket t = baseBuilder().status(TicketStatus.CLOSED).build();
        // closedAt 도 set 필요 — 단순화 위해 reflection 회피하고 builder 에 closedAt 추가하거나
        // 도메인 메서드를 통해 OPEN → CLOSED 전이로 closedAt 자동 set
        return t;
    }
}
```

### 6. 단위 테스트 — `com.nkia.itg.itsm.ticket.service.TicketServiceTest`

`@ExtendWith(MockitoExtension.class)`, `@Mock TicketRepository`, `@InjectMocks TicketService`. 케이스:

1. `create_ticket_no_는_ITSM_5자리_패턴` — `save` 가 id=42 반환 시 결과 `ticketNo == "ITSM-00042"`. assignTicketNo 가 호출됨을 확인.
2. `create_status_기본_OPEN_closedAt_null`.
3. `getById_없으면_TICKET_NOT_FOUND_404`.
4. `getById_정상_경로_TicketResponse_반환`.
5. `update_CLOSED_티켓_IllegalStateException` (도메인 예외 → GlobalExceptionHandler 가 400).
6. `update_부분_업데이트_null_은_변경_없음`.
7. `changeStatus_OPEN_to_RESOLVED_허용`.
8. `changeStatus_CLOSED_to_OPEN_IllegalStateException`.
9. `changeStatus_to_CLOSED_시_closedAt_set` — `isNotNull` 정도로만.
10. `changePriority_CLOSED_불허`.
11. `assign_CLOSED_불허`.
12. `assign_assigneeId_null_허용_담당자_해제` (CLOSED 가 아닐 때).
13. `search_status_priority_assignee_필터_repository_에_전달` — Mock 검증.
14. `search_pageable_생성_Sort_createdAt_DESC` — Mock argument captor.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend

# 1) 파일 존재
test -f src/main/java/com/nkia/itg/itsm/ticket/repository/TicketRepository.java
test -f src/main/java/com/nkia/itg/itsm/ticket/service/TicketService.java
test -f src/main/java/com/nkia/itg/itsm/ticket/dto/TicketResponse.java
test -f src/main/java/com/nkia/itg/itsm/ticket/dto/TicketSummary.java
test -f src/main/java/com/nkia/itg/itsm/ticket/dto/TicketCreateRequest.java
test -f src/main/java/com/nkia/itg/itsm/ticket/dto/TicketUpdateRequest.java
test -f src/main/java/com/nkia/itg/itsm/ticket/dto/TicketStatusChangeRequest.java
test -f src/main/java/com/nkia/itg/itsm/ticket/dto/TicketPriorityChangeRequest.java
test -f src/main/java/com/nkia/itg/itsm/ticket/dto/TicketAssignRequest.java
test -f src/test/java/com/nkia/itg/fixture/TicketFixture.java

# 2) 도메인 메서드 추가 (assignTicketNo)
grep -q "assignTicketNo" src/main/java/com/nkia/itg/itsm/ticket/entity/Ticket.java

# 3) 단위 테스트
./gradlew test --tests "com.nkia.itg.itsm.ticket.service.*"
./gradlew test            # 회귀 — 기존 모듈 영향 없음
./gradlew build
```

## 검증 절차

1. AC 모두 통과.
2. 아키텍처 체크리스트:
   - Service 가 `@Transactional` 클래스 레벨 + 조회 메서드 `readOnly` 인가?
   - 상태 전이·CLOSED 가드 로직이 Service 에 분기로 흩어져 있지 않고 Entity 의 도메인 메서드에 집중되었는가?
   - DTO 변환이 `from()` 정적 메서드를 통하는가?
   - `ticket_no` 가 한 트랜잭션 안에서 save 후 dirty checking 으로 정확히 부여되는가?
   - Repository 의 `search` 가 null 인자에 대해 조건을 무시(`is null or x = ...`)하여 옵션 필터로 동작하는가?
   - 단위 테스트 14 케이스 통과?
3. step 1 업데이트:
   - 성공 → `"summary": "TicketRepository (findByTicketNo + search Page) + TicketService (create/getById/getByTicketNo/search/update/changeStatus/changePriority/assign) + DTO Record 6종(Request 5 + Response/Summary) + Ticket#assignTicketNo 추가 + TicketFixture + Mockito 단위 테스트 14 케이스. ticket_no 는 save 후 ITSM-{id5} 부여 + dirty checking. 모든 상태 가드는 Entity 도메인 메서드 위임."`

## 금지사항

- Service 안에 상태 전이 매트릭스 if 를 재구현하지 마라. 이유: Entity 도메인 메서드가 정답. 중복은 일관성 결함의 원천.
- DTO 의 `priority`·`status` 를 `String` 으로 받지 마라. Enum 강제. CLAUDE.md 절대 규칙.
- Repository 의 `search` 를 Specification·Querydsl 로 재구현하지 마라. 이유: 본 phase 는 `@Query` JPQL 로 충분. QueryDSL 도입은 별도 ADR.
- Controller·시드 SQL·메타 INSERT 를 이 step 에서 만들지 마라. 이유: step 2·3 의 책임.
- 프런트엔드 수정 금지.
- 테스트 데이터에 실 운영 사번·이메일·서버명 금지. 가상 샘플(`assignee-sample-1` 등).
- `ticket_no` 생성 시 외부 시퀀스 테이블·UUID 사용 금지. id 기반 `ITSM-{id5}` 패턴만.
- `EntityManager` 직접 주입한 native query 사용 금지 (운영 코드 한정 — 테스트 시 트리거 회피 등의 한정 시나리오는 예외).
