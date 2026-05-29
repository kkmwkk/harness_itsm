# M8 도메인 깊이 e2e 보고서 — 워크플로우 + 자산 분류

> Phase 12 (domain-depth-workflow-category) Step 6 — `e2e-workflow-and-category`
> 작성일: 2026-05-29 · 대상: PRD §9 M8 (요청 유형·자산 분류·워크플로우 MVP)

본 보고서는 PRD §9 M8·ARCHITECTURE §15(워크플로우 엔진 MVP)·ADR-006(메타 버전 라우팅)·ADR-015(자체 단계 엔진)의
핵심 약속을, Step 0~5 의 산출물(스키마·엔진·컨트롤러·시드·프런트) 위에서 **실 백엔드 기동 + cURL e2e** 로
end-to-end 검증한 결과를 정리한다. 두 도메인 흐름 — **(1) 요청 유형별 워크플로우 자동 진행 + 역할 라우팅,
(2) 자산 분류별 원장 메타 분기 + 라이프사이클 이벤트** — 가 시드 위에서 동작함을 입증한다.

---

## 1. 환경

| 항목 | 값 |
|------|-----|
| 작업 디렉토리 | `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM` |
| 브랜치 | `feat-12-domain-depth-workflow-category` |
| Backend | Spring Boot 3 · Java 21 · `local` 프로파일 (`./gradlew bootRun`) |
| DB | PostgreSQL 16 · Docker 컨테이너 `itg-postgres` (`localhost:5432`, DB `itgdb`) |
| 헬스체크 | `GET /actuator/health` → `{"status":"UP"}` |
| 인증 | JWT (Bearer) — `/api/auth/login` 실 발급, 시드 사용자 3종 |
| 시드 사용자 | `admin`(ROLE_ADMIN) · `it-support`(ROLE_IT_SUPPORT) · `user-sample-1`(ROLE_USER) — 모두 ACTIVE |
| OpenAPI | `backend/openapi/itg-api-spec.json` (`GET /v3/api-docs`, 57 paths) 갱신 |

> 인증 테이블은 `user_account` 다(예약어 회피). 시드 적용은 `11/12/15/16/17/18_*.sql` 멱등 재적용으로 확인.

---

## 2. 시드 (Step 3 산출물 — 멱등 재적용 후 실측)

| 시드 | 건수 | 비고 |
|------|------|------|
| 요청 유형 (`ticket_request_type`) | **5** | INCIDENT · SERVICE_REQUEST · CHANGE · PROBLEM · QNA |
| 워크플로우 정의 (`workflow_definition`) | **5** | 요청 유형별 `steps` JSONB (WF_INCIDENT_STD 등) |
| 요청 유형별 폼 메타 (`itg-ticket-*`, PUBLISHED) | **5** | incident/srvreq/change/problem/qna |
| 자산 분류 트리 (`asset_category`, active) | **8** | HW · HW_LAPTOP · HW_SERVER · SW · SW_LICENSE · CONTRACT · … |
| 분류별 폼 메타 (`itg-asset-*`, PUBLISHED) | **5** | hw-laptop/hw-server/sw-license/… |

요청 유형 ↔ 폼 메타 매핑(`ticket_request_type.form_meta_group_id`):
`INCIDENT→itg-ticket-incident`, `CHANGE→itg-ticket-change`, `PROBLEM→itg-ticket-problem`,
`QNA→itg-ticket-qna`, `SERVICE_REQUEST→itg-ticket-srvreq`.

---

## 3. 시나리오 15 단계 결과 — **전체 PASS ✅**

cURL 자동 스크립트(`/tmp/e2e_phase12.sh`) 실행. `admin`/`it-support`/`user-sample-1` 토큰 사용.

| # | 시나리오 | 검증 | 결과 |
|---|----------|------|------|
| 1 | USER 가 INCIDENT 티켓 생성 | `POST /api/tickets` → `data.id` (TID=20) | ✅ |
| 2 | 워크플로우 인스턴스 자동 생성 | `currentStepIndex=0` · `status=RUNNING` · `steps[0].stepName='접수'` | ✅ |
| 3 | USER 가 step0(ROLE_IT_SUPPORT) 액션 불가 | `POST .../step/0/action` → **400** | ✅ |
| 4 | IT_SUPPORT step0 COMPLETE → step1 | `currentStepIndex=1` | ✅ |
| 5 | IT_SUPPORT step1 COMPLETE → step2 | `currentStepIndex=2` | ✅ |
| 6 | IT_SUPPORT step2 COMPLETE → step3 | `currentStepIndex=3` (종결 확인 = ROLE_USER) | ✅ |
| 7a | USER(요청자) step3 CONFIRM → 인스턴스 COMPLETED | `status=COMPLETED` | ✅ |
| 7b | Ticket 자동 CLOSED | `GET /api/tickets/{TID}` → `status=CLOSED` | ✅ |
| 8 | CLOSED 후 액션 거부 | `POST .../step/3/action` → **400** | ✅ |
| 9 | 변경 요청 폼 메타 active | `itg-ticket-change-v1-1` 노출 | ✅ |
| 10 | 요청 유형별 폼 분기 | incident 폼 필드(`priority`/`affectedSystem`) 존재 | ✅ |
| 11 | 자산 분류 트리 | `HW`·`HW_LAPTOP`·`SW_LICENSE` 모두 포함 | ✅ |
| 12 | HW_LAPTOP 분류 자산 등록 | `POST /api/assets` (pageGroupId=itg-asset-hw-laptop) → AID=8 | ✅ |
| 13 | 등록 메타 보존 (ADR-006) | `GET .../registration-meta` → `itg-asset-hw-laptop-v1-1` | ✅ |
| 14 | 라이프사이클 이벤트 기록 | `POST .../lifecycle-events` (TRANSFERRED) | ✅ |
| 15 | 이벤트 조회 | `GET .../lifecycle-events` → `TRANSFERRED` 포함 | ✅ |

> **시나리오 예시의 필드명 보정**: 2번 검증은 step 정의의 예시가 `steps[0]['name']` 을 참조했으나,
> 실제 `WorkflowInstanceStep` API 응답 필드는 `stepName` 이다(snake `step_name` → camel `stepName`).
> 단계명 "접수" 자체는 정상 노출되며, 신규 INCIDENT 티켓(TID=21)으로 `stepName='접수'`·`currentStepIndex=0`·
> `status=RUNNING` 을 재검증해 PASS 확인했다. 운영 코드 결함이 아닌 예시 스크립트의 필드명 오타이므로
> 운영 코드는 수정하지 않았다(금지사항 준수).

워크플로우 인스턴스(WID=1, ticket 20)의 4단계 이력은 모두 `action`·`actionByUserId`·`actionComment` 가
기록됐다: step0~2 = IT_SUPPORT(uid=2) COMPLETE, step3 = USER(uid=3) CONFIRM. `sla_due_at` 도 단계별 산정됨
(step0 +60분, step1 +120분, step2 +480분, step3 null).

---

## 4. 핵심 검증 사실

- **워크플로우 엔진 MVP 동작 (ADR-015)**: INCIDENT 요청이 생성되는 즉시 `default_workflow_id(WF_INCIDENT_STD)`
  로 `WorkflowInstance` 가 자동 생성되고 `current_step_index=0`(접수)으로 진입했다. 4단계(접수→원인 분석→해결→종결 확인)를
  COMPLETE/CONFIRM 액션으로 자동 진행해 마지막 단계 종료 시 인스턴스 `COMPLETED` + Ticket `CLOSED` 로 원자 전이했다.
- **단계별 역할 라우팅 + 권한 분기**: step0~2 는 `ROLE_IT_SUPPORT`, step3 는 `ROLE_USER`(요청자) 만 액션 가능.
  ROLE_USER 가 ROLE_IT_SUPPORT 단계(step0)를 시도하면 400 으로 거부됐다 — `@PreAuthorize` + 도메인 가드 이중 방어.
- **CLOSED 가드 (v2.0 패턴 재사용)**: 인스턴스 COMPLETED 이후 모든 액션이 400 으로 거부됐다.
- **요청 유형별 폼 분기 (도메인 깊이)**: `itg-ticket-incident` 와 `itg-ticket-change` 가 서로 다른 PUBLISHED
  폼 메타로 라우팅됐다 — 같은 `/api/tickets` 위에서 요청 유형에 따라 화면 정의가 갈린다.
- **자산 분류별 원장 + 메타 보존 (ADR-006)**: HW_LAPTOP 자산이 `itg-asset-hw-laptop` 메타로 등록되고,
  등록 시점 메타 버전(`itg-asset-hw-laptop-v1-1`)이 자산에 보존돼 양식 변경이 과거 데이터 표시에 영향을 주지 않는다.
- **라이프사이클 이벤트 추적**: TRANSFERRED 이벤트가 payload(JSONB)와 함께 기록·조회됐다.

---

## 5. 한계

- **SLA 초과 알림 stub**: `sla_due_at` 산정·`findOverdue` 조회는 동작하나, 초과 시 이메일·웹훅 알림은 미구현
  (PRD §5-3 — 알림 stub). 실 알림 채널은 별도 ADR 후보.
- **BPMN 모델러 없음**: 자체 선형 단계 엔진(MVP)만 검증. 병렬 분기·서브프로세스·이벤트 트리거는 미지원이며
  BPMN/Camunda 도입은 ADR-017 의 Stretch(phase 15) 책임 — 본 phase 범위 외.
- **자산 분류별 동적 컬럼은 메타 기반까지**: 분류별 폼·원장은 PageMeta 분기로 검증했으나, 분류 트리 기반의
  깊은 검색·집계·통계는 별도 phase 의 책임이다.
- **요청 유형 폼 필드 다양성**: incident/change 가 서로 다른 메타임을 확인했으나, 5종 전 유형의 필드 구성
  세부 비교는 본 e2e 의 범위가 아니다(시드 기반 분기 동작 확인까지).

---

## 6. 산출물

| 산출물 | 비고 |
|--------|------|
| `backend/E2E_REPORT_PHASE12.md` | **본 보고서** |
| `backend/openapi/itg-api-spec.json` | `GET /v3/api-docs` 최신 갱신 (57 paths) |
| (시드 보존) `ticket_request_type`·`workflow_definition`·`page_meta`·`asset_category` | 다음 phase 사용 — 정리하지 않음 |
| (e2e 생성 데이터 보존) ticket TID=20(CLOSED)·21(RUNNING) · asset AID=8(+TRANSFERRED 이벤트) | 다음 phase 사용 — 보존 |

---

## 7. 결론

- **PRD §9 M8 마일스톤 이행**: `INCIDENT 생성 → 워크플로우 자동 시작 → 단계별 역할 액션 → 종결/CLOSED → 가드`
  와 `자산 분류 트리 → 분류별 메타 등록 → 메타 보존 → 라이프사이클 이벤트` 의 두 도메인 흐름이 실 백엔드 위에서
  end-to-end 로 동작함을 15/15 PASS 로 검증했다.
- **No-code 메타 모델 + 도메인 깊이 결합 입증**: 요청 유형·자산 분류가 PageMeta 버전 그룹(ADR-006) 위에서
  화면을 분기하고, 그 화면이 자체 워크플로우 엔진(ADR-015)·라이프사이클 이벤트 도메인 위에서 동작한다.
- 후속(SLA 실 알림·BPMN·분류 통계)은 §5 의 별도 작업·별도 phase 항목이다.
