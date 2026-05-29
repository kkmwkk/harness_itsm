# 아키텍처 — Polestar10 ITG v2 (v2.1 개정)

> Frontend(Vue3) → Backend(Spring Boot 3 / Java 21) → DB(PostgreSQL 16) 의 단방향 호출.
> 모든 화면은 `page_meta` 의 메타 한 건에서 동적으로 생성된다 (ADR-004).
> v2.1 보강: 인증·메뉴·요청유형·워크플로우·자산분류·No-code 편집기 도메인 모델 추가.

## 1. 전체 구성

```
┌──────────────────────────────────────────────────────────────────────┐
│                          Browser (Vue 3 SPA)                         │
│                                                                      │
│  로그인 → 메뉴 트리(권한 필터) → 메뉴 클릭 → <DynamicPage> 진입       │
│        │                                                             │
│        ├── usePageMeta()  →  GET /api/meta/active/{groupId}          │
│        │                     (요청유형/자산분류 기반 group 분기)     │
│        ├── <DynamicForm>  ←  meta.form  → shadcn/vue + VeeValidate   │
│        ├── <DynamicGrid>  ←  meta.grid  → AG Grid / DataTable        │
│        └── <WorkflowPanel>←  workflow.steps + 현재 단계 + 액션       │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ HTTPS · JWT(Bearer)
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│           Spring Boot 3 (Java 21, Virtual Thread, Gradle)            │
│                                                                      │
│  JwtAuthenticationFilter (실 검증) → Controller(Swagger) →           │
│  @PreAuthorize 권한 → Service(@Transactional) →                      │
│  Repository(JPA + QueryDSL) → Entity                                 │
│                                                                      │
│  공통: ApiResponse<T> + ITGException + GlobalExceptionHandler        │
│  도메인: meta / auth / user / dept / role / perm / menu /            │
│         itsm.ticket / itsm.workflow / itsm.requesttype /             │
│         itam.asset / itam.category / pms / common                    │
└────────────────────────────────────────┬─────────────────────────────┘
                                         │ JDBC (HikariCP)
                                         ▼
┌──────────────────────────────────────────────────────────────────────┐
│         PostgreSQL 16 (Docker Desktop, localhost:5432, itgdb)        │
│                                                                      │
│  page_meta          (JSONB · 트리거 · UNIQUE(group,major,minor))     │
│  page_meta_active   (뷰: groupId 별 PUBLISHED 최신 1건)              │
│  user · dept · role · permission · user_role · role_permission · menu│
│  ticket · ticket_request_type · workflow_definition ·                │
│       workflow_instance · workflow_instance_step                     │
│  asset · asset_category · asset_lifecycle_event                      │
│  project · task · common_code · notice · attachment                  │
└──────────────────────────────────────────────────────────────────────┘
```

## 2. 디렉토리 구조

### 2-1. 프로젝트 루트
```
polestar-itg-nocoding-workspace/
├── docker-compose.yml · docker-compose.sonar.yml
├── sql/init/                         # 컨테이너 부팅 시 자동 적용
│   ├── 01_schema.sql                 # page_meta + 트리거 + 뷰
│   ├── 02_ticket.sql · 03_itg_ticket_meta.sql · 04_sample_tickets.sql
│   ├── 05_asset.sql  · 06_itg_asset_meta.sql · 07_sample_assets.sql
│   ├── 10_auth.sql                   # ★ v2.1 — user/dept/role/permission/menu (M7)
│   ├── 11_itsm_workflow.sql          # ★ v2.1 — ticket_request_type/workflow_* (M8)
│   ├── 12_itam_category.sql          # ★ v2.1 — asset_category (M8)
│   └── 13_common_pms.sql             # ★ v2.1 — common_code/notice/attachment/project/task
├── frontend/                         # Vite 6 + Vue 3 + shadcn/vue
└── backend/                          # Spring Boot 3 + Java 21
```

### 2-2. Frontend (`frontend/src/`)
```
components/
  ui/                          # shadcn/vue
  dynamic/                     # DynamicPage · DynamicForm · DynamicGrid · WorkflowPanel★
  layout/                      # AppLayout · TopBar · Sidebar · PageHeader
  common/                      # StatusBadge · PriorityBadge · UserPicker · DatePicker
  editor/                      # ★ v2.1 — MetaEditor (M9) · FieldEditor · GridColumnEditor
  workflow/                    # ★ v2.1 — StepEditor · ActionBar · TransitionDialog
composables/
  usePageMeta.ts               # /api/meta/active/{groupId} | /api/meta/{metaId}
  usePageData.ts               # /api/{module} 데이터 fetch (page/sort/filters)
  useDataMutation.ts           # POST/PATCH 공통 + 토스트
  useAuth.ts                   # ★ v2.1 — 로그인·토큰·사용자 정보
  useMenu.ts                   # ★ v2.1 — /api/menu 트리·권한 필터
  useWorkflow.ts               # ★ v2.1 — 현재 단계·허용 액션·이력
  useGridColumns.ts · useFormSchema.ts
stores/
  useLayoutStore.ts
  useAuthStore.ts              # ★ v2.1 — JWT 토큰·사용자·역할
  useMenuStore.ts              # ★ v2.1 — 메뉴 트리·active
lib/
  api.ts                       # createFetch + Authorization 자동 주입
  meta-body.ts · badges.ts · ag-grid-modules.ts
types/
  meta.ts · meta-body.ts · page.ts
  auth.ts                      # ★ v2.1 — User/Role/Permission/Menu 타입
  workflow.ts                  # ★ v2.1 — WorkflowDefinition/Step/Action
  ticket.ts · asset.ts
pages/
  LoginPage.vue                # ★ v2.1 — /login (M7)
  HomePage.vue                 # 대시보드 (메타 ready 상태 표시)
  _DynamicRoute.vue            # 메타 라우팅 진입점
  itsm/  itam/  pms/  common/  system/
  system/
    UserPage.vue · DeptPage.vue · RolePage.vue · MenuPage.vue   # ★ v2.1 (M7)
    MetaEditorPage.vue                                          # ★ v2.1 (M9)
    WorkflowEditorPage.vue                                      # ★ v2.1 (M8)
```

### 2-3. Backend (`backend/src/main/java/com/nkia/itg/`)
```
common/
  response/   exception/   security/   config/
auth/                                   # ★ v2.1 (M7)
  controller/AuthController             # /api/auth/login · refresh
  service/AuthService · JwtService
  filter/JwtAuthenticationFilter (실 검증)
  dto/LoginRequest · TokenResponse
system/                                 # ★ v2.1 (M7)
  user/   (controller/service/repo/entity/dto)
  dept/
  role/
  permission/
  menu/
meta/                                   # 기존
  controller/   service/   repository/   entity/   dto/   domain/
itsm/
  ticket/                               # 기존 (M3) — 확장
  requesttype/                          # ★ v2.1 (M8)
  workflow/                             # ★ v2.1 (M8)
    controller/WorkflowController
    service/WorkflowEngineService       # 단계 진입·전이·SLA
    repository/WorkflowDefinitionRepository · WorkflowInstanceRepository
    entity/WorkflowDefinition · WorkflowInstance · WorkflowInstanceStep
    domain/StepAction (APPROVE/REJECT/FORWARD/COMPLETE/CONFIRM/REOPEN)
itam/
  asset/                                # 기존 (M4) — 확장
  category/                             # ★ v2.1 (M8) — AssetCategory 트리
pms/ · common/
```

## 3. 메타 모델 (v2.0 유지)

### 3-1. 스키마 핵심
- `id PK` — 패턴 `{groupId}-v{major}-{minor}`.
- `system_type` · `package_type` · `group_id` · `(major, minor)` · `meta_status` 6축 필수.
- `meta_json JSONB NOT NULL`.
- `UNIQUE (group_id, major_version, minor_version)`.

### 3-2. 화면 노출용 뷰 — `page_meta_active`
v2.0 그대로. `WHERE meta_status='PUBLISHED' AND active=TRUE` 의 group 별 최신 1건.

### 3-3. 자동 DEPRECATE 트리거 + Service 이중 안전망
v2.0 그대로. ADR-006.

### 3-4. PageMeta JSON 본문 — v2.1 확장 키

```json
{
  "id": "itg-ticket-incident-v1-1",
  "title": "장애 요청",
  "systemType": "ITSM",
  "packageType": "PACKAGE",
  "groupId": "itg-ticket-incident",     // 요청 유형별 group 분기 ★
  "majorVersion": 1, "minorVersion": 1, "metaStatus": "PUBLISHED",
  "api": "/api/tickets",
  "requestTypeCode": "INCIDENT",        // ★ v2.1 — 요청 유형 키
  "workflowDefinitionCode": "WF_INCIDENT_STD",  // ★ v2.1 — 워크플로우 연결
  "grid":  { "columns": [ ... ] },
  "form":  { "layout": "two-column", "fields": [ ... ] },
  "detail":{ "fields": [ ... ] },
  "actions": [ ... ]
}
```

> 자산 분류 메타도 동일 패턴: `groupId: "itg-asset-hw-laptop"`, `categoryCode: "HW_LAPTOP"`.

## 4. 동적 렌더링 흐름 (v2.1 확장)

```
[사용자 라우팅]   /itsm
      │
      ▼
[메뉴 클릭]       useMenuStore.activeMenu → routes 매핑
      │
      ▼
<_DynamicRoute>   route.meta.groupId (예: 'itg-ticket' 또는 'itg-ticket-incident')
      │           ※ 요청 유형 선택 후 group 동적 결정도 가능
      ▼
<DynamicPage>
      │
      ├─(1) usePageMeta(groupId) → GET /api/meta/active/{groupId}
      │
      ├─(2) usePageData(api)     → GET /api/{module}?page&size&sort&filters
      │
      ├─(3) <DynamicGrid> :meta="body.grid" :rows="rows"
      │         행 클릭 → /itsm/:id 상세 라우팅
      │
      ├─(4) <DynamicForm> (dialog) :meta="body.form"
      │         submit → POST /api/{module} → 토스트 + reload
      │
      └─(5) ★ v2.1 <WorkflowPanel>
              useWorkflow(ticketId) → GET /api/workflow/instance/by-ticket/{ticketId}
              현재 단계·허용 액션·이력 표시
              액션 클릭 → POST /api/workflow/instance/{id}/step/{idx}/action
```

## 5. 필드 타입 매핑 (v2.0 유지)

| 백엔드(JPA/Swagger) | FieldType | shadcn 컴포넌트 |
|---|---|---|
| `String` | `text` | `Input` |
| `@Lob String` / `> 500자` | `textarea` | `Textarea` |
| `Int`·`Long`·`BigDecimal` | `number` | `Input(type=number)` |
| `LocalDate`·`LocalDateTime` | `date` / `date-range` | `DatePicker` |
| `Enum` | `select` | `SelectField` |
| `Boolean` | `checkbox` | `CheckboxField` |
| `User assignee` | `user-picker` | `UserPicker` |
| `MultipartFile` | `file` | `FileUpload` |
| (도메인) Status / Priority | `status` / `priority` | `StatusBadge` / `PriorityBadge` |

## 6. 그리드 렌더러 선택 규칙 (v2.0 유지)
- `rows <= 1000` → shadcn DataTable
- `rows > 1000` 또는 `inlineEdit` 또는 `export` → AG Grid Vue3
- ADR-007.

## 7. API 설계 (v2.1 확장)

### 7-1. 메타 (v2.0 유지)
| 메서드 | 경로 | 비고 |
|---|---|---|
| GET | `/api/meta/active/{groupId}` | PUBLISHED 최신 1건 |
| GET | `/api/meta/{metaId}` | 단건 (어떤 상태든) |
| GET | `/api/meta/group/{groupId}/versions` | 전체 버전 이력 |
| PATCH | `/api/meta/{metaId}/publish` · `/archive` | 상태 전이 |
| POST | `/api/meta/{metaId}/copy` | 신규 DRAFT |
| POST | `/api/meta/dry-run` | 검증 (INSERT 없음) |

### 7-2. 인증 ★ v2.1
| 메서드 | 경로 | 비고 |
|---|---|---|
| POST | `/api/auth/login` | 사용자명/비밀번호 → JWT |
| POST | `/api/auth/refresh` | refresh 토큰 → 새 access |
| POST | `/api/auth/logout` | 토큰 무효화 (옵션) |
| GET | `/api/auth/me` | 현재 사용자·역할 |

### 7-3. 사용자·부서·역할·권한·메뉴 ★ v2.1
| 메서드 | 경로 | 비고 |
|---|---|---|
| GET | `/api/users?page&size&dept&role` | 검색 |
| POST·PATCH·DELETE | `/api/users` · `/api/users/{id}` | CRUD |
| GET | `/api/departments` | 트리 |
| POST·PATCH | `/api/departments` · `/{id}` | 트리 변경 |
| GET·POST·PATCH | `/api/roles` · `/{id}` | 역할 CRUD |
| GET | `/api/permissions` | 권한 목록 |
| GET | `/api/menus` | 전체 (관리자) |
| GET | `/api/menu` | **현재 사용자의 메뉴 트리** (권한 필터 적용) |
| POST·PATCH | `/api/menus` · `/{id}` | 메뉴 트리 변경 |

### 7-4. ITSM 요청 유형·워크플로우 ★ v2.1
| 메서드 | 경로 | 비고 |
|---|---|---|
| GET | `/api/ticket-request-types` | 활성 요청 유형 목록 |
| POST·PATCH | `/api/ticket-request-types` · `/{code}` | CRUD |
| GET | `/api/workflow-definitions` · `/{code}` | 워크플로우 정의 |
| POST·PATCH | `/api/workflow-definitions` · `/{code}` | 정의 CRUD (관리자) |
| GET | `/api/workflow-instances/by-ticket/{ticketId}` | 인스턴스 + 단계 이력 |
| POST | `/api/workflow-instances/{id}/step/{idx}/action` | 단계 액션 (APPROVE 등) |

### 7-5. ITAM 자산 분류 ★ v2.1
| 메서드 | 경로 | 비고 |
|---|---|---|
| GET | `/api/asset-categories` | 트리 |
| POST·PATCH | `/api/asset-categories` · `/{code}` | CRUD |
| GET | `/api/assets/{id}/lifecycle-events` | 자산 이벤트 이력 |

응답은 v2.0 의 `ApiResponse<T>` 래퍼 강제 (ADR-009). 페이지는 `PageResponse<T>` 평탄화.

## 8. 인증·인가 (v2.1 정식 구현)

### 8-1. JWT 흐름
```
1) POST /api/auth/login { username, password }
   → AuthService 가 BCrypt 비교 → 통과 시 JwtService.issue(user)
   → { accessToken (15min), refreshToken (7d), user, roles[] } 반환
2) Frontend 의 useAuthStore 가 토큰 저장 (httpOnly cookie 권장, 또는 localStorage stub)
3) lib/api.ts 의 createFetch beforeFetch 가 Authorization: Bearer 자동 주입
4) JwtAuthenticationFilter 가 요청마다 검증 → SecurityContext 에 Authentication 설정
5) Controller 의 @PreAuthorize("hasPermission(...)") 가 권한 검사
```

### 8-2. SecurityConfig (v2.1)
```
permitAll: /api/auth/login · /api/auth/refresh · /swagger-ui/** · /v3/api-docs/** · /actuator/health
authenticated: 그 외 모든 /api/**
```

### 8-3. 메뉴 권한 필터
```
GET /api/menu →
  for each menu in menus:
    if menu.permission_code is null OR
       currentUser.permissions contains menu.permission_code:
      include
    else: skip
  결과를 parent_id 트리로 조립해 반환
```

### 8-4. 도메인 권한
- `TICKET_CREATE`·`TICKET_APPROVE_L1`·`META_PUBLISH`·`USER_ADMIN` 등 코드.
- 워크플로우 단계의 `assignee_role_code` 와 현재 사용자의 `Role` 매칭으로 단계 진입 가능 여부 결정.

## 9. 트랜잭션·동시성 (v2.0 유지)
- 트랜잭션 경계 = `@Service` 메서드.
- 워크플로우 단계 전이 = 한 트랜잭션 (`WorkflowInstance.currentStep` 갱신 + `WorkflowInstanceStep` 행 추가).
- Virtual Thread 활성화.

## 10. AI 메타 자동 생성 (v2.0 유지)
- `scripts/generate_meta.py` + `POST /api/meta/dry-run` + `docs/META_GENERATION_GUIDE.md`.
- v2.1 확장: 요청 유형·자산 분류 휴리스틱 보강.

## 11. 로컬 인프라 (v2.0 유지)
- `docker-compose.yml` → postgres:16 + pgAdmin.
- `docker-compose.sonar.yml` → SonarQube 분석용 (선택).
- 초기화 SQL 은 `sql/init/` 파일명 사전순.

## 12. 관측·문서
- Swagger UI: `/swagger-ui.html`.
- OpenAPI JSON/YAML: `/v3/api-docs(.yaml)` → `backend/openapi/itg-api-spec.{json,yaml}`.
- SonarQube CI 게이트 (M6).
- v2.1 추가: **Playwright 시각 회귀** (ADR-019) — 핵심 라우트 스크린샷·diff 자동.

## 13. v2.0 ↔ v2.1 변화

| 영역 | v2.0 | v2.1 |
|---|---|---|
| 인증 | permitAll | JWT 실 검증 + `@PreAuthorize` |
| 사용자/부서/역할/권한/메뉴 | 없음 | DB 모델 + CRUD + 동적 메뉴 |
| ITSM 도메인 | 단순 ticket CRUD | `request_type` + `workflow_*` + 역할 라우팅 |
| ITAM 도메인 | 단일 원장 | `asset_category` 트리 + `asset_lifecycle_event` |
| Frontend | hardcoded routes · placeholder | useMenu 기반 + 친화 카탈로그 |
| 메타 편집 | JSON 직접 / AI CLI | Form UI (M9) → 드래그(M10) → WYSIWYG(M11) |
| e2e | cURL + 코드 인스펙션 | + Playwright 시각 회귀 |

## 14. 도메인 모델 ERD ★ v2.1

### 14-1. 인증·메뉴
```
User ─N:1─ Department
 │
 ├─N:M─ Role ─N:M─ Permission
 │
 └─ Menu ─N:1─ Permission (옵션)
         ├── parent_id (자기참조 트리)
         └── group_id  (PageMeta 그룹과 연결 → DynamicPage 진입)
```

### 14-2. ITSM
```
TicketRequestType ─1:1─ form_meta_group_id (예: 'itg-ticket-incident')
        │
        ├─1:1─ WorkflowDefinition
        │            │
        │            └─N:1─ WorkflowStep (JSONB)
        │                      • assignee_role_code (Role 참조)
        │                      • sla_minutes · allowed_actions
        │
        └─1:N─ Ticket
                 │
                 ├─N:1─ WorkflowInstance ─1:N─ WorkflowInstanceStep
                 │
                 ├─N:1─ User (requester / assignee)
                 │
                 └── page_meta_id_at_registration (이력 보존, ITAM 패턴)
```

### 14-3. ITAM
```
AssetCategory (트리, code 식별)
        │
        ├── form_meta_group_id (예: 'itg-asset-hw-laptop')
        │
        └─1:N─ Asset ─1:N─ AssetLifecycleEvent
                              event_type: ACQUIRED/TRANSFERRED/REPAIRED/DISPOSED/RENEWED
```

## 15. 워크플로우 엔진 (MVP — ADR-015) ★ v2.1

### 15-1. 단계 진입 흐름
```
1) Ticket 생성 (request_type_code = 'INCIDENT')
2) TicketService.create() 가 TicketRequestType.default_workflow_id 로 WorkflowInstance 생성
   - current_step_index = 0
   - WorkflowInstanceStep[0] 행 추가 (started_at, sla_due_at)
   - assignee_role_code 의 사용자 풀에 알림 stub (M11 에서 실 알림)
3) 단계 액션 (POST /workflow-instances/{id}/step/{idx}/action)
   - 검증: 현재 사용자가 step 의 assignee_role 보유 + action 이 allowed_actions 안에
   - 액션에 따라 다음 단계로 전이 또는 종결
4) 마지막 단계 완료 시 WorkflowInstance.status = COMPLETED, Ticket.status = CLOSED
```

### 15-2. 단계 전이 매트릭스 (action → 다음 단계)
```
APPROVE    → 다음 단계 (index + 1)
REJECT     → 종결 (status=REJECTED) 또는 이전 단계 (정의에 따라)
FORWARD    → 다음 단계 (스킵 단계 옵션)
COMPLETE   → 다음 단계 또는 종결
CONFIRM    → 종결 (status=COMPLETED)
REOPEN     → 첫 단계로 (CLOSED 후 재오픈 시)
```

### 15-3. CLOSED 가드 (v2.0 패턴 재사용)
- `Ticket.status = CLOSED` 또는 `WorkflowInstance.status = COMPLETED` 시 모든 액션·수정 거부.
- 도메인 메서드 `WorkflowInstance.executeAction()` 안에 가드 박힘.

## 16. No-code 편집기 단계적 도입 (ADR-016) ★ v2.1

### 16-1. M9 — 폼 기반 메타 편집기
- `system/MetaEditorPage.vue` — 메타 ID 선택 → 폼/그리드 정의 편집 → DRAFT 저장 → publish.
- 컴포넌트: `FieldEditor` (필드 추가/삭제/시즌/라벨/옵션), `GridColumnEditor` (컬럼 추가/순서/width).
- JSON 직접 노출 없음. 모든 편집은 UI 컨트롤.
- 발행 흐름: `POST /api/meta` (DRAFT) → `dry-run` 자동 → `PATCH /publish`.

### 16-2. M10 — 드래그앤드롭
- 폼 필드 순서·레이아웃(`span: 1|2`) 드래그.
- 그리드 컬럼 순서·width 드래그.
- 라이브러리 후보: VueDraggablePlus / SortableJS (CDN 가볍).

### 16-3. M11 — WYSIWYG (Stretch)
- 실 화면 미리보기에서 직접 클릭·편집.
- 별도 PoC 후 결정. Builder.io / GrapeJS 등 참고.

## 17. UX 베이스라인 ★ v2.1

### 17-1. 메시지 카탈로그 — `frontend/src/lib/ui-messages.ts`
```ts
export const UI = {
  empty: {
    grid:    '아직 등록된 항목이 없습니다.',
    metaNotPublished: '아직 준비된 화면이 없습니다. 메타가 등록되지 않았거나 배포되지 않은 상태입니다.',
  },
  error: {
    metaLoad: '메타를 불러올 수 없습니다.',
    dataLoad: '데이터를 불러올 수 없습니다.',
    submit:   '저장에 실패했습니다.',
    network:  '네트워크 연결을 확인하세요.',
    auth:     '로그인이 필요합니다.',
  },
  loading: {
    meta:    '화면 정의를 불러오는 중...',
    data:    '데이터를 불러오는 중...',
  },
} as const;
```

### 17-2. raw 토큰 노출 금지 규칙
- 백엔드 `ApiResponse.errorCode` 는 분기·로깅 용도. 화면에 직접 표시 금지.
- `usePageMeta` / `usePageData` / `useDataMutation` 의 error 메시지는 모두 `UI.error.*` 로 매핑.

### 17-3. Playwright 시각 회귀 (ADR-019)
- `frontend/e2e/playwright.config.ts` + `frontend/e2e/*.spec.ts`.
- 핵심 라우트: `/`, `/login`, `/itsm`, `/itam`, `/itam/:id`, `/system/meta`, `/system/meta-editor`.
- 각 라우트 스크린샷 캡처 → baseline 과 diff. CI workflow 의 통과 조건에 포함.

## 18. 마이그레이션 메모 (v2.0 → v2.1)

- v2.0 의 코드·메타·시드는 그대로 유지.
- v2.1 의 새 테이블(`user`·`menu`·`workflow_*`·`asset_category` 등) 은 `sql/init/10_*.sql ~ 13_*.sql` 로 추가.
- 기존 `ticket` 테이블에 `request_type_code`·`workflow_instance_id`·`requester_user_id` 컬럼 추가 (`ALTER TABLE` 멱등).
- 기존 `asset` 에 `category_code` 컬럼 추가 (`ALTER TABLE` 멱등).
- 메타 그룹 재정의: 기존 `itg-ticket-v1-1` 은 `itg-ticket` 으로 유지하되, 요청 유형별 신규 그룹(`itg-ticket-incident` 등) 을 새로 발행.
- ADR-013(TDD)·ADR-011(민감정보) 등 모든 v2.0 ADR 유지.
