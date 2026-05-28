# 아키텍처 — Polestar10 ITG v2

> Frontend(Vue3) → Backend(Spring Boot 3 / Java 21) → DB(PostgreSQL 16) 의 단방향 호출. 모든 화면은 `page_meta` 의 메타 한 건에서 동적으로 생성된다.

## 1. 전체 구성

```
┌──────────────────────────────────────────────────────────────────────┐
│                          Browser (Vue 3 SPA)                         │
│                                                                      │
│  <DynamicPage group-id="itg-ticket" />                               │
│        │                                                             │
│        ├── usePageMeta()  →  GET /api/meta/active/{groupId}          │
│        ├── <DynamicForm>  ←  meta.form  → shadcn/vue + VeeValidate   │
│        └── <DynamicGrid>  ←  meta.grid  → AG Grid / shadcn DataTable │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ HTTPS · JWT(Bearer)
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│           Spring Boot 3 (Java 21, Virtual Thread, Gradle)            │
│                                                                      │
│  Controller (Swagger 어노테이션) → Service (@Transactional) →        │
│  Repository (JPA + QueryDSL) → Entity ─┐                             │
│                                        │                             │
│  ApiResponse<T> 공통 래퍼              │                             │
│  GlobalExceptionHandler · JWT Filter   │                             │
└────────────────────────────────────────┼─────────────────────────────┘
                                         │ JDBC (HikariCP)
                                         ▼
┌──────────────────────────────────────────────────────────────────────┐
│         PostgreSQL 16 (Docker Desktop, localhost:5432, itgdb)        │
│                                                                      │
│  page_meta          (JSONB · 트리거 · 유니크 (group_id,major,minor)) │
│  page_meta_active   (뷰: groupId 별 PUBLISHED 최신 1건)              │
│  itsm_* · itam_* · pms_* · common_* · system_* 도메인 테이블         │
└──────────────────────────────────────────────────────────────────────┘
```

## 2. 디렉토리 구조

### 2-1. 프로젝트 루트
```
polestar-itg-nocoding-workspace/
├── docker-compose.yml
├── sql/init/01_schema.sql           # page_meta + 트리거 + 뷰
├── frontend/
└── backend/
```

### 2-2. Frontend (`frontend/src/`)
```
components/
  ui/                  # shadcn/vue (자동 생성, 소스 소유)
  dynamic/             # DynamicPage · DynamicForm · DynamicGrid
  common/              # StatusBadge · PriorityBadge · UserPicker · PageHeader
composables/
  usePageMeta.ts       # /api/meta/active/{groupId}
  useGridColumns.ts    # meta.grid.columns → AG Grid / DataTable 컬럼 변환
  useFormSchema.ts     # meta.form.fields → Zod schema 생성
stores/                # Pinia (storeToRefs 필수)
types/
  meta.ts              # SystemType · PackageType · MetaStatus · PageMeta
  domain/              # 모듈별 도메인 타입
lib/utils.ts           # cn() 등 공용 유틸 (shadcn 관례)
pages/
  itsm/  itam/  pms/  common/  system/
```

### 2-3. Backend (`backend/src/main/java/com/nkia/itg/`)
```
common/
  response/     ApiResponse · PageResponse
  exception/    GlobalExceptionHandler · ITGException
  security/     JwtFilter · SecurityConfig
  config/       SwaggerConfig · JpaConfig
meta/
  controller/   MetaController
  service/      MetaService (publish · copy · getActive · getVersions)
  repository/   MetaRepository (+ QueryDSL Impl)
  entity/       PageMeta
  dto/          PageMetaResponse · PageMetaVersionResponse
  domain/       SystemType · PackageType · MetaStatus (Enum)
itsm/   itam/   pms/   common/   system/
```

### 2-4. Backend Test (`backend/src/test/java/com/nkia/itg/`)
```
fixture/     MetaFixture · TicketFixture · ...
meta/        MetaServiceTest · MetaControllerTest
itsm/  itam/  pms/  common/  system/
```

## 3. 메타 모델 (`page_meta`)

### 3-1. 스키마 핵심
- `id PK` — 권장 패턴 `{groupId}-v{major}-{minor}` (예: `itg-ticket-v1-2`).
- `system_type` — `ITSM | ITAM | PMS | COMMON | SYSTEM` (CHECK 제약).
- `package_type` — `PACKAGE | CUSTOM` (CHECK 제약).
- `group_id` + `major_version` + `minor_version` — `UNIQUE (group_id, major_version, minor_version)`.
- `meta_status` — `DRAFT | PUBLISHED | DEPRECATED | ARCHIVED` (DEFAULT `DRAFT`).
- `meta_json JSONB NOT NULL` — `api`/`grid`/`form`/`detail`/`actions` 본문.
- 인덱스: `(system_type)`, `(package_type)`, `(system_type, package_type)`, `(group_id)`, `(group_id, meta_status)`, `GIN(meta_json)`.

### 3-2. 화면 노출용 뷰 — `page_meta_active`
```sql
CREATE OR REPLACE VIEW page_meta_active AS
SELECT DISTINCT ON (group_id) *
FROM   page_meta
WHERE  meta_status = 'PUBLISHED'
  AND  active      = TRUE
ORDER  BY group_id, major_version DESC, minor_version DESC;
```

### 3-3. 자동 DEPRECATE 트리거
- `BEFORE/AFTER UPDATE` 시 새 행의 `meta_status` 가 `PUBLISHED` 로 바뀌면 동일 `group_id` 내 다른 `PUBLISHED` 를 `DEPRECATED` 로 일괄 갱신.
- Service 로직(`MetaService#publish`)에서도 동일한 처리를 명시적으로 수행 (이중 안전망).

### 3-4. PageMeta JSON 본문 예시
```json
{
  "id": "itg-ticket-v1-2",
  "title": "ITSM 티켓 관리",
  "systemType": "ITSM",
  "packageType": "PACKAGE",
  "groupId": "itg-ticket",
  "majorVersion": 1,
  "minorVersion": 2,
  "metaStatus": "PUBLISHED",
  "api": "/api/tickets",
  "grid": { "columns": [ /* … */ ] },
  "form": { "layout": "two-column", "fields": [ /* … */ ] },
  "actions": [ /* … */ ]
}
```

## 4. 동적 렌더링 흐름

```
[사용자 라우팅]  /itsm/ticket
      │
      ▼
<DynamicPage group-id="itg-ticket" />
      │
      ├─(1) usePageMeta('itg-ticket')
      │       └─ GET /api/meta/active/{groupId}
      │           ApiResponse<PageMetaResponse>  (PUBLISHED 최신만)
      │
      ├─(2) useGridColumns(meta.grid)
      │       └─ rows>1000 || 인라인 편집 || export → AG Grid
      │          else                                → shadcn DataTable
      │
      └─(3) useFormSchema(meta.form)
              └─ Zod schema 생성 → VeeValidate toTypedSchema 바인딩

[데이터 로딩]
  GET  meta.api  → 그리드 데이터
  POST meta.api  → 폼 submit
  PATCH meta.api/{id} → 인라인 편집 저장
```

DRAFT 메타는 절대 `usePageMeta` 가 받아오지 않는다. 버전 관리 화면(별도 `/system/meta`)에서만 모든 상태를 조회한다.

## 5. 필드 타입 매핑

| 백엔드(JPA/Swagger) | PageMeta FieldType | shadcn/vue 컴포넌트 |
|---|---|---|
| `String` | `text` | `Input` |
| `@Lob String` | `textarea` | `Textarea` |
| `Int`·`Long`·`BigDecimal` | `number` | `Input(type=number)` |
| `LocalDate`·`LocalDateTime` | `date` / `date-range` | `DatePicker` / `DateRangePicker` |
| `Enum` | `select` (options=EnumValues) | `SelectField` |
| `Boolean` | `checkbox` | `CheckboxField` |
| `@ManyToOne User` | `user-picker` | `UserPicker` |
| `MultipartFile` | `file` | `FileUpload` |
| (도메인) Status | `status` | `StatusBadge` |
| (도메인) Priority | `priority` | `PriorityBadge` |

검증:
- `@NotNull`·`@NotBlank` → `required: true`
- `@Column(length=N)` → `maxLength: N`
- `@Size(min,max)`·`@Pattern` → Zod 스키마에 그대로 반영

## 6. 그리드 렌더러 선택 규칙

```
rows <= 1000              → shadcn DataTable
rows >  1000              → AG Grid Vue3
인라인 편집 (행 수 무관)  → AG Grid Vue3
엑셀 export (행 수 무관)  → AG Grid Vue3
```

## 7. API 설계

| 메서드 | 경로 | 목적 |
|---|---|---|
| GET | `/api/meta/active/{groupId}` | 화면 노출용 — PUBLISHED 최신 1건 |
| GET | `/api/meta/group/{groupId}/versions` | 그룹 전체 버전 이력 (DRAFT 포함) |
| GET | `/api/meta/{metaId}` | 특정 버전 단건 (이력 복원용) |
| PATCH | `/api/meta/{metaId}/publish` | DRAFT → PUBLISHED (+ 기존 PUBLISHED → DEPRECATED) |
| PATCH | `/api/meta/{metaId}/archive` | → ARCHIVED |
| POST | `/api/meta/{metaId}/copy` | 신규 DRAFT 생성 (minorVersion+1) |
| GET | `/api/meta/system/{systemType}/active` | 모듈별 배포 메타 목록 |
| GET | `/api/meta/package/{packageType}` | 패키지 구분별 목록 |

응답은 `ApiResponse<T>` 공통 래퍼.

## 8. 인증·인가

- Spring Security + JWT (Bearer). `SecurityConfig` 에서 `/api/meta/active/**` 외 모든 경로는 인증 요구.
- 메타 작성·배포(`/publish`·`/archive`·`/copy`)는 관리자 권한 필요.
- 프런트는 VueUse `useFetch` 의 `onRequest` 훅에서 `Authorization: Bearer …` 헤더를 자동 주입.

## 9. 트랜잭션·동시성

- 트랜잭션 경계는 `@Service` 메서드에서만 설정 (`@Transactional`). Controller·Repository 에 트랜잭션 어노테이션 금지.
- `publish` 시 동일 `groupId` 의 이전 `PUBLISHED` 갱신 + 신규 PUBLISHED 전환을 같은 트랜잭션으로 묶는다 (DB 트리거 + Service 로직 병행).
- 동시 publish 경쟁은 `UNIQUE (group_id, major_version, minor_version)` 와 `meta_status` 조건 갱신으로 마지막 커밋만 살아남도록 처리. 필요 시 비관적 락(`SELECT ... FOR UPDATE`) 추가.
- Virtual Thread 활성화(`spring.threads.virtual.enabled=true`) — I/O 바운드 워크로드 처리량 확보.

## 10. AI 메타 자동 생성 파이프라인

```
1. Spring Boot @Entity 또는 /v3/api-docs(.json|.yaml) 추출
2. Claude Code 에 "이 엔티티/DTO로 itg-{name} PageMeta 만들어줘.
                   systemType:ITSM, packageType:PACKAGE,
                   groupId:itg-{name}, major:1, minor:1, status:DRAFT"
3. 산출물:
   - PageMeta JSON
   - page_meta INSERT SQL (필수 컬럼 전부 포함)
   - Vue Router 등록 코드 (groupId 기반)
   - (선택) OpenAPI → TypeScript 타입
4. INSERT(DRAFT) → 검토 → PATCH /publish → 화면 자동 노출
5. 변경 필요 시 POST /copy → DRAFT 수정 → /publish
```

## 11. 로컬 인프라

- `docker-compose.yml` 으로 `postgres:16` + `dpage/pgadmin4` 구동.
- 컨테이너명 `itg-postgres` · `itg-pgadmin`, 볼륨 `itg-pgdata`.
- 초기화 스크립트는 `sql/init/` 마운트 → 컨테이너 최초 기동 시 `01_schema.sql` 자동 실행.
- Spring Boot `application-local.yml` 은 `localhost:5432`·`itgdb`·`itg/itg1234`, `ddl-auto: validate`, `format_sql: true`, Virtual Thread 활성화, SpringDoc 활성화.

## 12. 관측·문서

- Swagger UI: `/swagger-ui.html` — 모든 컨트롤러는 `@Tag` + `@Operation` + `@Schema` 어노테이션 필수. `@Schema(example)` 에 민감정보 금지.
- OpenAPI JSON/YAML: `/v3/api-docs(.yaml)` 로 추출 후 Word·Confluence 문서화에 활용.
- (예정) SonarQube 연동 — Gradle `sonar` 태스크 + CI 게이트.

## 13. 마이그레이션 메모 (Low-code → No-code)

- React → Vue 3 / Antd → shadcn/vue·Tailwind v4 / Webpack → Vite / MongoDB → PostgreSQL / Kotlin → Java 21.
- Antd · MongoDB 의존 코드 신규 작성 금지. 기존 화면은 점진적으로 메타 + DynamicPage 로 대체.
- 메타 모델은 처음부터 `systemType`·`packageType`·버전 그룹을 강제하므로 기존 화면을 그대로 옮기지 않고, 메타 단위로 재정의한다.
