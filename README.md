# Polestar10 ITG v2 (POLESTAR-ITSM)

비개발자가 **WYSIWYG 으로 화면을 만들고**, 그 화면이 도메인 모델(요청 유형·자산 분류·워크플로우·역할) 위에서 동작하는 **No-code ITSM 플랫폼**.
메타 한 건이 화면 한 개를 정의한다 — Vue 파일을 새로 쓰지 않고 `page_meta` 레코드만 등록한다.

![Status](https://img.shields.io/badge/M1~M11-completed-success) ![Java](https://img.shields.io/badge/Java-21-orange) ![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen) ![License](https://img.shields.io/badge/license-internal-lightgrey)

---

## 기능 한눈에

| 모듈 | 내용 |
|---|---|
| **ITSM** | 요청 유형 5종(장애·서비스요청·변경·문제·문의) × 워크플로우 정의 5종 · 단계별 역할 라우팅 · SLA · 칸반 보드 |
| **ITAM** | 자산 분류 트리(HW/SW/계약/서비스) · 분류별 폼 메타 분기 · 라이프사이클 이벤트 타임라인 · 등록 시점 메타 보존 |
| **PMS** | 프로젝트·태스크 + 간트 차트 |
| **SYSTEM** | 사용자·부서·역할·권한·메뉴(동적 트리·권한 필터) · 메타 편집기(폼·드래그·WYSIWYG) |
| **공통** | JWT 인증 · 다크 모드 · 알림 센터 · 대시보드(KPI·차트·활동 피드) · AI 메타 생성 CLI |

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| Frontend | Vue 3.5 (Composition + `<script setup lang="ts">`) · Vite 6 · TypeScript 5 strict · pnpm 11 |
| UI | shadcn/vue · Tailwind CSS v4 (CSS 변수 토큰) · Pretendard Variable |
| Grid/Chart | AG Grid Community 32 · TanStack Table · ECharts 6 · vue-echarts |
| Form | VeeValidate 4 + Zod (`toTypedSchema`) |
| State | Pinia 2 · VueUse 11 (`useFetch`) · Vue Router 4 |
| Backend | Spring Boot 3.5 · **Java 21** (Record · Virtual Thread) · JPA · Spring Security + JWT (HS256) |
| API 문서 | SpringDoc OpenAPI / Swagger UI |
| DB | PostgreSQL 16 (Docker Desktop) |
| 빌드/테스트 | Gradle 8 · JUnit 5 + Mockito · Vitest · Playwright (시각 회귀) · JaCoCo · SonarQube |

---

## 사전 설치 (한 번만)

| 도구 | 버전 | 설치 |
|---|---|---|
| **Docker Desktop** | 최신 | <https://www.docker.com/products/docker-desktop> |
| **Node.js** | **v24** (LTS) | `nvm install 24 && nvm use 24` (또는 시스템 설치) |
| **pnpm** | 11.x | `corepack enable` 또는 `npm i -g pnpm@11` |
| **Java** | 21 | Gradle toolchain 이 자동 다운로드 (foojay-resolver) — 별도 설치 불필요 |
| **Git** | — | — |

> Node v18 은 corepack/pnpm 호환 안 됨 — 반드시 v24.

---

## 첫 부팅 — 5분 절차

```bash
# 0) Clone
git clone https://github.com/kkmwkk/harness_itsm.git
cd harness_itsm

# 1) PostgreSQL 컨테이너 부팅 + sql/init/*.sql 자동 적용
docker-compose up -d
docker-compose ps        # itg-postgres healthy 확인

# 2) 백엔드 (새 터미널)
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
# 첫 실행: Gradle 의존성·Java 21 toolchain 다운로드로 3~5분 소요
# 'Started Application in X.Xs' 메시지 후 8080 에서 listen

# 3) 프런트엔드 (또 다른 터미널)
cd frontend
nvm use 24                                 # ⚠️ Node 24 필수
pnpm install                               # 첫 실행: 약 1분
pnpm dev                                   # Vite 5173 부팅

# 4) 브라우저
# 👉 http://localhost:5173
# 로그인: admin / admin-sample-1234
```

### 시드 사용자 (테스트용 — 운영 비밀번호 변경 필수)

| 계정 | 비밀번호 | 역할 |
|---|---|---|
| `admin` | `admin-sample-1234` | ROLE_ADMIN — 전체 권한 |
| `it-support` | `it-sample-1234` | ROLE_IT_SUPPORT |
| `team-lead` | `team-sample-1234` | ROLE_TEAM_LEAD (1차 승인자) |
| `user-sample-1` | `user-sample-1234` | ROLE_USER |

---

## 접속 URL

| 서비스 | URL |
|---|---|
| **Frontend (Vue)** | <http://localhost:5173> |
| **Backend (Spring Boot)** | <http://localhost:8080> |
| **Swagger UI** | <http://localhost:8080/swagger-ui.html> |
| **PostgreSQL** | `localhost:5432` (DB `itgdb`, user `itg`, pw `itg1234`) |
| pgAdmin | <http://localhost:5050> (`admin@itg.local` / `admin1234`) |

---

## 추천 워크스루 (admin 로그인 후)

1. **홈** — KPI 4 카드(열린 티켓·SLA 임박·내 작업·전체 자산) + 차트 + 활동 피드 + 워크플로우 큐
2. **TopBar 우측 테마 토글** — 라이트/시스템/다크 즉시 전환
3. **`/itsm`** — 시드 티켓 그리드 → "등록" 클릭 → 요청 유형 선택 → 폼 입력 → 저장
4. **`/itsm/board`** — 칸반 보드 (드래그로 상태 전이)
5. **티켓 행 클릭 → `/itsm/:id`** — 상세 + **WorkflowPanel** (현재 단계·SLA·액션 버튼)
6. **`/itam`** — 좌측 자산 분류 트리 → HW_LAPTOP 클릭 → 분류별 폼으로 등록
7. **`/itam/:id`** — 자산 상세 + **라이프사이클 타임라인**
8. **`/pms`** — 프로젝트 그리드 + **간트 차트**
9. **`/system/users`·`/depts`·`/roles`·`/menus`** — 시스템 관리 CRUD
10. **`/system/meta-editor`** — **No-code 메타 편집기** (필드 드래그 정렬·발행 → 화면 자동 갱신)
11. **`user-sample-1` 로 재로그인** — Sidebar 메뉴가 권한별로 다르게 표시

---

## 자주 만나는 첫 실행 이슈

| 증상 | 해결 |
|---|---|
| `pnpm dev` 시 `TypeError: Invalid host defined options` | Node 18 사용 중. `nvm use 24` 후 재실행 |
| Backend 부팅 후 메타 API 가 빈 응답 | 기존 컨테이너 볼륨에 시드가 안 들어감. `docker-compose down -v && docker-compose up -d` 로 볼륨 재생성 |
| Playwright e2e 시 `Executable doesn't exist` | `cd frontend && pnpm exec playwright install chromium` |
| Gradle 첫 빌드가 너무 오래 | 의존성·Java 21 toolchain 다운로드 (한 번만). 이후엔 캐시 |
| 5432 포트 점유 | 호스트 PostgreSQL 종료 (`brew services stop postgresql` 등) 후 `docker-compose up -d` |
| 5173 포트 점유 | `lsof -nP -iTCP:5173 -sTCP:LISTEN` 으로 PID 확인 → kill |
| Backend 가 401 만 반환 | JWT 시크릿이 application-local.yml 에 평문 가상값으로 박혀있음. 운영 배포 시 `APP_JWT_SECRET` 환경변수로 교체 |

---

## 자주 쓰는 명령

```bash
# Docker
docker-compose up -d                                  # 인프라 기동
docker-compose ps
docker-compose down                                   # 컨테이너 중지 (볼륨 보존)
docker-compose down -v                                # 볼륨까지 삭제 (시드 재적용 시 필수)
docker exec -it itg-postgres psql -U itg -d itgdb     # DB 접속

# Backend
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test                                        # JUnit 5 + Mockito
./gradlew jacocoTestReport                            # 커버리지 리포트
./gradlew sonar                                       # SonarQube 분석 (.env.sonar 필요)

# Frontend
cd frontend
pnpm dev                                              # 개발 서버 5173
pnpm build                                            # 프로덕션 빌드
pnpm type-check                                       # vue-tsc strict
pnpm lint                                             # ESLint
pnpm test                                             # Vitest
pnpm e2e                                              # Playwright 시각 회귀

# Swagger 산출물
curl http://localhost:8080/v3/api-docs       -o /tmp/spec.json
curl http://localhost:8080/v3/api-docs.yaml  -o /tmp/spec.yaml

# AI 메타 생성 (신규 모듈 추가)
python3 scripts/generate_meta.py \
  --openapi backend/openapi/itg-api-spec.json \
  --request-dto MyCreateRequest --response-dto MySummary \
  --group-id itg-my-module --title "내 모듈" \
  --system-type COMMON --package-type PACKAGE \
  --major 1 --minor 1 --api /api/my-module
```

---

## 프로젝트 구조

```
harness_itsm/
├── docker-compose.yml          # postgres + pgadmin
├── docker-compose.sonar.yml    # SonarQube (옵션)
├── sql/init/                   # DB 스키마·시드 (컨테이너 부팅 시 자동 적용)
│   ├── 01_schema.sql           # page_meta 메타 모델
│   ├── 02_ticket.sql ~ 07      # ITSM 티켓·자산
│   ├── 10_auth.sql ~ 14        # 사용자·부서·역할·권한·메뉴 + 시드
│   ├── 11~12, 15~18            # 워크플로우·자산 분류·메타 시드
│   ├── 19_pms_seed.sql         # PMS
│   └── 20_notification.sql     # 알림
├── backend/                    # Spring Boot 3 + Java 21
│   ├── src/main/java/com/nkia/itg/
│   │   ├── auth, system, meta, itsm, itam, pms, common, dashboard, notification
│   │   └── ...
│   ├── openapi/                # OpenAPI JSON/YAML 산출물
│   └── build.gradle            # jacoco + sonarqube + jwt + springdoc
├── frontend/                   # Vite 6 + Vue 3.5 + TS strict
│   ├── src/
│   │   ├── components/         # ui(shadcn), dynamic, dataviz, dashboard, editor, layout, ...
│   │   ├── composables/        # use* (Pinia store / VueUse fetch)
│   │   ├── pages/              # itsm, itam, pms, common, system, LoginPage, HomePage
│   │   ├── stores/             # useAuthStore, useMenuStore, useThemeStore, useLayoutStore
│   │   ├── types/              # 백엔드 DTO 1:1 타입
│   │   ├── lib/                # api(useFetch), meta-body type guard, ui-messages, chart-theme, ...
│   │   └── assets/styles/      # tokens.css(v2), shadcn-mapping, base, ag-theme-itg
│   ├── e2e/                    # Playwright spec + baseline 스크린샷
│   └── package.json
├── scripts/                    # AI 메타 생성·검증·SonarQube
│   ├── generate_meta.py
│   ├── validate_meta.sh
│   ├── jacoco_summary.py
│   └── sonar_gate.py
├── phases/                     # 17 phase 의 step 설계·진행 이력 (개발 프로세스 기록)
├── .github/workflows/
│   ├── sonarqube.yml           # JaCoCo + SonarQube CI
│   └── playwright.yml          # 시각 회귀 CI
└── docs/
    ├── PRD.md                  # 제품 요구 (v2.1)
    ├── ARCHITECTURE.md         # 18 섹션 — 도메인 ERD·동적 렌더링·워크플로우·UX
    ├── ADR.md                  # ADR-001~020
    ├── UI_GUIDE.md             # UI 토큰·컴포넌트 규칙 (v2: 모듈 컬러·다크·마이크로 인터랙션)
    ├── META_GENERATION_GUIDE.md
    ├── QA_REPORT.md · QUALITY_GATE_REPORT.md
    ├── DESIGN_V2_REPORT.md · STRETCH_DECISION_REPORT.md
    └── PROJECT_FINAL_REPORT.md # M1~M11 + 디자인 v2 종합
```

---

## 마일스톤 진행 (M1~M11 + 디자인 v2 모두 완료)

| M | 내용 |
|---|---|
| **M1** | 백엔드 메타 모듈 (`page_meta` JSONB + 버전 그룹 + 자동 DEPRECATE) |
| **M2** | 동적 렌더링 (DynamicPage/Form/Grid + shadcn/AG Grid 자동 분기) |
| **M3** | ITSM 티켓 (백엔드 + 프런트 통합) |
| **M4** | ITAM 자산원장 + 이력 메타 보존 (등록 시점 메타 복원) |
| **M5** | AI 메타 자동 생성 CLI (`scripts/generate_meta.py`) + dry-run |
| **M6** | SonarQube CI 게이트 (Service 커버리지 70% · 중복 3% · Blocker/Critical 0) |
| **M7** | 인증·사용자·부서·역할·권한·메뉴 (JWT + 동적 메뉴 트리) |
| **M8** | 도메인 깊이 (요청 유형 5종 + 워크플로우 정의 5종 + 자산 분류 트리) |
| **M9** | No-code 메타 편집 UI (폼·그리드·액션 GUI 편집기) |
| **M10** | 드래그앤드롭 메타 편집 (필드·컬럼 순서) |
| **M11 Stretch** | WYSIWYG 인플레이스 편집 PoC + BPMN/Camunda 도입 평가 |
| **디자인 v2** | 모듈 컬러 5종 · 다크 모드 · ECharts · 대시보드 · 칸반 · 간트 · 마이크로 인터랙션 · 알림 센터 · 풍부한 빈 상태 · 폼 위젯 풀구현 |

자세한 산출물·결함 카탈로그·교훈은 [`docs/PROJECT_FINAL_REPORT.md`](./docs/PROJECT_FINAL_REPORT.md) 참고.

---

## 더 보기

- **개발 절대 규칙·프로세스** → [`CLAUDE.md`](./CLAUDE.md)
- **제품 요구사항 (PRD v2.1)** → [`docs/PRD.md`](./docs/PRD.md)
- **아키텍처 · 도메인 ERD** → [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md)
- **의사결정 기록 (ADR-001~020)** → [`docs/ADR.md`](./docs/ADR.md)
- **UI 토큰·컴포넌트 규칙 v2** → [`docs/UI_GUIDE.md`](./docs/UI_GUIDE.md)
- **신규 모듈 메타 생성** → [`docs/META_GENERATION_GUIDE.md`](./docs/META_GENERATION_GUIDE.md)
- **QA·시각 회귀 운영** → [`docs/QA_REPORT.md`](./docs/QA_REPORT.md)
- **품질 게이트** → [`docs/QUALITY_GATE_REPORT.md`](./docs/QUALITY_GATE_REPORT.md)
- **디자인 v2 before/after** → [`docs/DESIGN_V2_REPORT.md`](./docs/DESIGN_V2_REPORT.md)

---

## 라이선스

내부 프로젝트 — 외부 배포는 별도 결정 후.
