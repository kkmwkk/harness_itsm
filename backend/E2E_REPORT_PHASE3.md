# Phase 3 E2E 검증 보고서 — ITSM 티켓 모듈 (PRD §9 M3)

> 본 보고서는 `itg-ticket` PUBLISHED 메타 + 샘플 티켓 시드 위에서 ITSM 티켓 백엔드의
> end-to-end 동작(목록·필터·생성·상태 전이·CLOSED 가드)을 cURL 시나리오로 검증한 결과다.
> 운영 코드(Entity·Service·Controller·Config) 변경은 없다.

## 환경
- 검증 일시: 2026-05-29 (KST)
- Docker 이미지: `postgres:16` (`itg-postgres`, healthy), `dpage/pgadmin4:latest`
- Spring Boot: 3.5.0 / Java 21 (Gradle toolchain `languageVersion = 21`)
- profile: `local` (`SPRING_PROFILES_ACTIVE=local`)
- DB 접속: `localhost:5432` / `itgdb` / `itg`
- OpenAPI: SpringDoc 2.8.6, OAS 3.1.0
- 보안: phase-3 한정 `/api/meta/**`·`/api/tickets/**` permitAll (JWT 도입 전)

## 검증 메타 — `itg-ticket-v1-1` (PUBLISHED)
| 항목 | 값 |
|------|-----|
| id | `itg-ticket-v1-1` |
| title | ITSM 티켓 관리 |
| systemType / packageType | `ITSM` / `PACKAGE` |
| groupId · major · minor | `itg-ticket` · 1 · 1 |
| metaStatus | `PUBLISHED` |
| api | `/api/tickets` (TicketController 베이스 경로와 일치) |
| grid.columns (6) | `ticketNo` · `title` · `status` · `priority` · `assigneeId` · `createdAt` — **`TicketSummary` 필드명과 1:1 일치** |
| form.fields (6) | `title`(text·span2) · `category`(select) · `priority`(radio) · `assigneeId`(user-picker) · `content`(textarea·span2) |
| actions | `create` (dialog-form) |

> grid 컬럼 `field` 명이 `TicketSummary` Record(`id·ticketNo·title·priority·status·assigneeId·createdAt`)의
> 필드명과 정확히 일치 — 다음 phase 의 `DynamicGrid` 가 `accessorKey` 로 사용한다.

## 시드 티켓 5건 (`ITSM-SAMPLE-001~005`, 시드 유지)
| ticket_no | 제목 | priority | status | category | assignee |
|-----------|------|----------|--------|----------|----------|
| ITSM-SAMPLE-001 | 샘플 티켓 1 — 로그인 오류 | HIGH | OPEN | BUG | assignee-sample-1 |
| ITSM-SAMPLE-002 | 샘플 티켓 2 — 기능 요청 | MEDIUM | IN_PROGRESS | REQ | assignee-sample-2 |
| ITSM-SAMPLE-003 | 샘플 티켓 3 — 문의 | LOW | RESOLVED | QNA | assignee-sample-1 |
| ITSM-SAMPLE-004 | 샘플 티켓 4 — 장애 보고 | CRITICAL | OPEN | BUG | (null) |
| ITSM-SAMPLE-005 | 샘플 티켓 5 — 종료된 항목 | MEDIUM | CLOSED | REQ | assignee-sample-3 |

> 상태 혼합(OPEN·IN_PROGRESS·RESOLVED·CLOSED) + 우선순위 4단계 전부 포함. `ITSM-SAMPLE-005` 는 직접 INSERT 이므로 `closed_at` 을 `UPDATE` 로 보정.
> 모든 값은 가상 샘플(ADR-011) — 실 운영 데이터 없음.

## 시나리오 결과 (14 단계)
| # | 단계 | HTTP | 결과 | 비고 |
|---|------|------|------|------|
| 0 | `docker-compose up -d` | — | PASS | `itg-postgres` Up (healthy) |
| 1 | 스키마/시드 적용 (`02`·`03`·`04`) | — | PASS | 멱등(`ON CONFLICT DO NOTHING`) — 재적용 시 `INSERT 0 0` |
| 2 | `bootRun` + `GET /actuator/health` | 200 | PASS | `{"status":"UP"}` (Started in 2.48s) |
| 3 | `GET /api/meta/active/itg-ticket` | 200 | PASS | `id=itg-ticket-v1-1` (PUBLISHED 라우팅) |
| 4 | `GET /api/tickets?page=0&size=20` | 200 | PASS | `totalElements >= 5` |
| 5 | `GET /api/tickets?status=OPEN` | 200 | PASS | content 의 status 집합 = `{OPEN}` (필터 동작) |
| 6 | `POST /api/tickets` 신규 생성 | 201 | PASS | `data.id=18` 반환 |
| 7 | `GET /api/tickets/{id}` | 200 | PASS | `ticketNo` ↔ `^ITSM-\d{5}$` 일치, `status=OPEN` |
| 8 | `PATCH .../{id}/status` OPEN→IN_PROGRESS | 200 | PASS | `status=IN_PROGRESS` |
| 9 | `PATCH .../{id}/status` IN_PROGRESS→OPEN | 400 | PASS | 역방향 거부, `errorCode=INVALID_REQUEST` |
| 10 | `PATCH .../{id}/status` IN_PROGRESS→CLOSED | 200 | PASS | `status=CLOSED`, `closedAt != null` |
| 11 | `PATCH .../{id}` (CLOSED 후 본문 수정) | 400 | PASS | CLOSED 가드, `errorCode=INVALID_REQUEST` |
| 12 | `PATCH .../{id}/status` (CLOSED→IN_PROGRESS) | 400 | PASS | CLOSED 가드 |
| 12b | `PATCH .../{id}/priority` (CLOSED) | 400 | PASS | CLOSED 가드 (보강) |
| 12c | `PATCH .../{id}/assign` (CLOSED) | 400 | PASS | CLOSED 가드 (보강) |
| 13 | OpenAPI 산출 (`/v3/api-docs(.yaml)`) | 200 | PASS | json 22,700B · yaml 29,187B, ticket 경로 6개 |
| 14 | `bootRun` 종료 (시드 유지) | — | PASS | 시드 DELETE 안 함 (다음 phase 사용) |

## 핵심 검증 사실

### PRD §9 M3 — ITSM 티켓 모듈 백엔드 e2e 완료
시드 메타 1건 + 시드 티켓 5건 위에서 목록·필터·단건 조회·생성·상태 전이·가드가
실제 HTTP 경로(Controller → Service → Repository → PostgreSQL)로 일관 동작함을 확인.

### 상태 전이 매트릭스
- **허용**: `OPEN → IN_PROGRESS`(8) · `IN_PROGRESS → CLOSED`(10) — 정상 전이 + CLOSED 시 `closedAt` 자동 set.
- **거부(400)**: `IN_PROGRESS → OPEN`(9, 역방향) · `CLOSED → IN_PROGRESS`(12, 종료 후 재개).
- 모든 거부는 `IllegalStateException` → `GlobalExceptionHandler` → `400 / errorCode=INVALID_REQUEST` 로 매핑.

### CLOSED 가드 (4개 변경 엔드포인트 모두 400)
| 엔드포인트 | CLOSED 티켓 결과 |
|------------|------------------|
| `PATCH /{id}` (본문 수정) | 400 |
| `PATCH /{id}/status` (상태 전이) | 400 |
| `PATCH /{id}/priority` (우선순위) | 400 |
| `PATCH /{id}/assign` (담당자) | 400 |

CLOSED 티켓은 어떤 변경도 받지 않음 — Entity 도메인 메서드의 가드(step 0)가 전 경로에서 일관 적용됨을 확인.

### `ticket_no` 자동 부여
신규 생성 티켓의 `ticketNo` 가 `^ITSM-\d{5}$` (예: `ITSM-00018`) 패턴으로 자동 부여됨 확인 —
`Service.create` 의 save 후 `ITSM-{id5}` 부여 로직(step 1) 정상 동작. 시드의 `ITSM-SAMPLE-NNN` 과 구분됨.

### 메타 `itg-ticket` PUBLISHED 확인
`GET /api/meta/active/itg-ticket` 가 버전 라우팅을 거쳐 `itg-ticket-v1-1`(PUBLISHED) 단건을 반환.
다음 phase 의 `<DynamicPage group-id="itg-ticket" />` 가 `/itsm` 진입 시 이 메타로 폼·그리드를 자동 렌더한다.

## 한계 (다음 phase 범위)
- 프런트엔드 통합(`DynamicPage`/`DynamicForm`/`DynamicGrid`) 및 실제 폼 submit 동작.
- 담당자(`user-picker`) 검색 UI · 옵션 소스.
- 그리드 렌더러 분기(shadcn DataTable ↔ AG Grid) 실측.

## 산출물
- SQL 시드 3종: `sql/init/02_ticket.sql`(step 0) · `sql/init/03_itg_ticket_meta.sql`(신규) · `sql/init/04_sample_tickets.sql`(신규)
- OpenAPI: `backend/openapi/itg-api-spec.json` (22,700B) · `backend/openapi/itg-api-spec.yaml` (29,187B) — ticket 경로 6개 등록
- 본 보고서: `backend/E2E_REPORT_PHASE3.md`

> **시드 유지**: `itg-ticket-v1-1` 메타와 `ITSM-SAMPLE-001~005` 티켓, E2E 생성 티켓(`E2E 생성 샘플`)은
> DELETE 하지 않는다 — 다음 phase(frontend 통합)가 사용한다.
