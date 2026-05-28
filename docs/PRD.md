# PRD — Polestar10 ITG v2 (POLESTAR-ITSM)

> Product Requirements Document · Owner: ITG Dev Team (NKIA) · Version: v2.0.0-draft

## 1. 문제 정의

기존 Polestar10 ITG는 React + Ant Design + Kotlin/Spring + MongoDB 기반의 **Low-code** 플랫폼이다. 다음 한계가 누적되어 신규 구축이 결정되었다.

- **동적 폼 설계 한계**: Antd `Form` 의 `shouldUpdate` 래퍼와 동기 `rules` 구조 때문에 메타 기반 동적 폼 변형이 까다롭다.
- **유지보수 부담**: React `useEffect` 의존성 배열·수동 `useMemo` 관리가 팀 신규 인력에게 진입 장벽이 된다.
- **언어 분기**: Kotlin 백엔드는 팀 내 학습 곡선이 분산되어 있어, Java 21 LTS 표준으로 통일이 필요하다.
- **DB 정합성**: MongoDB 의 스키마리스 특성이 ITSM 워크플로우·ITAM 자산원장의 관계형 정합성에 맞지 않는다.
- **목표 격상**: Low-code(일부 자동화) → **No-code**(AI 에이전트가 메타 자동 생성, 신규 화면에 Vue 파일 작성 없음).

## 2. 비전·목표

> "JSON 메타 한 건만 추가하면 폼·그리드·목록·상세가 자동 생성된다."

- 신규 화면 추가 시 Vue 파일을 작성하지 않는다. `page_meta` DB 레코드만 등록한다.
- 동일 페이지의 여러 버전을 안전하게 운영한다. `DRAFT` 로 작업하고 `PUBLISHED` 로 배포하며, 구버전은 자동 `DEPRECATED` 처리된다.
- 본사 기본 메타(`PACKAGE`)와 고객사 커스터마이징(`CUSTOM`)을 분리하여 업그레이드 기준선을 보존한다.
- AI 에이전트(Claude Code)가 Spring Boot `@Entity` 또는 Swagger DTO 를 분석해 메타·SQL·Vue Router 등록 코드를 자동 생성한다.

## 3. 사용자·이해관계자

| 역할 | 사용 시나리오 |
|------|--------------|
| NKIA 개발팀 | `PACKAGE` 메타 작성·배포, 신규 모듈(ITSM·ITAM·PMS) 확장 |
| 현장 컨설턴트 | 고객사 요구사항 반영 → 기존 `PACKAGE` 복사 → `CUSTOM` 으로 변형 |
| 고객사 IT 관리자 | 화면 운영, 워크플로우 양식 버전 업, 자산원장 메타 관리 |
| 최종 사용자 | 자동 생성된 화면에서 티켓 등록·자산 조회·프로젝트 관리 수행 |

## 4. 모듈 범위 (v2.0.0)

| 모듈 (`systemType`) | 범위 |
|---|---|
| `ITSM` | 티켓·변경·문제·SLA·워크플로우 양식 버전 관리 |
| `ITAM` | 자산원장·라이선스·계약·이력(자산 등록 당시 메타 버전 보존) |
| `PMS` | 프로젝트·태스크·일정 |
| `COMMON` | 코드 관리·공지·첨부파일 |
| `SYSTEM` | 사용자·권한·메뉴·설정 |

## 5. 핵심 요구사항

### 5-1. 메타 모델 (필수)
- 모든 `PageMeta` 는 `systemType`, `packageType`, `groupId`, `majorVersion`, `minorVersion`, `metaStatus` 를 반드시 포함한다.
- `id` 는 `{groupId}-v{major}-{minor}` 패턴을 권장한다 (예: `itg-ticket-v1-2`).
- 본문 `meta_json` 에는 `api`, `grid`, `form`, `detail`(선택), `actions` 구조를 담는다.

### 5-2. 버전 라우팅 (필수)
- 화면 노출은 동일 `groupId` 내 `metaStatus = 'PUBLISHED'` 중 `(major, minor)` 가 가장 높은 단 하나만 노출.
- `PUBLISHED` 가 없으면 화면 미노출 (`DRAFT` 는 절대 노출 금지).
- 새 버전 `PUBLISHED` 전환 시 기존 `PUBLISHED` 는 자동 `DEPRECATED` 처리 (DB 트리거 + Service 로직 병행).
- 버전 복사 시 복사본은 원본 상태와 무관하게 항상 `DRAFT` 로 시작, `minorVersion` 은 `+1`.

### 5-3. 동적 렌더링 (필수)
- `<DynamicPage group-id="..." />` 가 `/api/meta/active/{groupId}` 를 호출해 메타를 받아 화면을 구성한다.
- `DynamicForm` 은 메타의 `form.fields` 를 shadcn/vue 컴포넌트에 매핑하고 VeeValidate + Zod 로 검증한다.
- `DynamicGrid` 는 행 수·인라인 편집·엑셀 export 요건에 따라 AG Grid 또는 shadcn DataTable 을 자동 선택한다.

### 5-4. AI 메타 자동 생성 (필수)
- Claude Code 는 `@Entity` / Swagger DTO 를 읽어 PageMeta JSON, `page_meta` INSERT SQL, Vue Router 등록 코드를 생성한다.
- 필드 타입 매핑은 ARCHITECTURE 문서의 변환표를 따른다.
- 누락된 필수 메타 값(`systemType` 등)이 있으면 생성을 거부한다.

### 5-5. 보안·민감정보 (필수)
- Swagger `@Schema(example)` 와 테스트 코드에 실제 IP·이메일·서버명·사번·시리얼·계약번호 등 운영 데이터 금지.
- 가상 샘플 값(`"샘플-"`, `"SAMPLE-"`, `example.com`, RFC 5737 `192.0.2.x`)만 사용한다.

## 6. 비기능 요구사항

| 항목 | 목표 |
|------|------|
| Service 단위 테스트 커버리지 | 70% 이상 |
| 중복 코드 비율 (SonarQube) | 3% 이하 |
| 보안 취약점 | Blocker·Critical 0건 |
| 하드코딩 시크릿 | 0건 |
| 그리드 렌더링 | 1000행 초과 시 AG Grid 가상 스크롤로 60fps 유지 |
| API 응답 시간 | 화면 노출용 메타 조회 p95 < 200ms |

## 7. 성공 지표

- 신규 화면 추가 평균 소요 시간: Vue 코드 작성 0줄, 메타 등록 + 검토 + publish 합계 1시간 이내.
- `PACKAGE` ↔ `CUSTOM` 분리율 100% (고객사 커스터마이징이 본사 기준선을 오염시키지 않음).
- `DRAFT` 메타가 사용자 화면에 노출되는 사고 0건.
- 워크플로우 양식 버전 업 시 기존 접수 건의 화면 복원 가능 (자산 이력도 동일).

## 8. 의도적 비포함 (Out of Scope)

- 다국어(i18n) — v2.1 이후 검토.
- 모바일 전용 앱 — 반응형 웹으로 대응.
- 외부 IDP(SAML/OIDC) 연동 — JWT 자체 인증으로 v2 시작, v2.1 에 검토.
- 멀티 테넌트 — 단일 인스턴스 가정. 멀티 인스턴스 분리 운영으로 대응.
- 메타 시각 편집기(GUI Designer) — AI 에이전트가 생성한 메타를 JSON 으로 검토·승인하는 방식으로 시작.

## 9. 마일스톤 (계획)

| 단계 | 산출물 |
|------|--------|
| M1 | `page_meta` 스키마 + 트리거/뷰, MetaService(`publish`/`copy`/`getActive`), Swagger 노출 |
| M2 | `DynamicPage`/`DynamicForm`/`DynamicGrid`, 기본 필드 매핑, AG Grid·shadcn DataTable 분기 |
| M3 | ITSM 티켓 모듈 메타 1세트(`itg-ticket`) — End-to-end 검증 |
| M4 | ITAM 자산원장 메타 + 자산 이력 복원 시나리오 검증 |
| M5 | Claude Code 자동 메타 생성 파이프라인 안정화 |
| M6 | SonarQube CI 게이트 연동 |
