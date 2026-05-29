# SonarQube 커버리지 Baseline — Polestar10 ITG v2 백엔드

> Phase 8 (sonarqube-ci-gate) Step 1 — `coverage-baseline`
> CLAUDE.md §9 / PRD §9 M6 품질 게이트 baseline 측정 보고서

## 1. 측정 환경

| 항목 | 값 |
|------|-----|
| 측정 일시 | 2026-05-29 14:38 (KST) |
| 작업 디렉토리 | `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend` |
| 브랜치 | `feat-8-sonarqube-ci-gate` |
| 측정 명령 | `./gradlew clean test jacocoTestReport` |
| 커버리지 도구 | JaCoCo 0.8.12 (Step 0 셋업) |
| 입력 리포트 | `build/reports/jacoco/test/jacocoTestReport.xml` |
| 요약 스크립트 | `scripts/jacoco_summary.py` |
| 빌드 결과 | BUILD SUCCESSFUL (7 tasks) |

> 커버리지 지표는 **line coverage** 기준이다. JaCoCo exclusions(Application·package-info·dto·`_generated`)는 Step 0 `sonar-project.properties` 기준과 동일하게 적용된다.

## 2. 핵심 결과

| 지표 | 커버리지 | 비고 |
|------|----------|------|
| **OVERALL** line coverage | **84.40%** (460/545) | 전체 `com.nkia.itg` 패키지 |
| **SERVICE** line coverage | **82.16%** (221/269) | `*/service` 패키지만 — **게이트 목표 70%** |

### 게이트 평가 — **PASS ✅**

SERVICE 패키지 line coverage 가 **82.16%** 로 CLAUDE.md §9 / PRD §9 M6 의 목표인 **70% 를 +12.16%p 상회**한다. 본 시점의 baseline 은 게이트를 통과한다.

## 3. 패키지별 커버리지 (오름차순)

| 패키지 | line coverage | covered/total |
|--------|---------------|---------------|
| `com/nkia/itg/itam/asset/repository` | 0.00% | 0/0 |
| `com/nkia/itg/itsm/ticket/repository` | 0.00% | 0/0 |
| `com/nkia/itg/meta/repository` | 0.00% | 0/0 |
| `com/nkia/itg/itsm/ticket/entity` | 67.39% | 31/46 |
| `com/nkia/itg/itam/asset/entity` | 68.29% | 28/41 |
| `com/nkia/itg/itam/asset/controller` | 76.92% | 10/13 |
| **`com/nkia/itg/itam/asset/service`** | **78.38%** | 29/37 |
| **`com/nkia/itg/meta/service`** | **82.41%** | 164/199 |
| **`com/nkia/itg/itsm/ticket/service`** | **84.85%** | 28/33 |
| `com/nkia/itg/meta/controller` | 86.67% | 13/15 |
| `com/nkia/itg/itsm/ticket/controller` | 92.31% | 12/13 |
| `com/nkia/itg/common/exception` | 93.75% | 30/32 |
| `com/nkia/itg/meta/entity` | 96.67% | 29/30 |
| `com/nkia/itg/itsm/ticket/domain` | 100.00% | 10/10 |
| `com/nkia/itg/common/config` | 100.00% | 15/15 |
| `com/nkia/itg/itam/asset/domain` | 100.00% | 11/11 |
| `com/nkia/itg/common/security` | 100.00% | 25/25 |
| `com/nkia/itg/meta/domain` | 100.00% | 14/14 |
| `com/nkia/itg/common/response` | 100.00% | 11/11 |

> `*/repository` 패키지가 0/0 으로 표기되는 이유: JPA 인터페이스 + QueryDSL `Impl` 구조에서 실행 가능한 라인이 거의 없어(인터페이스는 카운트 대상 아님) JaCoCo 의 분석 대상 라인이 0 이다. 커버리지 미달이 아니라 **측정 대상 라인 없음**이다.

## 4. 미커버 영역 식별 (SERVICE 패키지)

게이트는 통과했으나, SERVICE 패키지에서 미커버 라인이 있는 메서드를 후속 보강 후보로 기록한다 (HTML 리포트 `build/reports/jacoco/test/html/index.html` 기준).

| 클래스 | 미커버 라인 | 미커버 메서드 (missed lines) |
|--------|------------|------------------------------|
| `MetaValidationService` | 33 | `validateActions`(10), `validateForm`(9), `validateAxes`(6), `validateGrid`(6), `validateApi`(1), `validateGridFormMatch`(1) |
| `AssetService` | 8 | `getByAssetNo`(3+1 lambda), `update`(2), `assign`(2), `getById`(1) |
| `TicketService` | 5 | `getByTicketNo`(3+1 lambda), `changePriority`(2) |
| `MetaService` | 2 | `getById`(1), `archive`(1) |

미커버 패턴 분석:
- **조회 not-found 분기 (`getByXxx` 의 `orElseThrow` lambda)**: 정상 조회 케이스만 테스트되어 미존재 예외 경로가 비어 있다.
- **`MetaValidationService` 의 형식 검증 분기**: `validateActions`·`validateForm` 등 일부 ERROR/WARNING 분기가 미테스트. dry-run 검증 로직의 negative 케이스 보강 여지.
- **상태 전이 메서드 (`changePriority`, `archive`, `update`, `assign`)**: 일부 분기·예외 경로 미커버.

## 5. 결론

- **게이트 통과**: SERVICE line coverage **82.16% ≥ 70%**. OVERALL 84.40%. 본 baseline 은 SonarQube CI 게이트 도입(Step 2~3)의 기준선으로 사용한다.
- **후속 작업 (별도 phase — 본 step 범위 외)**: §4 의 미커버 분기(특히 `MetaValidationService` negative 케이스, 각 Service 의 not-found 예외 경로)에 단위 테스트를 보강하면 SERVICE 커버리지를 90%대로 끌어올릴 수 있다. 단, 커버리지 인플레이션(의미 없는 테스트)은 금지하며, ADR 로 보강 범위를 정의한 뒤 진행한다.
- 본 step 은 baseline **측정·기록**이 목적이며, 운영 코드·테스트 수정은 수행하지 않았다.
