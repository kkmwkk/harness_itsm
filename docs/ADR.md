# Architecture Decision Records — Polestar10 ITG v2

## 철학

No-code 우선. 신규 화면을 만들 때 코드를 새로 쓰지 않는다. 모든 화면은 단일 메타 모델(`page_meta`) 하나에서 동적으로 생성되어야 한다. 모델·DB·API 설계는 그 전제를 깨지 않는 한도에서만 채택한다.

기존 ITG(React + Antd + Kotlin + MongoDB) 의 Low-code 자산은 유산으로 인정하되, 신규 코드는 신규 스택 규칙을 따른다. 모호한 상황에서는 "메타에 들어가는가, 코드에 들어가는가" 를 먼저 묻는다 — 답이 "메타" 면 코드를 늘리지 않는다.

---

### ADR-001: 프론트엔드 Vue 3 + Vite + shadcn/vue + Tailwind v4 채택

**결정**: 프런트엔드 스택을 React + Antd + Webpack 에서 Vue 3 (Composition API + `<script setup lang="ts">`) + Vite 6 + shadcn/vue + Tailwind CSS v4 로 전면 교체한다.

**이유**:
- Antd `Form` 의 `shouldUpdate` 래퍼와 동기 `rules` 가 메타 기반 동적 폼 변형의 자유도를 떨어뜨렸다. VeeValidate 4 + Zod (`toTypedSchema`) 는 메타에서 생성한 스키마를 그대로 묶기 좋다.
- React `useEffect` 의존성 배열·수동 `useMemo` 의 진입 장벽이 신규 인력에게 반복적으로 발생했다. Vue 의 반응형 시스템은 동일 유즈 케이스를 더 적은 보일러플레이트로 표현한다.
- shadcn/vue 는 "소스 코드 소유" 모델이라 디자인 시스템을 우리가 자유롭게 가지치기할 수 있고, Antd 처럼 거대한 라이브러리 업그레이드에 묶이지 않는다.
- Vite 의 콜드 스타트·HMR 이 Webpack 대비 빠르다.

**트레이드오프**:
- React 자산(컴포넌트·훅)을 그대로 옮기지 못한다 — 메타 단위로 재설계해야 한다. 신규 모델이 No-code 를 전제로 하므로 어차피 1:1 이식이 의미 없으므로 수용 가능.
- shadcn/vue 는 컴포넌트를 직접 소유하므로 보안 패치·업데이트를 우리가 관리해야 한다.

---

### ADR-002: 백엔드 Java 21 + Spring Boot 3 (Kotlin 신규 작성 중단)

**결정**: 백엔드 언어를 Kotlin 에서 Java 21 LTS 로 통일한다. 신규 코드는 Java 21 Record · Sealed Class · Switch Expression · Virtual Thread 를 활용한다.

**이유**:
- 팀 표준 언어 통일로 학습 비용·코드 리뷰 일관성·장기 유지보수 부담을 줄인다.
- Java 21 Record DTO 가 Lombok 의존 없이도 DTO 보일러플레이트를 충분히 줄여준다.
- Virtual Thread 로 I/O 바운드 워크로드 처리량을 별도 코드 변경 없이 확보 가능.

**트레이드오프**:
- Kotlin 의 `data class`·확장 함수·null 안전성 같은 편의를 일부 잃는다. → 코드 컨벤션(Record + `Optional` 활용)으로 보완.
- 기존 Kotlin 자산은 운영 잔존. 신규 작성 금지 규칙으로 점진 대체.

---

### ADR-003: DB 를 MongoDB → PostgreSQL 16 로 교체, 메타는 JSONB 로 보관

**결정**: 데이터 저장소를 PostgreSQL 16 으로 교체한다. `page_meta.meta_json` 은 `JSONB` 컬럼으로 보관하되, 필수 분류 축(`system_type`, `package_type`, `group_id`, `major_version`, `minor_version`, `meta_status`)은 별도 컬럼으로 승격하여 인덱스·제약 조건을 건다.

**이유**:
- ITSM 워크플로우·ITAM 자산원장은 관계형 정합성(외래키·트랜잭션·UNIQUE 제약)이 본질이다. MongoDB 의 스키마리스 자유도는 이 도메인에서 자산이 아니라 부채로 작용했다.
- 메타 본문은 변형이 크므로 `JSONB` 로 유연성을 확보하고, 검색 성능은 `GIN(meta_json)` 인덱스로 보충.
- 필수 축을 컬럼으로 분리해 CHECK 제약·UNIQUE 제약·뷰(`page_meta_active`)·트리거(자동 DEPRECATE) 같은 DB 레벨 안전망을 깐다.

**트레이드오프**:
- 기존 MongoDB 데이터의 마이그레이션 비용. → 신규 메타 모델은 처음부터 재정의하므로 1:1 이관 부담이 적다.
- JSONB 변경 시 마이그레이션 없이 구조 변경이 가능한 반면, 잘못된 변경의 안전망이 약하다. → 메타 변경은 반드시 새 `minorVersion` 으로 복사 → 검토 → publish 흐름을 강제하여 보완.

---

### ADR-004: No-code — 모든 화면은 `page_meta` 한 건에서 동적 생성

**결정**: 신규 화면은 Vue 파일을 작성하지 않는다. `<DynamicPage group-id="..." />` 하나가 `usePageMeta` 로 메타를 받아 `DynamicForm` + `DynamicGrid` 로 렌더링한다. Vue Router 도 `groupId` 기반으로 등록한다.

**이유**:
- "JSON 메타 한 건 추가 = 화면 한 개" 등식이 No-code 의 핵심 약속. 화면별 Vue 파일을 허용하는 순간 등식이 깨지고, 코드 리뷰·유지보수 부담이 다시 누적된다.
- Claude Code 같은 AI 에이전트가 `@Entity` / Swagger DTO 만으로 메타·INSERT SQL·Router 코드를 일관되게 생성할 수 있어야 한다.

**트레이드오프**:
- 예외적으로 복잡한 화면(특수 차트, 캔버스 인터랙션)은 메타로 표현이 어렵다. → 그런 경우는 동적 컴포넌트의 `slot` 또는 메타의 `customComponent` 필드로 명시적으로 표현하고, 그 화면은 별도 ADR 로 예외 기록한다. 기본 가정은 메타로 표현되지 않는다는 것은 메타 모델이 부족하다는 신호로 해석한다.

---

### ADR-005: 메타 분류 — `systemType` + `packageType` 필수

**결정**: 모든 `PageMeta` 는 `systemType (ITSM|ITAM|PMS|COMMON|SYSTEM)` 과 `packageType (PACKAGE|CUSTOM)` 을 반드시 포함한다. 누락 시 메타 생성·INSERT 거부. Enum 사용 강제 (String 하드코딩 금지).

**이유**:
- `systemType` 으로 모듈 단위 메타 추출·배포 관리가 가능해진다 (예: ITSM 전체 PACKAGE 메타 일괄 백업).
- `packageType` 분리로 본사 기준선(`PACKAGE`)이 고객사 변형(`CUSTOM`)에 오염되지 않는다. 업그레이드 시 `PACKAGE` 만 교체하면 되고, 고객사 변경분은 별도 관리.
- DB CHECK 제약 + 백엔드 Enum + 프런트 타입 가드 3중으로 강제하면 분류 누락 사고가 원천 차단된다.

**트레이드오프**:
- 분류 축을 추가하면 후방 호환성이 깨지는 마이그레이션이 필요. → 신규 구축이므로 매몰비용 없음.

---

### ADR-006: 버전 그룹 — `groupId` + `major/minor` + `metaStatus` 로 화면 라우팅

**결정**: 동일 페이지 계열을 `group_id` 로 묶고, `(major_version, minor_version)` 로 버전을 식별하며, `meta_status` 로 화면 노출 여부를 결정한다. 화면에는 `meta_status = 'PUBLISHED'` 중 `(major, minor)` 가 가장 높은 단 하나만 노출한다. 조회는 `page_meta_active` 뷰를 통한다.

규칙:
- `DRAFT` 메타는 사용자 화면에 절대 노출하지 않는다.
- 새 버전을 `PUBLISHED` 로 전환하면 동일 `group_id` 의 기존 `PUBLISHED` 는 자동 `DEPRECATED` 로 처리한다 (DB 트리거 + Service 로직 병행).
- 버전 복사 시 복사본은 원본 상태와 무관하게 항상 `DRAFT` 로 시작, `minorVersion` 은 `+1`.
- `id` 권장 패턴 `{groupId}-v{major}-{minor}`.

**이유**:
- ITSM 워크플로우 양식을 안전하게 버전 업하기 위함. 운영 중인 양식을 `DRAFT` 사본에서 수정·검토 후 `publish` 하면 사용자 화면이 원자적으로 전환되고, 과거 접수 건은 해당 버전 메타 id 를 참조하여 화면을 그대로 복원할 수 있다.
- ITAM 자산원장도 동일. 자산 등록 시점의 메타 버전을 자산 레코드에 저장해두면 양식 변경이 과거 데이터 표시에 영향을 주지 않는다.
- 안전망 이중화: DB 트리거(`fn_auto_deprecate_on_publish`) + Service 로직(`MetaService#publish`) 두 곳에서 동일 처리.

**트레이드오프**:
- 메타 ID 관리·버전 관리 UI 의 복잡도 증가. → 시스템 관리 모듈(`SYSTEM`)에서 별도 화면으로 흡수.
- 한 화면이라도 `(group_id, major, minor)` 가 단일 키이므로 동일 버전 중복 등록 불가. UNIQUE 제약으로 강제.

---

### ADR-007: 그리드 렌더러 이원화 — shadcn DataTable + AG Grid Vue3

**결정**: 그리드는 메타 정보에 따라 자동 선택한다.
- `rows <= 1000` → shadcn DataTable (TanStack Table 기반)
- `rows > 1000` 또는 인라인 편집·엑셀 export → AG Grid Vue3 32.x

**이유**:
- 대부분의 관리 화면(코드 관리·공지 등)은 1000행 이하라 shadcn DataTable 로 충분하다 — 디자인 일관성, 번들 크기 작음.
- 자산원장·티켓 목록처럼 대용량 + 인라인 편집 + 엑셀 export 가 필요한 화면만 AG Grid 의 가상 스크롤·셀 에디터·export 기능에 의존.

**트레이드오프**:
- 두 라이브러리 학습·운영 부담. → `useGridColumns` 컴포저블이 메타에서 컬럼 정의를 만들고 라이브러리 선택까지 책임지므로 화면 레벨에서는 단일 인터페이스.
- AG Grid Enterprise 기능은 사용하지 않는다 (라이선스 부담). Community 기능만 사용.

---

### ADR-008: 인증은 Spring Security + JWT (자체 발급)

**결정**: v2 시작은 Spring Security + JWT (Bearer) 자체 인증. 외부 IDP(SAML/OIDC) 연동은 v2.1 이후 검토.

**이유**:
- 초기 도입 비용이 가장 낮고, 단일 인스턴스 운영을 가정하면 충분하다.
- 프런트는 VueUse `useFetch` 의 `onRequest` 훅에서 토큰을 자동 주입하여 코드 일관성 유지.

**트레이드오프**:
- 엔터프라이즈 고객의 SSO 요건이 들어오면 추가 작업 필요. → 인증 추상화를 처음부터 어댑터(`AuthenticationProvider`) 단위로 분리하여 교체 비용을 낮춘다.

---

### ADR-009: API 응답 공통 래퍼 `ApiResponse<T>` 강제

**결정**: 모든 REST 응답은 `ApiResponse<T>(success, data, message, errorCode)` 래퍼로 감싼다. Controller 는 `ResponseEntity<ApiResponse<T>>` 반환.

**이유**:
- 프런트가 응답 처리 코드를 한 가지 모양으로 가져갈 수 있다.
- 에러 코드 분기 일관성. `GlobalExceptionHandler` 가 도메인 예외(`ITGException`) 를 `ApiResponse.fail(code, message)` 로 변환.

**트레이드오프**:
- 외부 시스템 연동 시 래퍼를 벗기는 어댑터가 필요. → API Gateway 레이어에서 처리.

---

### ADR-010: 로컬 인프라는 Docker Desktop + PostgreSQL/pgAdmin 컨테이너

**결정**: 로컬 개발 DB 는 `docker-compose.yml` 의 `postgres:16` 컨테이너로 통일한다. 호스트 직접 설치 금지. pgAdmin 도 컨테이너로 제공.

**이유**:
- 팀 환경 표준화. "내 PC 에서는 됐는데" 류 이슈 차단.
- 초기화 SQL(`sql/init/01_schema.sql`)이 컨테이너 최초 기동 시 자동 실행되어 신규 인력 온보딩이 단순해진다.

**트레이드오프**:
- Docker Desktop 라이선스·리소스 부담. → 사내 라이선스 정책 기준 운영.

---

### ADR-011: Swagger `@Schema(example)` 와 테스트 데이터의 민감정보 금지

**결정**: Swagger 어노테이션과 테스트 코드에 실제 운영 데이터(사용자명·사번·이메일·내부망 IP·서버명·시리얼·계약번호 등)를 절대 사용하지 않는다. 대신 가상 샘플(`"샘플-"`, `"SAMPLE-"`, `example.com`, RFC 5737 `192.0.2.x`)을 사용한다.

**이유**:
- Swagger UI 와 OpenAPI 산출물은 외부 공유 가능성이 높아 운영 데이터 노출 위험이 크다.
- 테스트 데이터에 운영 데이터를 섞으면 git 이력에 남아 회수 불가능. 사고 발생 시 영향 범위가 크다.

**트레이드오프**: 없음. PR 리뷰 블로킹 항목으로 명시.

---

### ADR-012: TestFixture 클래스로 목 데이터 중앙 관리

**결정**: 테스트용 도메인 객체 생성은 `MetaFixture`·`TicketFixture` 같은 `final` 픽스처 클래스에 모은다. 테스트 메서드 내에서 빌더를 직접 호출해 동일 데이터를 반복 생성하지 않는다.

**이유**:
- 도메인 필드 추가 시 픽스처 한 곳만 수정하면 전체 테스트가 따라온다.
- 의미 있는 시나리오(`draftMeta`, `publishedMeta`)를 메서드명으로 표현하여 가독성 향상.

**트레이드오프**: 픽스처 자체가 비대해질 수 있음 → 모듈별로 분리(`MetaFixture` / `TicketFixture` / `AssetFixture` 등).

---

### ADR-013: TDD — JUnit 5 테스트 먼저, 통과시키는 구현 다음

**결정**: 신규 Service·Controller 메서드는 JUnit 5 + Mockito 테스트를 먼저 작성하고, 테스트가 통과하는 최소 구현을 작성한다. `given / when / then` 구조 준수, 테스트 메서드명은 한글 서술형 허용.

**이유**:
- 메타 모델 규칙(`DRAFT` 노출 금지·자동 DEPRECATE 등)이 테스트로 박혀 있어야 회귀를 막을 수 있다.
- 단위 테스트가 도메인 의도의 살아 있는 문서 역할을 한다.

**트레이드오프**: 초기 작성 부담 → SonarQube 게이트(Service 70% 커버리지)와 묶어 강제.

---

### ADR-014: AI 메타 자동 생성 워크플로우 — CLI + dry-run + Claude Code 다듬기

**결정**: 신규 모듈 PageMeta 생성을 **3단계 파이프라인**으로 정형화한다.
1. **Python CLI** (`scripts/generate_meta.py`) — OpenAPI 사양 + DTO 이름에서 PageMeta DRAFT 골격·INSERT SQL 생성 (필드 타입 매핑은 ARCHITECTURE §5).
2. **Spring Boot dry-run** (`POST /api/meta/dry-run`, `scripts/validate_meta.sh`) — DB 변경 없이 필수 3축·id 패턴·grid/form/actions 형식 검증.
3. **Claude Code 다듬기** — 라벨·options.label 을 사용자 친화 한글로 보강 (보호 규칙 프롬프트 동반).

생성된 메타는 항상 `DRAFT` 로 시작하며, 사람 검토 후 `PATCH /publish` 로만 배포한다. CLI → DB 자동 INSERT 흐름은 의도적으로 만들지 않는다. 워크플로우 전체는 `docs/META_GENERATION_GUIDE.md` 에 문서화한다.

**이유**:
- 완전 자동화는 도메인 휴리스틱 한계로 위험하다. 자산-도메인-특수 컬럼(예: `pageMetaIdAtRegistration`)·그리드 width·Vue Router 등록은 generator 가 자동 인식하지 못한다.
- dry-run 검증을 파이프라인 중간에 끼워 형식 결함을 배포 전에 차단한다.
- DRAFT → 검토 → publish 흐름(ADR-006)을 강제하여 잘못된 메타가 화면에 노출되는 사고를 막는다.

**트레이드오프**:
- 라벨·옵션·컬럼 width 같은 사용자-친화 다듬기는 여전히 사람·AI 협업이 필요하다 (완전 무인 생성 아님).
- 백엔드 DB 스키마 결함(hotfix `8d636a2`)·SecurityConfig permitAll 누락(hotfix `b5f9150`) 같은 결함은 메타 파이프라인 범위 밖이라 통합·e2e 단계에서 별도로 잡아야 한다.

---

### ADR-015: 워크플로우 엔진 — 자체 단계 모델(MVP), BPMN 엔진은 별도 deferred

**결정**: v2.1 의 워크플로우는 자체 구현 단계 엔진(MVP). `WorkflowDefinition.steps` JSONB 단계 배열 + `WorkflowInstance` + `WorkflowInstanceStep` 의 3-테이블 모델로 시작한다. BPMN/Camunda 엔진 통합은 [[adr-017-bpmn-deferred]] 에서 별도 다룬다.

단계 모델:
- `WorkflowDefinition.steps[]` 의 각 단계는 `index`·`name`·`assignee_role_code`·`sla_minutes`·`allowed_actions[]` 5 필드.
- `Ticket` 생성 시 `TicketRequestType.default_workflow_id` 로 `WorkflowInstance` 자동 생성, `current_step_index=0` 으로 첫 단계 진입.
- 액션(`APPROVE`/`REJECT`/`FORWARD`/`COMPLETE`/`CONFIRM`/`REOPEN`) 은 `assigned_to_role_code` 의 사용자만 실행 가능. `@PreAuthorize` + 도메인 가드 이중.
- 마지막 단계 완료 시 `WorkflowInstance.status='COMPLETED'` + `Ticket.status='CLOSED'`.
- CLOSED 가드 (v2.0 ticket 패턴 재사용) — 모든 액션·수정 거부.

**이유**:
- BPMN 엔진(Camunda 7) 통합은 별도 솔루션 수준 작업(1~2개월). v2.1 의 핵심은 **요청 유형별 워크플로우가 동작하는 것** 이지 엔진 자체가 아니다.
- 자체 모델은 JSONB 한 컬럼 + 2 테이블로 표현 가능. 단계 정의·실행·이력 모두 SQL 로 디버깅 가능.
- 메타(`page_meta`) 와 결이 같다 — `workflow_definition` 도 버전 관리·DRAFT/PUBLISHED 흐름을 따를 수 있다.

**트레이드오프**:
- 병렬 분기·서브프로세스·이벤트 기반 트리거 등 BPMN 의 고급 기능 없음. v2.1 은 선형 단계만.
- 단계 정의 UI 가 한정적 (M9·M10 의 No-code 편집기에서 다룸).
- 추후 BPMN 으로 확장 시 마이그레이션 필요 — 그러나 단계 모델이 단순해서 변환 가능.

---

### ADR-016: No-code 편집기 단계적 도입 — 폼 UI → 드래그앤드롭 → WYSIWYG

**결정**: 비개발자가 PageMeta 를 GUI 로 편집하는 도구를 **3단계로 점진 도입**한다.

| 단계 | 마일스톤 | 산출물 |
|---|---|---|
| 1단계 | M9 | `system/MetaEditorPage.vue` — 폼 UI 로 필드 추가·삭제·라벨·옵션 편집. JSON 노출 없음. dry-run + publish 흐름 통합 |
| 2단계 | M10 | 드래그앤드롭 — 필드 순서·`span`·그리드 컬럼 순서·width 변경. SortableJS/VueDraggablePlus |
| 3단계 (Stretch) | M11 | WYSIWYG — 실 미리보기에서 직접 클릭·편집. Builder.io / GrapeJS 수준 PoC 후 결정 |

각 단계의 산출물은 다음 단계의 토대. 1단계가 완성되면 비개발자가 라벨·옵션 한글화는 GUI 로 가능. 드래그·WYSIWYG 는 UX 향상 단계.

**이유**:
- v2.0 의 "메타 시각 편집기는 OoS" 결정이 사용자 핵심 요구를 놓쳤다. v2.1 에서 핵심 범위로 끌어올린다.
- 완전 WYSIWYG 은 별도 솔루션 수준 작업. 폼 UI 만으로도 비개발자 진입 장벽 대부분 해소.
- 모든 편집은 v2.0 의 메타 버전 그룹(ADR-006) 위에서 동작 — DRAFT 저장 → dry-run → publish → 자동 DEPRECATE.

**트레이드오프**:
- 폼 UI 만으로는 복잡한 레이아웃·중첩 그리드 표현이 어렵다 → 그런 케이스는 JSON 직접 편집 또는 M10 드래그.
- WYSIWYG(M11) 은 PoC 결과에 따라 보류·교체 가능 (예: 외부 도구 임베드).

---

### ADR-017: BPMN/Camunda 엔진 도입 — 별도 PoC 후 결정 (Deferred)

**결정**: v2.1 범위에서는 **자체 단계 엔진(ADR-015) 으로 시작**하고, BPMN/Camunda 엔진 도입은 별도 PoC 후 결정한다. 결정 시점: M8(워크플로우 MVP) 완료 후 사용자 요구·운영 부담을 평가.

**이유 — 도입 시 얻는 것**:
- 병렬 분기·서브프로세스·타이머 이벤트·메시지 이벤트 등 BPMN 표준 표현력.
- BPMN 모델러 도구 생태계 (Camunda Modeler 등).
- 엔진 안정성·테스트 베드 풍부.

**이유 — 보류하는 이유**:
- Camunda 7 통합 = 별도 DB schema·REST API·UI tools. 본 프로젝트 인프라 (Postgres·Spring Boot·Vue) 와 결이 다르다.
- 라이선스: Camunda 7 Community 는 OK 지만 8 은 SaaS·Self-Managed Enterprise 가 따로.
- 운영 부담: 엔진 모니터링·튜닝·버전 업그레이드.
- v2.1 의 자체 단계 모델로도 요청 유형·역할 라우팅·SLA·결재까지 표현 가능.

**도입 트리거 조건**:
- 자체 모델로 표현 불가능한 워크플로우 요구(병렬 분기·복잡 이벤트) 가 3건 이상 누적될 때.
- 운영 측의 BPMN 모델러 사용 요구가 명시될 때.
- 엔터프라이즈 고객 적용으로 운영 비용 정당화 가능할 때.

**트레이드오프**:
- 자체 모델로 시작하면 추후 마이그레이션 비용 발생. → 단계 정의가 단순해서 변환 가능 (선형 단계는 BPMN 의 sequence flow 와 직접 매핑).

**M8 후 1차 평가 (2026-05-30, phase 15 step 1 — `docs/BPMN_EVAL_NOTES.md`)**: 자체 모델의 표현력 천장(병렬 분기·타이머 자동 escalation·서브프로세스 3/3 표현 불가)을 코드 실측으로 확인했으나, 실 수요 0건으로 위 도입 트리거(3건 이상 누적·모델러 요구 명시)는 **미충족**. → **결론: 도입 보류 + 자체 모델 점진 확장**(SLA `findOverdue` 기반 스케줄 escalation·조건 분기 필드 추가). 병렬·서브프로세스 실 수요가 트리거에 도달하면 Camunda 7 PoC 실 착수. 자세한 측정·근거는 `docs/BPMN_EVAL_NOTES.md` 참조.

---

### ADR-018: 멀티 테넌트 — Deferred

**결정**: v2.1 은 **단일 테넌트** 운영을 가정한다. Multi-tenant (테이블별 `tenant_id` + row-level security) 는 별도 phase·별도 ADR 로 미룬다.

**이유**:
- 단일 인스턴스 운영이 핵심 사용 시나리오 (사내 ITSM/ITAM 솔루션).
- 멀티 테넌트는 모든 테이블에 `tenant_id` 추가·인덱스·쿼리 필터·JWT 클레임 등 침투 범위가 큼.
- 멀티 인스턴스 분리 운영(고객사별 별도 DB·앱)으로 대부분 시나리오 대응 가능.

**도입 트리거 조건**:
- SaaS 형태로 다수 고객사를 단일 인스턴스에서 운영해야 할 때.
- 인스턴스 분리 운영 비용이 멀티 테넌트 설계 비용을 초과할 때.

**트레이드오프**:
- 도입 시 모든 도메인 모델에 침투 → 초기 설계에 박혀있지 않으면 추후 마이그레이션이 매우 비싸다.
- 그러나 사용 시나리오가 단일 테넌트로 충분하면 매몰 비용 회피가 더 가치 있다.

---

### ADR-019: Playwright 시각 회귀 — 자동화된 e2e 의 첫 번째 형태

**결정**: v2.1 부터 **Playwright 기반 시각 회귀 e2e** 를 CI 게이트에 포함한다. 핵심 라우트의 스크린샷을 baseline 으로 저장하고, PR 마다 diff 를 검사한다.

대상 라우트(초기):
- `/`, `/login`, `/itsm`, `/itam`, `/itam/:id`, `/system/meta`, `/system/meta-editor`.

각 라우트 진입 → 페이지 로드 완료 대기 → 스크린샷 → 기존 baseline 과 픽셀 diff. 임계치 초과 시 PR 통과 차단.

**이유**:
- v2.0 의 자동 검증(JUnit·Vitest·cURL) 은 **실 브라우저 시각 결함을 못 잡았다** — phase 1 step 1 의 Tailwind `@source` 누락이 9 phase 가 끝날 때까지 발견 안 됨.
- 시각 회귀는 도메인 결함보다 catch 가 어렵지만, 스크린샷 diff 는 사람 검토 부담을 크게 줄인다.
- Playwright 는 Vue/SPA 친화적, headless·headed 양쪽 지원, GitHub Actions 통합 단순.

**트레이드오프**:
- baseline 관리 부담 — 의도된 UI 변경 시 baseline 갱신이 필요. PR 본문에 명시 강제.
- 시각 diff 는 false positive (폰트 렌더링 차이·OS 환경 차이) 발생 가능. CI 환경을 컨테이너로 고정해서 환경 차이 최소화.
- 도메인 결함(예: 등록 폼 submit 이 잘못된 endpoint 호출) 은 시각 회귀로 못 잡음 → 별도 시나리오 e2e (action chain) 도 추가 검토.

**최소 운영 규칙**:
- baseline 은 main 브랜치에 commit. PR 마다 diff.
- 시각 변경이 의도일 때만 `pnpm e2e --update-snapshots` 로 갱신 + PR 본문에 사유 명시.
- diff 1px 이내는 자동 허용. 초과는 사람 검토.

---

### ADR-020: UX 메시지 카탈로그 — raw 백엔드 토큰 노출 금지

**결정**: 사용자 화면에 노출되는 모든 메시지(빈 상태·에러·로딩) 는 `frontend/src/lib/ui-messages.ts` 의 카탈로그로 통일한다. 백엔드 `ApiResponse.errorCode` 는 분기·로깅용으로만 쓰고 **사용자 화면에 직접 표시하지 않는다**.

카탈로그 구조:
```
UI.empty.{grid, metaNotPublished, ...}
UI.error.{metaLoad, dataLoad, submit, network, auth, ...}
UI.loading.{meta, data, ...}
```

각 composable(`usePageMeta`·`usePageData`·`useDataMutation`)의 error 값은 `UI.error.*` 로 매핑된 한글 메시지만 반환. errorCode 는 `console.warn`/Sentry 로그 등 개발자 채널로만 흘려보낸다.

**이유**:
- v2.0 의 `META_NOT_PUBLISHED: 배포된 메타가 없습니다: itg-project` 같은 raw 메시지가 사용자에게 노출되는 사고 발생 (사용자 피드백).
- 백엔드 코드 변경 시 화면 메시지가 함께 바뀌는 결합도 회피.
- 한글화·다국어 도입(v2.2) 시 카탈로그 한 곳만 수정.

**트레이드오프**:
- 신규 에러 코드 추가 시 카탈로그도 함께 갱신 필요 — PR 리뷰 체크리스트에 포함.
- 매우 드물게 백엔드 errorCode 가 사용자에게 의미 있는 경우(예: "OTP_MISMATCH" 같은 인증 흐름) 는 예외적으로 노출 허용. 그래도 한글 라벨은 카탈로그를 거친다.
