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
