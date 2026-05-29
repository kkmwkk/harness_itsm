# 품질 게이트 보고서 — Polestar10 ITG v2 백엔드

> Phase 8 (sonarqube-ci-gate) Step 3 — `quality-gate-and-report`
> CLAUDE.md §9 / PRD §9 M6 품질 게이트 실측 + 평가 보고서

본 보고서는 **로컬 SonarQube 인스턴스를 실제로 기동하여 `./gradlew sonar` 정적분석을 수행**하고,
CLAUDE.md §9 / PRD §9 M6 의 품질 게이트(Service 70% 커버리지 / 중복 3% 이하 / Blocker·Critical 0 /
시크릿 0)를 실측값으로 평가한 결과를 정리한다.

---

## 1. 환경

| 항목 | 값 |
|------|-----|
| 분석 일시 | 2026-05-29 14:43 (KST) |
| 작업 디렉토리 | `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM` |
| 브랜치 | `feat-8-sonarqube-ci-gate` |
| SonarQube | `sonarqube:community` (Docker, `docker-compose.sonar.yml`) — http://localhost:9000 |
| SonarQube DB | `postgres:16` (`itg-sonar-db`, 운영 `itgdb` 와 분리) |
| 분석 명령 | `./gradlew test jacocoTestReport sonar --no-daemon` (`backend/`) |
| Project Key | `polestar-itg-v2` |
| 커버리지 입력 | JaCoCo 0.8.12 XML (`build/reports/jacoco/test/jacocoTestReport.xml`) |
| 분석 범위 | backend 모듈 한정 (frontend 별도 — 본 phase 범위 외) |
| 시스템 메모리 | Docker 7.65GiB (SonarQube community 기동 충족) |

> 토큰은 SonarQube REST API(`/api/user_tokens/generate`)로 임시 발급해 분석에만 사용했으며,
> 본 보고서·git 어디에도 토큰 값을 기록하지 않는다 (`.env.sonar` 는 `.gitignore` 대상, Step 0).

---

## 2. 측정 결과

SonarQube `/api/measures/component` 실측값 (project `polestar-itg-v2`):

| 지표 (metric) | 값 | 비고 |
|---------------|-----|------|
| `coverage` (전체 line+branch) | **78.2%** | SonarQube 전체 커버리지 |
| `duplicated_lines_density` | **0.0%** | 중복 라인 비율 |
| `bugs` | **0** | reliability_rating A |
| `vulnerabilities` | **0** | security_rating A |
| `security_hotspots` | **1** | CSRF 비활성화 검토 (§3 참조, 시크릿 아님) |
| `code_smells` | **52** | maintainability_rating A (sqale_rating 1.0) |
| `reliability_rating` | **1.0 (A)** | |
| `security_rating` | **1.0 (A)** | |
| `sqale_rating` (maintainability) | **1.0 (A)** | |
| `ncloc` | 1,762 | 분석 대상 코드 라인 |
| `lines` | 2,124 | 전체 라인 |

### 2-1. SERVICE 커버리지 보강 (JaCoCo summary)

CLAUDE.md §9 의 1차 게이트 기준은 **Service 패키지 70% 커버리지**다. SonarQube 의 `coverage` 는 전체
대상 기준(78.2%)이므로, `scripts/jacoco_summary.py` 로 SERVICE 패키지를 분리 측정해 보강한다
(Step 1 baseline 과 동일 방식):

| 범위 | line coverage | covered/total |
|------|---------------|---------------|
| **SERVICE** (`*/service`) | **82.16%** | 221/269 |
| OVERALL (`com.nkia.itg`) | 84.40% | 460/545 |

→ SERVICE 82.16% ≥ 70% (Step 1 baseline 과 동일, 회귀 없음).

---

## 3. 게이트 평가 (CLAUDE.md §9 / PRD §9 M6) — **PASS ✅**

`scripts/sonar_gate.py /tmp/sonar_measures.json` 실행 결과:

```
Gate: PASS
  coverage=78.2%, duplicated_lines_density=0.0%
  bugs=0.0, vulnerabilities=0.0
  code_smells=52.0, security_hotspots=1.0
  ratings(rel/sec/maint)=1.0/1.0/1.0
```

| 게이트 기준 (CLAUDE.md §9) | 목표 | 실측 | 판정 |
|---------------------------|------|------|------|
| Service 단위 테스트 커버리지 | ≥ 70% | SERVICE 82.16% (전체 78.2%) | ✅ PASS |
| 중복 코드 비율 | ≤ 3% | 0.0% | ✅ PASS |
| 보안 취약점 (Blocker·Critical) | 0건 | 0건 (vulnerabilities=0) | ✅ PASS |
| 하드코딩 시크릿 | 0건 | 0건 (§3-1 참조) | ✅ PASS |

### 3-1. security_hotspot 1건 — 시크릿 아님

유일한 security_hotspot 은 **시크릿이 아니다**:

- 위치: `com/nkia/itg/common/security/SecurityConfig.java:21`
- 카테고리: `csrf` — *"Make sure disabling Spring Security's CSRF protection is safe here."*
- 성격: JWT 기반 **stateless REST API** 에서 CSRF 보호를 비활성화한 표준 설정 검토 항목.
  세션 쿠키 기반이 아니므로 CSRF 비활성화는 의도된 설계(ADR-008)다 — 하드코딩 시크릿과 무관.

→ 하드코딩 시크릿 탐지 결과 **0건**. 게이트의 "시크릿 0" 기준 충족. (단, §4 의 도구 한계 참조)

---

## 4. 분석 한계

- **로컬 SonarQube 부팅 부담**: SonarQube community 는 Elasticsearch 를 포함해 메모리(권장 ≥ 4GiB)를
  요구한다. 부팅이 어려운 환경에서는 본 로컬 분석을 생략하고, `.github/workflows/sonarqube.yml`(Step 2)이
  권한 있는 secret(`SONAR_TOKEN`/`SONAR_HOST_URL`)으로 **sonarcloud.io 또는 사내 SonarQube 인스턴스**에
  분석을 수행한다. CI 자동 동작은 Step 2 workflow 가 보증한다.
- **SonarQube coverage 와 SERVICE 게이트의 단위 차이**: SonarQube `coverage` 는 전체 대상(line+branch)
  기준이므로 SERVICE-only 70% 게이트와 직접 일치하지 않는다. SERVICE 분리 측정은 `scripts/jacoco_summary.py`
  로 보강한다(§2-1).
- **시크릿 탐지의 한계**: SonarQube community 의 security hotspot 은 하드코딩 시크릿 탐지를 전면 보장하지
  않는다. 시크릿 0 게이트를 강하게 보장하려면 `gitleaks` 등 전용 도구를 CI 에 추가하는 것이 바람직하다
  (**별도 ADR 후보** — 본 phase 범위 외).

---

## 5. 후속 작업

- **커버리지 보강 (별도 phase)**: Step 1 baseline §4 에서 식별한 미커버 분기에 단위 테스트 보강.
  우선순위 — `MetaValidationService` negative 분기(33 라인), `AssetService`/`TicketService` 의
  not-found 예외 경로(`getByAssetNo`·`getByTicketNo` 의 `orElseThrow`), `MetaService.archive`.
  단, 의미 없는 커버리지 인플레이션은 금지하며 ADR 로 범위 정의 후 진행한다.
- **code_smells 52건 정리 (별도 phase)**: maintainability_rating 은 A 이나 누적 code_smell 은 점진
  정리 대상. SonarQube 대시보드에서 severity 순 분류 후 우선순위화.
- **gitleaks 등 시크릿 전용 스캐너 도입**: §4 의 시크릿 탐지 한계 보강 — 별도 ADR 작성 후 CI 추가.
- **CI 게이트 등록**: GitHub repo Settings → Secrets 에 `SONAR_TOKEN`(필수)·`SONAR_HOST_URL` 등록
  (sonarcloud.io 또는 사내 인스턴스 택1). docs/SONAR_CI_GUIDE.md §5 참조.
- **PR 차단 게이트화**: GitHub branch protection 규칙에 SonarQube workflow 를 required check 로 등록해
  게이트 미달 PR 을 머지 차단. (`continue-on-error` 는 사용하지 않음 — 게이트 의미 보존.)

---

## 6. 산출물

| 산출물 | 비고 |
|--------|------|
| `backend/build.gradle` | Step 0 — jacoco + org.sonarqube 플러그인 |
| `backend/sonar-project.properties` | Step 0 — sonar 설정 (exclusions 등) |
| `backend/SONAR_BASELINE.md` | Step 1 — 커버리지 baseline 측정 |
| `scripts/jacoco_summary.py` | Step 1 — JaCoCo XML → SERVICE/OVERALL 요약 |
| `.github/workflows/sonarqube.yml` | Step 2 — PR/main CI 분석 |
| `docker-compose.sonar.yml` | Step 2 — 로컬 SonarQube + 전용 DB |
| `docs/SONAR_CI_GUIDE.md` | Step 2 — 로컬/CI 분석 가이드 |
| `scripts/sonar_gate.py` | **본 step — measures.json 파싱 + 게이트 평가** |
| `docs/QUALITY_GATE_REPORT.md` | **본 보고서** |

---

## 7. 결론

- **PRD §9 M6 마일스톤 이행**: `로컬 SonarQube 기동 → ./gradlew sonar 분석 → measures API 조회 →
  게이트 평가` 가 실제로 동작함을 실측으로 검증했다. SonarQube CI 게이트 연동의 산출물이 정착됐다.
- **게이트 PASS**: SERVICE 커버리지 82.16%(≥70%), 중복 0.0%(≤3%), bugs/vulnerabilities 0건,
  하드코딩 시크릿 0건 — CLAUDE.md §9 / PRD §9 M6 의 4개 기준을 모두 충족한다.
- **PRD §9 전 마일스톤(M1~M6) 종료**: M6(SonarQube CI 게이트 연동)을 끝으로 PRD §9 계획 마일스톤이
  모두 산출물로 정착됐다. 후속(커버리지 보강·시크릿 스캐너·branch protection)은 §5 의 별도 작업 항목이다.
