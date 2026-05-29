# 프로젝트: Polestar10 ITG v2 (POLESTAR-ITSM) — v2.1

**비개발자가 WYSIWYG 로 화면을 만들고, 그 화면이 도메인 모델(요청 유형·자산 분류·워크플로우·역할) 위에서 동작하는 No-code 플랫폼.** 메타 한 건이 화면 한 개를 정의한다 (`page_meta` JSONB + 버전 그룹). 신규 모듈 추가 시 Vue 파일을 새로 쓰지 않고 `page_meta` 레코드만 등록. ITSM·ITAM·PMS·COMMON·SYSTEM 다섯 모듈을 단일 메타 모델로 운영. 자세한 배경은 `docs/PRD.md`, 구조는 `docs/ARCHITECTURE.md`, 결정 이유는 `docs/ADR.md`, UI 토큰·컴포넌트 규칙은 `docs/UI_GUIDE.md` 참조.

**v2.0 (M1~M6) 완료** — 메타·동적 렌더링·ITSM 티켓·ITAM 자산·AI 메타 CLI·SonarQube CI.
**v2.1 (M7~M11) 진행 중** — 인증·사용자/부서/역할/권한/메뉴, 도메인 깊이(요청 유형·자산 분류·워크플로우 MVP), No-code 편집기(폼 UI → 드래그 → WYSIWYG), UX 베이스라인 + Playwright 시각 회귀.

## 기술 스택
- Frontend: Vue 3.5+ (Composition API + `<script setup lang="ts">` 전용) / Vite 6 / TypeScript 5 strict
- UI: shadcn/vue + Tailwind CSS v4 (CSS 변수 기반 테마)
- Grid: AG Grid Vue3 32.x (1000행 초과 또는 인라인 편집·엑셀 export), shadcn DataTable (그 외)
- 폼 검증: VeeValidate 4 + Zod (`toTypedSchema` 통합)
- 상태/유틸: Pinia 2 (`storeToRefs` 필수), VueUse 11 (`useFetch` 표준), Vue Router 4
- Backend: Spring Boot 3 + **Java 21** (Record · Virtual Thread 활용) / JPA + QueryDSL / Spring Security + JWT
- API 문서: SpringDoc OpenAPI (Swagger UI `/swagger-ui.html`)
- DB: PostgreSQL 16 — 로컬 Docker Desktop 컨테이너 (`localhost:5432`, DB `itgdb`)
- 빌드/테스트: Gradle 8 / JUnit 5 + Mockito / vue-tsc (CI 포함)

## 핵심 설계 사상 — 모든 메타·API·DB의 최우선 기준

모든 `PageMeta` 는 아래 세 축을 반드시 포함한다. 누락된 메타는 생성·INSERT 자체를 거부한다.

1. **systemType** — `ITSM | ITAM | PMS | COMMON | SYSTEM`. 모듈 단위 추출·관리용.
2. **packageType** — `PACKAGE`(본사 기본) 또는 `CUSTOM`(고객사 커스터마이징). 업그레이드 기준선과 현장 변경분을 분리한다.
3. **버전 그룹** — `groupId` + `majorVersion` + `minorVersion` + `metaStatus`. 동일 페이지 계열을 묶고, 화면에는 `PUBLISHED` 상태 중 가장 높은 버전 단 하나만 노출한다.

`metaStatus` 전이 규칙:
- `DRAFT` → 작성 중, 사용자 화면에 절대 노출 금지
- `PUBLISHED` → 배포 완료, 화면 노출 대상
- `DEPRECATED` → 구버전, 새 버전 `PUBLISHED` 전환 시 자동 처리 (DB 트리거 + Service 병행)
- `ARCHIVED` → 이력 보관용
- 버전 복사 시 원본 상태와 무관하게 복사본은 항상 `DRAFT` 로 시작
- 화면 노출 메타 조회는 `page_meta_active` 뷰 또는 동등 로직(버전 라우팅) 사용

## 아키텍처 규칙
- CRITICAL: Frontend → Backend → DB 의 단방향 호출. 외부 API 키·DB 접속 정보는 Spring Boot 서버 내부에서만 다룬다. 클라이언트 코드에서 DB·시크릿에 직접 접근 금지.
- CRITICAL: Controller 는 HTTP 변환 + Swagger 어노테이션만 담당. 비즈니스 로직과 트랜잭션 경계는 `@Service` 에서만. Repository 는 JPA 인터페이스 + QueryDSL 구현으로 분리.
- CRITICAL: 화면 노출용 메타 조회 API 는 반드시 버전 라우팅 로직을 거친다 — `groupId` 기준 `PUBLISHED` 최신 버전만 반환. `DRAFT` 메타를 화면에 직접 바인딩하는 코드 작성 금지.
- 패키지 구조: `com.nkia.itg.{common|meta|itsm|itam|pms|system}` — 모듈별 수평 분리.
- Frontend 구조: `components/ui/`(shadcn), `components/dynamic/`(DynamicPage·Form·Grid), `composables/use*`(재사용 로직), `stores/use*Store`(전역 상태), `types/meta.ts`(SystemType·PackageType·MetaStatus 등 메타 타입).
- 신규 화면 추가 절차: ① Claude Code 에 메타 생성 요청 → ② `page_meta` INSERT(`DRAFT`) → ③ 검토 후 `PATCH /api/meta/{id}/publish` → ④ `<DynamicPage group-id="..." />` 로 Vue Router 등록.
- 응답은 `ApiResponse<T>` 공통 래퍼 강제. `SystemType`·`PackageType`·`MetaStatus` 는 Enum 사용 (String 하드코딩 금지).
- 1000행 초과·인라인 편집·엑셀 export 가 필요한 화면은 AG Grid, 그 외는 shadcn DataTable.

## 절대 규칙 (예외 없음)
- Meta: `systemType`·`packageType`·`groupId`·`majorVersion`·`minorVersion`·`metaStatus` 누락 시 생성 거부. 복사본 `metaStatus` 는 항상 `DRAFT`. `PUBLISHED` 전환 시 동일 `groupId` 의 기존 `PUBLISHED` 는 자동 `DEPRECATED`.
- Frontend: `<script setup lang="ts">` 전용 (Options API 금지) · `any` 타입 금지 · API 통신은 VueUse `useFetch` · 검증 스키마는 Zod + `toTypedSchema` · 컴포넌트 PascalCase · composable `use` 접두사 · 운영 코드에 `console.log` 잔류 금지 · Antd / MongoDB 의존 신규 작성 금지.
- Backend: Java 21 전용 (Kotlin 신규 작성 금지) · 모든 Controller Swagger 어노테이션 필수 · `@Schema(example)` 에 실제 IP·이메일·서버명·사번·시리얼·계약번호 등 민감정보 절대 금지 (가상 샘플 — `"샘플-"`, `"SAMPLE-"`, `example.com`, RFC 5737 `192.0.2.x` 사용) · `ApiResponse<T>` 공통 래퍼 사용 · 트랜잭션 경계는 Service 레이어 · `SystemType`·`PackageType`·`MetaStatus` 는 Enum 사용.
- Infra: DB 접속은 `localhost:5432` (Docker) 기준 · `docker-compose.yml` 변경은 팀 공유 후.

## 개발 프로세스
- TDD: 새 기능 구현 시 JUnit 5 테스트 먼저, 통과시키는 구현은 그다음.
- 목 데이터는 `TestFixture` 클래스로 중앙 관리 (예: `MetaFixture`, `TicketFixture`). 테스트 코드에도 민감정보 금지.
- 커밋 메시지는 conventional commits 형식 (`feat:`, `fix:`, `refactor:`, `style:`, `docs:`, `chore:`, `test:`).
- 브랜치 전략: `main`(운영) / `develop`(통합) / `feature/{기능명}` / `fix/{이슈번호}` / `hotfix/{내용}`.
- PR: 1인 이상 승인 · `vue-tsc` 0건 · squash merge. 메타 변경 시 `systemType`·`packageType`·`groupId`·`metaStatus` 포함 여부 자가 점검.
- 의사결정은 `docs/ADR.md` 에 ADR-NNN 형식으로 누적 기록.

## 명령어
```bash
# Docker (PostgreSQL + pgAdmin)
docker-compose up -d
docker-compose ps
docker exec -it itg-postgres psql -U itg -d itgdb

# Backend
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test
./gradlew build

# Frontend
pnpm install
pnpm dev
pnpm build
pnpm test
pnpm type-check    # vue-tsc

# Swagger 산출물
curl http://localhost:8080/v3/api-docs      -o itg-api-spec.json
curl http://localhost:8080/v3/api-docs.yaml -o itg-api-spec.yaml
```

## 로컬 접속 정보
- PostgreSQL: `localhost:5432` / DB `itgdb` / user `itg` / pw `itg1234`
- pgAdmin: <http://localhost:5050> (`admin@itg.local` / `admin1234`)
- Spring Boot: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Vue Dev: <http://localhost:5173>
- 시드 사용자 (테스트용 — 운영 비밀번호 변경 필수):
  - admin / admin-sample-1234 (ROLE_ADMIN)
  - it-support / it-sample-1234 (ROLE_IT_SUPPORT)
  - user-sample-1 / user-sample-1234 (ROLE_USER)

## 환경 변수 / 런타임
- Java 21 (LTS) · Spring Boot 3.x · Gradle 8.x
- Node.js v24 (공용 운영 버전 — 변경 금지)
- Docker Desktop (Windows 기준 검증)
- Spring Profile: `local` (개발 PC), `dev`/`prod` 는 별도 관리
