# Step 2: ticket-controller-and-swagger

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "절대 규칙"(Controller 비즈니스 로직 금지, Swagger 어노테이션 필수, `@Schema(example)` 민감정보 금지, `ApiResponse<T>` 래퍼)
- `/docs/ADR.md` — ADR-009 (`ApiResponse<T>`), ADR-011 (Swagger 민감정보 금지)
- `/phases/3-itsm-ticket-backend/step0.md`·`step1.md` — Entity·Service 시그니처
- `/backend/src/main/java/com/nkia/itg/meta/controller/MetaController.java` — Controller 작성 스타일 (어노테이션 풀패스 회피 패턴 등)

## 작업

이 step 의 목적은 **`TicketController` 를 만들고 Swagger 어노테이션·가상 샘플 example 을 완비하며, `@WebMvcTest` 로 단위 검증하는 것**이다.

### 1. `TicketController` — `com.nkia.itg.itsm.ticket.controller`

```java
@Tag(name = "ITSM Ticket — 티켓 관리")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController { /* ... */ }
```

#### 엔드포인트 일람

| 메서드 | 경로 | Service | 응답 |
|---|---|---|---|
| POST   | `/api/tickets` | `create(req)` | 201 + `ApiResponse<TicketResponse>` |
| GET    | `/api/tickets` | `search(status, priority, assigneeId, page, size)` | 200 + `ApiResponse<Page<TicketSummary>>` 또는 평탄화된 `PageResponse<TicketSummary>` |
| GET    | `/api/tickets/{id}` | `getById(id)` | 200 + `ApiResponse<TicketResponse>` |
| GET    | `/api/tickets/by-no/{ticketNo}` | `getByTicketNo(ticketNo)` | 200 + `ApiResponse<TicketResponse>` |
| PATCH  | `/api/tickets/{id}` | `update(id, req)` | 200 + `ApiResponse<TicketResponse>` |
| PATCH  | `/api/tickets/{id}/status` | `changeStatus(id, req)` | 200 + `ApiResponse<TicketResponse>` (메시지 "상태가 변경되었습니다.") |
| PATCH  | `/api/tickets/{id}/priority` | `changePriority(id, req)` | 200 + `ApiResponse<TicketResponse>` |
| PATCH  | `/api/tickets/{id}/assign` | `assign(id, req)` | 200 + `ApiResponse<TicketResponse>` |

> **DELETE 는 만들지 않는다**. 이유: ITSM 티켓은 보통 archive·close 로 다루지 물리 삭제 안 함. 본 phase 의 범위 아님.

#### 페이지 응답 형태 — `PageResponse<T>` (공통)

`Page<T>` 의 메타 정보(`totalElements`·`totalPages`·`number`·`size`)를 단순 record 로 평탄화. **이 step 에서 추가**:

`src/main/java/com/nkia/itg/common/response/PageResponse.java`:

```java
package com.nkia.itg.common.response;

import org.springframework.data.domain.Page;
import java.util.List;

public record PageResponse<T>(
    List<T> content,
    long    totalElements,
    int     totalPages,
    int     number,
    int     size
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );
    }
}
```

> `Page<T>` 를 직접 노출하면 Spring Data 의 deprecated 노출 경고가 떠서 모든 호출자가 그 형태에 묶인다. 평탄화하여 API 컨트랙트를 우리 소유로.

#### Swagger 어노테이션 (각 메서드 필수)

- `@Operation(summary, description)` — 한 줄 요약 + 비즈니스 의미.
- 경로 변수·쿼리 파라미터에 `@Parameter(description, example)`.
- 가능한 응답 코드에 `@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode, description)`.
- `example` 값은 가상 샘플만 — `id`: `42`, `ticketNo`: `"ITSM-00042"`, `assigneeId`: `"assignee-sample-1"`, `priority`: `"MEDIUM"`, `status`: `"OPEN"`.

예시 한 건:

```java
@Operation(
    summary = "티켓 상태 전이",
    description = "허용 전이: OPEN→IN_PROGRESS/RESOLVED/CLOSED, IN_PROGRESS→RESOLVED/CLOSED, " +
                  "RESOLVED→CLOSED/IN_PROGRESS(재오픈), CLOSED→불가."
)
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "전이 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "허용되지 않은 전이"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "티켓 없음")
})
@PatchMapping("/{id}/status")
public ResponseEntity<ApiResponse<TicketResponse>> changeStatus(
    @Parameter(description = "티켓 PK", example = "42") @PathVariable Long id,
    @Valid @RequestBody TicketStatusChangeRequest req
) {
    return ResponseEntity.ok(
        ApiResponse.ok(ticketService.changeStatus(id, req), "상태가 변경되었습니다.")
    );
}
```

#### 검색 쿼리 파라미터

```java
@GetMapping
public ResponseEntity<ApiResponse<PageResponse<TicketSummary>>> search(
    @Parameter(description = "상태", example = "OPEN")
    @RequestParam(required = false) TicketStatus status,
    @Parameter(description = "우선순위", example = "HIGH")
    @RequestParam(required = false) Priority priority,
    @Parameter(description = "담당자 ID", example = "assignee-sample-1")
    @RequestParam(required = false) String assigneeId,
    @Parameter(description = "페이지 번호 (0부터)", example = "0")
    @RequestParam(defaultValue = "0")  int page,
    @Parameter(description = "페이지 크기", example = "20")
    @RequestParam(defaultValue = "20") int size
) {
    Page<TicketSummary> result = ticketService.search(status, priority, assigneeId, page, size);
    return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
}
```

### 2. 검증·예외 처리

- `@Valid` + `@RequestBody`. 검증 실패는 `MethodArgumentNotValidException` → `GlobalExceptionHandler` 가 400 `VALIDATION_FAILED`.
- 도메인 예외(`IllegalStateException` — CLOSED 가드·허용되지 않은 전이) 는 `GlobalExceptionHandler` 가 400 `INVALID_REQUEST` 로 변환.
- `ITGException("TICKET_NOT_FOUND", "...", HttpStatus.NOT_FOUND)` → 404.

> `GlobalExceptionHandler` 는 M1 phase 에서 이미 구현됨. 본 step 은 그대로 활용.

### 3. `@WebMvcTest` — `com.nkia.itg.itsm.ticket.controller.TicketControllerTest`

`@WebMvcTest(TicketController.class)` + `@MockBean TicketService`. 케이스:

1. `POST_create_201_과_TicketResponse_반환`.
2. `POST_create_title_누락_400_VALIDATION_FAILED`.
3. `GET_by_id_200_정상`.
4. `GET_by_id_없으면_404_TICKET_NOT_FOUND` — Service Mock 이 ITGException 던지게.
5. `GET_search_status_priority_assignee_파라미터_Service_에_전달` — argument captor.
6. `GET_search_기본_page_0_size_20`.
7. `PATCH_status_200_과_메시지_상태가_변경되었습니다`.
8. `PATCH_status_허용되지_않은_전이_400_INVALID_REQUEST` — Service mock 이 IllegalStateException 던지게.
9. `PATCH_priority_CLOSED_400`.
10. `PATCH_assign_assignee_null_허용` (해제).
11. `PATCH_update_부분_업데이트_200`.
12. `GET_by_no_200`.

`@WebMvcTest` 슬라이스가 `GlobalExceptionHandler`·`SecurityConfig` 를 포함해야 한다 (이전 phase 의 MetaControllerTest 와 동일 패턴 — `@Import(...)` 명시 필요 시).

### 4. Swagger 노출 확인 (cURL)

bootRun 후:
- `GET /v3/api-docs` JSON 에 8 개 ticket 경로 모두 포함.
- `paths` 에 `/api/tickets`, `/api/tickets/{id}`, `/api/tickets/{id}/status` 등 등록.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend

# 1) 파일 존재
test -f src/main/java/com/nkia/itg/itsm/ticket/controller/TicketController.java
test -f src/main/java/com/nkia/itg/common/response/PageResponse.java

# 2) 단위 테스트
./gradlew test --tests "com.nkia.itg.itsm.ticket.controller.*"
./gradlew test            # 회귀
./gradlew build

# 3) bootRun + Swagger 경로 확인
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
# ticket 스키마가 컨테이너에 적용되어 있어야 함 (step 0 에서 적용)
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'
curl -fsS http://localhost:8080/v3/api-docs > /tmp/spec.json
python3 -c "import json; d=json.load(open('/tmp/spec.json')); \
            need={'/api/tickets','/api/tickets/{id}','/api/tickets/by-no/{ticketNo}', \
                  '/api/tickets/{id}/status','/api/tickets/{id}/priority','/api/tickets/{id}/assign'}; \
            missing=need - set(d['paths'].keys()); \
            assert not missing, f'missing {missing}'"
kill %1
```

## 검증 절차

1. AC 모두 통과.
2. 아키텍처 체크리스트:
   - Controller 가 비즈니스 로직 없이 Service 호출만 하는가?
   - Swagger `@Schema(example)` 의 모든 값이 가상 샘플인가?
   - `Page<T>` 가 응답에 직접 노출되지 않고 `PageResponse<T>` 로 평탄화되었는가?
   - 모든 응답이 `ApiResponse<T>` 래퍼인가?
   - `POST /api/tickets` 가 201, 상태 전이는 PATCH 인가?
   - 단위 테스트 12 케이스 통과?
   - `/v3/api-docs` 에 ticket 경로 6개(또는 8개) 모두 등록?
3. step 2 업데이트:
   - 성공 → `"summary": "TicketController 8 엔드포인트 (POST create / GET search·byId·byTicketNo / PATCH update·status·priority·assign) + PageResponse<T> 공통 응답 + @Operation·@Parameter·@ApiResponses 어노테이션 + 가상 샘플 example + @WebMvcTest 12 케이스. /v3/api-docs 에 ticket 경로 등록 확인."`

## 금지사항

- Controller 안에 비즈니스 분기 금지. 이유: 레이어 분리.
- DELETE `/api/tickets/{id}` 엔드포인트 추가 금지. 이유: ITSM 티켓은 보통 close 처리 — 본 phase 범위 아님.
- `Page<T>` 를 응답에 직접 노출 금지. `PageResponse.from(...)` 평탄화.
- `@Schema(example)` 에 실 운영 데이터 금지. 가상 샘플(`assignee-sample-1`, `샘플 티켓 제목`).
- 인증 강제를 위해 `SecurityConfig` 수정 금지. 이유: `/api/tickets/**` 도 phase 동안 permitAll. 인증은 별도 phase.
- 사용자 검색·자동완성 엔드포인트 추가 금지. 이유: 사용자 모듈은 다음 phase.
- 프런트엔드 수정 금지. 메타 시드 추가 금지 (step 3).
- 응답 형태를 새로 정의하지 마라 — `ApiResponse<T>` + `PageResponse<T>` 외 추가 래퍼 금지.
- 도메인 예외를 잡아 자체 에러 응답을 만들지 마라. `GlobalExceptionHandler` 위임.
