# PRD — Polestar10 ITG v2 (POLESTAR-ITSM) — v2.1 개정판

> Product Requirements Document · Owner: ITG Dev Team (NKIA) · Version: v2.1-draft · Last revised: 2026-05-29
>
> **v2.1 개정 배경**: 1차 빌드(M1~M6) 의 결과물이 PoC 데모 수준에 머문 점을 인정하고,
> 도메인 깊이 + 공통 인프라 + No-code WYSIWYG 편집기 + UX 베이스라인을 정식 범위로
> 끌어올린다. v2.0 의 메타·DynamicPage 골격은 유지·확장한다.

---

## 1. 문제·비전

### 1-1. 기존 ITG (Polestar10 v1) 의 한계
- Antd `Form` 의 `shouldUpdate`·동기 `rules` → 메타 기반 동적 폼 한계.
- React `useEffect` 의존성·`useMemo` 부담 → 신규 인력 진입 장벽.
- Kotlin·MongoDB 의 학습 곡선 분산·관계형 정합성 부족.
- **Low-code 수준** — 신규 화면 추가 시 일부 자동화는 있지만 결국 개발자가 코드를 짠다.

### 1-2. v2.0 (M1~M6) 의 결과와 자기 진단

| 영역 | v2.0 결과 |
|---|---|
| 메타 모델 | ✅ `page_meta` + 버전 그룹 + 자동 DEPRECATE 트리거 정착 |
| 동적 렌더링 | ✅ DynamicPage/Form/Grid + shadcn/AG Grid 자동 분기 |
| ITSM 티켓 | ⚠️ 단순 CRUD 그리드. **요청 유형·워크플로우·역할 라우팅 부재** |
| ITAM 자산 | ⚠️ 단일 원장. **자산 분류별 메타 분기 부재** |
| AI 메타 생성 | ✅ Python CLI + dry-run 검증 |
| 인증·사용자·부서·역할·권한·메뉴 | ❌ **전부 미구현**. `/api/**` permitAll, `assigneeId` 단순 문자열 |
| No-code GUI 편집기 | ❌ **미구현**. AI 가 JSON 메타 생성하는 도구만 존재 |
| UX 다듬기 | ❌ Dialog 시각 결함·raw 백엔드 에러 노출·placeholder 텍스트 잔존 |

### 1-3. v2.1 비전

> **"비개발자가 WYSIWYG 로 화면을 만들고, 그 화면이 도메인 모델(요청 유형·자산 분류·워크플로우·역할) 위에서 동작한다."**

세 가지 약속:

1. **No-code WYSIWYG 편집기** — 비개발자가 GUI 로 폼·그리드·워크플로우를 시각 편집한다. DRAFT → 검토 → PUBLISHED 흐름은 v2.0 의 메타 버전 그룹 위에서 동작.
2. **도메인 깊이** — ITSM 요청은 유형에 따라 다른 폼·다른 워크플로우·단계별 역할 라우팅이 일어난다. ITAM 자산은 분류별로 원장 폼이 다르다.
3. **공통 인프라 완비** — 사용자·부서·역할·권한·메뉴가 DB 모델로 정착. 로그인 후 본인 권한에 맞는 메뉴 트리만 보이고, 본인 역할에 할당된 워크플로우 단계만 처리할 수 있다.

### 1-4. v2.0 ↔ v2.1 변화 한눈에

| 영역 | v2.0 (M1~M6) | v2.1 (M7~M11) |
|---|---|---|
| 화면 정의 | AI/사람이 JSON 메타 작성 | **WYSIWYG 편집기** + 폼 UI |
| ITSM 티켓 | 단순 CRUD | 요청 유형별 폼 + **워크플로우 단계** + **역할 라우팅** |
| ITAM 자산 | 단일 원장 | **자산 분류 트리** + 분류별 원장 메타 |
| 인증·사용자 | permitAll | **JWT** + 사용자/부서/역할/권한/메뉴 |
| 메뉴 | router 하드코딩 | **DB 기반 동적 메뉴 트리** + 권한 필터 |
| UX | placeholder · raw 에러 | **친화 메시지 카탈로그** + 시각 회귀 검증(Playwright) |

---

## 2. 사용자·역할별 시나리오

### 2-1. 시스템 관리자 (admin)
- 권한: 모든 메타·사용자·부서·역할·권한·메뉴·워크플로우 정의 관리.
- 대표 시나리오:
  > "ITSM 의 '변경' 요청 폼에 [영향 시스템] 필드를 추가하고 v1.3 로 발행" → WYSIWYG 편집 → DRAFT → 검토 → PUBLISHED → v1.2 자동 DEPRECATE.

### 2-2. 부서 관리자
- 권한: 자기 부서 범위의 사용자/역할 할당 + 부서별 워크플로우 단계 승인.
- 대표 시나리오:
  > "신규 입사자 + 'IT 지원' 역할 할당 + 변경 요청의 1차 승인자로 지정."

### 2-3. 일반 사용자 (요청자)
- 권한: 본인이 만든 요청 추적, 본인이 사용하는 자산 조회·요청.
- 대표 시나리오:
  > "노트북 고장 신고 → '장애' 요청 유형 폼 표시 → 본문 작성 → 자동으로 'IT 지원' 역할 사용자 풀에 할당."

### 2-4. 컨설턴트 (현장)
- 권한: 고객사 환경에서 PACKAGE 메타를 복사 → CUSTOM 으로 변형 → 발행.
- 대표 시나리오:
  > "기본 자산원장 메타 복사 → 고객사 전용 컬럼([부서 코드]) 추가 → publish. 본사 PACKAGE 는 무손상 유지."

### 2-5. 비개발 사용자 (v2.1 의 핵심 사용자)
- 권한: WYSIWYG 편집기로 직접 화면 만들기. JSON 직접 편집 없이.
- 대표 시나리오:
  > "ITSM 에 '제안' 요청 유형 신규 추가 → 폼 필드를 GUI 로 배치(드래그·드롭) → 워크플로우 단계 시각 편집(접수→검토→채택/반려) → 단계별 역할 라우팅(검토는 [부서장]) → 발행 → 즉시 사용 가능."

---

## 3. 모듈 범위 (확장)

| 모듈 (`systemType`) | v2.0 범위 | v2.1 추가 |
|---|---|---|
| `ITSM` | 티켓 단순 CRUD | **요청 유형**(장애·서비스요청·변경·문제·QnA)·**워크플로우 정의**·**단계**·**역할 라우팅**·**SLA**·결재 |
| `ITAM` | 자산 단일 원장 | **자산 분류 트리**(HW/SW/라이선스/계약/서비스 하위)·분류별 폼 메타·**이력 이벤트**(취득·이관·폐기·갱신·수리) |
| `PMS` | 미구현 | 프로젝트·태스크·간트·자원 (기본 CRUD) |
| `COMMON` | 미구현 | **공통 코드 트리**·공지·첨부 파일 |
| `SYSTEM` | 미구현 | **사용자·부서·역할·권한·메뉴 트리·시스템 설정·메타 관리 UI·WYSIWYG 편집기** |

---

## 4. 도메인 모델

### 4-1. 인증·사용자 (SYSTEM 모듈 핵심)

```
User
 ├── id (BIGSERIAL PK)
 ├── username (UNIQUE)
 ├── password_hash, password_changed_at
 ├── name, email (옵션), phone (옵션)
 ├── department_id (FK → Department, NULL 허용)
 ├── status (ACTIVE / LOCKED / RETIRED)
 ├── created_at, updated_at, last_login_at

Department (조직 트리)
 ├── id (BIGSERIAL PK)
 ├── code (UNIQUE), name
 ├── parent_id (FK 자기참조), path ('1/3/12' 형태로 sort/검색)
 ├── manager_user_id (FK → User, NULL 허용)
 └── active

Role (역할)
 ├── id (BIGSERIAL PK)
 ├── code (UNIQUE — 예: 'ROLE_ADMIN', 'ROLE_IT_SUPPORT')
 ├── name (라벨)
 └── description

Permission (권한 — 메뉴·기능 단위)
 ├── id (BIGSERIAL PK)
 ├── code (UNIQUE — 예: 'TICKET_CREATE', 'TICKET_APPROVE_L1', 'META_PUBLISH')
 ├── name, description

user_role (N:M)
 ├── user_id, role_id, granted_at, granted_by

role_permission (N:M)
 ├── role_id, permission_id

Menu (동적 메뉴 트리)
 ├── id (BIGSERIAL PK)
 ├── code (UNIQUE)
 ├── parent_id (FK 자기참조)
 ├── label, icon, sort_order
 ├── route (예: '/itsm', '/itam')
 ├── group_id (옵션 — PageMeta 그룹과 연결되면 DynamicPage 진입)
 ├── permission_code (NULL 허용 — NULL 이면 누구나, 값 있으면 그 권한 필요)
 └── active
```

> 메뉴 → 화면 진입: 메뉴 클릭 → `route` 로 라우팅 → `_DynamicRoute.vue` 가 `group_id` 로 PageMeta 조회 → DynamicPage 자동 생성.

### 4-2. ITSM (도메인 깊이)

```
TicketRequestType (요청 유형)
 ├── id (BIGSERIAL PK)
 ├── code (UNIQUE — 'INCIDENT' / 'SERVICE_REQUEST' / 'CHANGE' / 'PROBLEM' / 'QNA')
 ├── label
 ├── form_meta_group_id (예: 'itg-ticket-incident')      ← 폼 메타 분기 키
 ├── default_workflow_id (FK → WorkflowDefinition)
 ├── sla_minutes_default
 └── active

Ticket (v2.0 의 ticket 테이블 확장)
 ├── 기존 컬럼 (id, ticket_no, title, content, priority, status, …)
 ├── request_type_code (FK → TicketRequestType.code) ★ 신규
 ├── workflow_instance_id (FK → WorkflowInstance) ★ 신규
 ├── requester_user_id (FK → User) ★ 신규 (assignee_id 외에 요청자)
 ├── page_meta_id_at_registration ★ 신규 (이력 복원 — ITAM 패턴 적용)
 └── ...

WorkflowDefinition (단계 정의 — page_meta 와 별개의 자체 모델)
 ├── id (BIGSERIAL PK)
 ├── code (UNIQUE — 'WF_CHANGE_STD')
 ├── name, version
 ├── steps (JSONB) — 단계 배열 (※ 4-2-a 참조)
 ├── active, created_at, updated_at

WorkflowInstance (티켓이 실제로 거치는 워크플로우 인스턴스)
 ├── id (BIGSERIAL PK)
 ├── workflow_definition_id (FK)
 ├── ticket_id (FK)
 ├── current_step_index (0-based)
 ├── status (RUNNING / COMPLETED / CANCELED)
 ├── started_at, completed_at

WorkflowInstanceStep (단계별 실행 이력)
 ├── id (BIGSERIAL PK)
 ├── workflow_instance_id (FK)
 ├── step_index
 ├── step_name (스냅샷)
 ├── assigned_to_user_id / assigned_to_role_code (둘 중 하나)
 ├── started_at, completed_at, sla_due_at
 ├── action (APPROVED / REJECTED / FORWARDED / COMPLETED)
 ├── action_by_user_id, action_comment
```

#### 4-2-a. `WorkflowDefinition.steps` JSONB 스키마

```json
{
  "steps": [
    { "index": 0, "name": "접수",     "assignee_role_code": "ROLE_IT_SUPPORT",
      "sla_minutes": 60,  "allowed_actions": ["FORWARD", "COMPLETE"] },
    { "index": 1, "name": "1차 검토",  "assignee_role_code": "ROLE_TEAM_LEAD",
      "sla_minutes": 240, "allowed_actions": ["APPROVE", "REJECT"] },
    { "index": 2, "name": "변경 적용", "assignee_role_code": "ROLE_IT_SUPPORT",
      "sla_minutes": 480, "allowed_actions": ["COMPLETE"] },
    { "index": 3, "name": "종결 확인", "assignee_role_code": "ROLE_REQUESTER",
      "sla_minutes": null,"allowed_actions": ["CONFIRM", "REOPEN"] }
  ]
}
```

> v2.1 의 워크플로우는 **자체 단계 엔진(MVP)**. BPMN/Camunda 도입은 ADR-017 로 별도 미루기.

### 4-3. ITAM (분류별 원장)

```
AssetCategory (자산 분류 트리)
 ├── id (BIGSERIAL PK)
 ├── code (UNIQUE — 'HW_LAPTOP' / 'HW_SERVER' / 'SW_LICENSE' / 'CONTRACT_NDA' …)
 ├── label
 ├── parent_id (FK 자기참조), path
 ├── form_meta_group_id (예: 'itg-asset-hw-laptop')      ← 분류별 메타 분기
 └── active

Asset (v2.0 확장)
 ├── 기존 컬럼 …
 ├── category_code (FK → AssetCategory.code) ★ 신규
 └── (page_meta_id_at_registration 은 그대로 유지)

AssetLifecycleEvent (자산 이력 이벤트)
 ├── id (BIGSERIAL PK)
 ├── asset_id (FK)
 ├── event_type (ACQUIRED / TRANSFERRED / REPAIRED / DISPOSED / RENEWED)
 ├── event_date, by_user_id, payload (JSONB — 이벤트별 부가 정보)
```

### 4-4. PMS (기본 CRUD — v2.1 MVP)

```
Project
 ├── id, code, name, status (PLANNED / IN_PROGRESS / DONE / ON_HOLD)
 ├── owner_user_id, dept_id
 ├── started_at, due_at, finished_at

Task
 ├── id, project_id, title, status, priority
 ├── assignee_user_id, started_at, due_at
```

### 4-5. COMMON

```
CommonCode (공통 코드 트리)
 ├── id, group_code (예: 'ASSET_VENDOR', 'TICKET_REASON'), code, label, sort, active

Notice
 ├── id, title, content (TEXT), pinned_until, created_by, created_at

Attachment
 ├── id, owner_type (TICKET/ASSET/...), owner_id, file_name, mime, size, storage_path
```

---

## 5. 핵심 요구사항

### 5-1. 메타 모델·버전 라우팅 (v2.0 유지)
- `page_meta` + `page_meta_active` 뷰 + 자동 DEPRECATE 트리거.
- `systemType`·`packageType`·`groupId`·`major`·`minor`·`metaStatus` 필수.

### 5-2. 동적 렌더링 (v2.0 유지 + 확장)
- `DynamicPage` 한 컴포넌트가 메타 → 화면 자동 생성.
- 확장: 메뉴 `group_id` 연결, 메타 분기(요청 유형·자산 분류).

### 5-3. 워크플로우 엔진 (신규 — MVP)
- `WorkflowDefinition.steps` JSONB 단계 모델 (4-2-a).
- 티켓 생성 시 요청 유형의 `default_workflow_id` 로 WorkflowInstance 자동 생성.
- 단계 진입 시 `assignee_role_code` 의 모든 User 가 알림 대상 / `assigned_to_user_id` 직접 지정도 허용.
- 각 단계는 `allowed_actions` 만 허용. CLOSED 상태는 도메인 가드(v2.0 패턴 재사용).
- SLA: `sla_due_at` 초과 시 별도 알림 (구현 단순화 — 알림은 stub).
- BPMN 엔진 도입은 ADR-017 로 별도 미루기 (Camunda 7 통합 예시 첨부).

### 5-4. No-code WYSIWYG 편집기 (신규 — 단계적)
- **1단계 (M9)**: 폼/그리드 메타 편집 UI. 필드 추가·삭제·라벨 편집·옵션 편집·발행. JSON 직접 노출 없음.
- **2단계 (M10)**: 드래그앤드롭. 필드 순서·레이아웃 변경, 그리드 컬럼 순서·width.
- **3단계 (M11)**: WYSIWYG. 실 미리보기에서 직접 클릭·편집. (Stretch — Builder.io 급) — **PoC 완료, 결정: 보류**(M9·M10 폼 UI 로 충분, PoC 자산 보존·재도입 트리거 명시 — `docs/STRETCH_DECISION_REPORT.md`).

### 5-5. AI 메타 자동 생성 (v2.0 유지 + 확장)
- `scripts/generate_meta.py` 기본 매핑.
- 확장: 요청 유형·자산 분류 휴리스틱 추가, 라벨 한글화 프롬프트 강화.

### 5-6. 인증·권한·메뉴 (신규)
- JWT (Bearer) 로그인 — `/api/auth/login` + `/api/auth/refresh`.
- `JwtAuthenticationFilter` 실 검증.
- `SecurityConfig` 의 permitAll 은 `/api/auth/login`, `/swagger-ui/**`, `/actuator/health` 만.
- 메뉴: `GET /api/menu` 가 현재 로그인 사용자의 권한 트리 반환.
- 권한: `@PreAuthorize("hasPermission(...)") ` 또는 동등 검사.
- 단일 테넌트 (v2.1 범위). Multi-tenant 는 ADR-018 별도.

---

## 6. UX 베이스라인 (신규)

### 6-1. 빈 상태·에러·로딩 메시지 카탈로그
- raw 백엔드 errorCode/메시지 **사용자 노출 금지**.
- 모든 화면에 "빈 상태" 안내 (예: 그리드 0건 시 "아직 등록된 항목이 없습니다 [등록]").
- 로딩 표시 — 텍스트 또는 skeleton, blink 금지.
- 에러 — 친화 한글 메시지 + 액션 버튼 (재시도/관리자 문의 등).

### 6-2. raw 토큰 노출 금지 규칙
- 백엔드 `ApiResponse.errorCode` 는 frontend 분기·로깅 용도. 화면에는 **한글 message** 만 노출.
- `META_NOT_PUBLISHED`·`TICKET_NOT_FOUND` 등 코드를 UI 텍스트에 직접 표시 금지.

### 6-3. 시각 회귀 자동 검증 (신규 — ADR-019)
- Playwright e2e 도입 — 핵심 라우트별 스크린샷 자동 캡처·diff.
- CI workflow 통과 조건에 포함.

---

## 7. 비기능 요구사항

| 항목 | 목표 |
|---|---|
| Service 단위 테스트 커버리지 | 70% 이상 (v2.0 게이트 유지) |
| 중복 코드 | 3% 이하 |
| 보안 취약점 (SonarQube) | Blocker·Critical 0 |
| 하드코딩 시크릿 | 0 |
| 그리드 60fps | rows 1000+ AG Grid 가상 스크롤 |
| API p95 | < 200ms (메타·자산·티켓 단건) |
| Playwright e2e | 핵심 5 라우트 PASS |
| 동시 접속 | 100 user (단일 인스턴스 기준) |
| 가용성 | 99.5% (운영 환경) |

---

## 8. 성공 지표

- **비개발자 가 메타 1건 발행 평균 소요 시간**: WYSIWYG 단순 폼 = 10 분 이내.
- **요청 유형별 폼 분기**: 같은 `/itsm/new` 에서 5종 요청 유형 폼이 모두 동작.
- **워크플로우**: 변경 요청이 4단계를 거쳐 종결까지 자동 진행.
- **자산 분류별 원장**: HW_LAPTOP / SW_LICENSE 두 분류가 다른 폼으로 등록 가능.
- **메뉴 동적**: 로그인 사용자의 권한에 따라 메뉴 트리가 달라짐.
- **PACKAGE ↔ CUSTOM 분리율** 100% (v2.0 약속 유지).
- **DRAFT 노출 사고** 0건 (v2.0 약속 유지).
- **회귀** Playwright e2e 5 라우트 항상 PASS.

---

## 9. 의도적 비포함 (재정의)

- 다국어(i18n) — v2.2 검토 (v2.1 범위 아님).
- 모바일 전용 앱 — 반응형 웹으로만 대응.
- 외부 IDP(SAML/OIDC) — v2.2 검토. v2.1 은 JWT 자체.
- 멀티 테넌트 — 단일 인스턴스 가정. (ADR-018 에서 별도 다룸)
- BPMN/Camunda 엔진 — v2.1 은 자체 단계 엔진(MVP). BPMN 은 ADR-017 stretch.
- 실시간 알림 (websocket/push) — v2.1 은 폴링·이메일 stub.
- 파일 미리보기·이미지 처리 — v2.1 은 단순 업로드/다운로드.
- **v2.0 의 "메타 시각 편집기 OoS" 항목 — v2.1 에서 핵심 범위로 끌어올림 (§5-4)**.

---

## 10. 마일스톤 재구성

### v2.0 완료 (1차)
| M | 내용 | 상태 |
|---|---|---|
| M1 | `page_meta` 스키마·뷰·트리거 + MetaService | ✅ |
| M2 | DynamicPage/Form/Grid + 그리드 자동 분기 | ✅ |
| M3 | ITSM 티켓 백엔드 + 프런트 통합 (단순 CRUD) | ⚠️ (도메인 깊이는 M8 에서 확장) |
| M4 | ITAM 자산 백엔드 + 이력 메타 복원 | ⚠️ (분류별 원장은 M8) |
| M5 | AI 메타 자동 생성 CLI + dry-run | ✅ |
| M6 | SonarQube CI 게이트 | ✅ |

### v2.1 신규
| M | 내용 | 우선순위 | 예상 phase 수 |
|---|---|---|---|
| **M7** | **인증·사용자·부서·역할·권한·메뉴** | **최우선** | 3~4 phase |
| **M8** | **도메인 깊이** — 요청 유형·자산 분류·워크플로우 MVP | **최우선** | 4~5 phase |
| **M9** | **메타 편집 UI (No-code 1단계)** — 폼 기반 메타 편집·발행 | 높음 | 2~3 phase |
| M10 | 드래그앤드롭 page builder (No-code 2단계) | 중간 | 2~3 phase |
| M11 | WYSIWYG · BPMN 엔진 (Stretch) | 낮음 | **PoC 완료 — 결정: 보류 (WYSIWYG·BPMN 둘 다)** — `docs/STRETCH_DECISION_REPORT.md` |
| 베이스라인 | UX 카탈로그 + Playwright e2e + Dialog/스타일 hotfix | M7 와 병행 | 1 phase |

### 권장 진행 순서
1. **UX hotfix + 베이스라인 정착** (Playwright 도입) — 즉시
2. **M7** (인증·메뉴) — 모든 후속 phase 가 인증을 가정
3. **M8** (도메인 깊이) — 실 솔루션 수준
4. **M9** (메타 편집 UI) — 비개발자 진입
5. **M10** (드래그앤드롭) — UX 향상
6. **M11** (WYSIWYG·BPMN) — 별도 PoC 평가 후 결정

---

## 부록 A. 1차 빌드의 결함 카탈로그 (v2.1 hotfix 대상)

| ID | 결함 | 영향 | 처리 |
|---|---|---|---|
| F-001 | Tailwind v4 `@source` 누락 → 모든 utility 무효 | 스타일 박살 | ✅ hotfix 적용 (tokens.css + shadcn-mapping.css) |
| F-002 | DialogOverlay `bg-black/10` 너무 투명 | Dialog CSS 박살 | ✅ hotfix (`bg-black/50`) |
| F-003 | shadcn 시멘틱 변수가 `@theme` 외부 → `bg-popover` 무효 | Dialog 흰 배경 X | ✅ hotfix (shadcn-mapping.css 에 `@import tailwindcss`) |
| F-004 | `usePageMeta` 의 notPublished 매칭 좁음 → raw 백엔드 메시지 노출 | 사용자 신뢰 손상 | ✅ hotfix (방어적 매칭 + 코드 접두사 제거) |
| F-005 | HomePage 카드의 "다음 phase 에서…" placeholder 잔존 | 미완성 인상 | ✅ hotfix (메타 ready 상태 표시) |
| F-006 | DynamicPage 의 notPublished/error 카드 raw 텍스트 | 사용자 친화도 | ✅ hotfix (친화 카탈로그) |
| F-007 | system/MetaPage 의도 불분명 | UX | ✅ hotfix (안내 보강) |
| F-008 | `@PreAuthorize` 등 권한 가드 부재 | 보안 결함 | M7 의 일부 |
| F-009 | 자동 e2e 가 실 브라우저 시각 결함 못 잡음 | 회귀 위험 | M7 베이스라인의 Playwright |

(부록 B: 도메인 모델 ERD 그림은 ARCHITECTURE.md 에 별도 그림으로 첨부 예정)
