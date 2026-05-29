# Step 3: quality-gate-and-report

## 읽어야 할 파일

- `/CLAUDE.md` §9 — 품질 게이트 목표
- `/docs/PRD.md` §9 M6
- `/phases/8-sonarqube-ci-gate/step0~2.md` — 산출물 전체
- `/backend/SONAR_BASELINE.md` (step 1)
- `/docker-compose.sonar.yml`·`.github/workflows/sonarqube.yml` (step 2)

## 작업

이 step 의 목적은 **로컬 SonarQube 인스턴스를 띄워 실제 `./gradlew sonar` 분석을 수행하고, 품질 게이트(Service 70% / 중복 3% / Blocker·Critical 0 / 시크릿 0) 결과를 보고서로 정리하는 것**이다. 실 운영 게이트 통과 여부 와 후속 작업 항목 식별이 산출물.

> SonarQube 실행은 메모리·시간 비용이 있으므로, **자동 시나리오 + 수동 검증의 절충**. CI 환경에서 자동으로 분석이 동작한다는 점은 step 2 의 workflow 가 보증.

### 1. 로컬 SonarQube 인스턴스 기동

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose -f docker-compose.sonar.yml up -d
# SonarQube 첫 부팅은 1~3 분 소요
sleep 60
curl -fsS http://localhost:9000/api/system/status \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['status'])"
# 'UP' 이면 정상
```

> 부팅에 실패하거나 컨테이너 메모리 부족이면 **시나리오를 자동 시나리오 + 보고서 작성으로 갈음** — 보고서에 "로컬 SonarQube 미실행, 분석은 CI workflow 로 갈음" 명시. step status 는 그래도 completed 가능.

### 2. 토큰 발급 + 분석 실행 (수동 일부)

대화형 단계 — 실 토큰 발급은 사용자가 직접 ("admin/admin" 로그인 → 토큰 발급) 또는 CI workflow 에서 자동.

**자동화 대안**: SonarQube REST API 로 토큰 발급:

```bash
# admin 기본 비번 변경 후 토큰 발급
curl -fsS -u admin:admin -X POST "http://localhost:9000/api/user_tokens/generate?name=ci-token" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['token'])" > /tmp/sonar_token
# 위가 실패하면(기본 admin/admin 만료 등) 수동 발급으로 fallback
SONAR_TOKEN=$(cat /tmp/sonar_token)

cd backend
SONAR_HOST_URL=http://localhost:9000 \
SONAR_TOKEN=$SONAR_TOKEN \
SONAR_PROJECT_KEY=polestar-itg-v2 \
  ./gradlew test jacocoTestReport sonar --no-daemon
```

분석 후 SonarQube 측정 결과를 API 로 조회:

```bash
curl -fsS -u "$SONAR_TOKEN:" \
  "http://localhost:9000/api/measures/component?component=polestar-itg-v2&metricKeys=coverage,duplicated_lines_density,bugs,vulnerabilities,security_hotspots,code_smells,sqale_rating,reliability_rating,security_rating" \
  > /tmp/sonar_measures.json
```

### 3. 게이트 평가

`scripts/sonar_gate.py` (선택, 단순) — measures.json 파싱 + 게이트 평가:

```python
#!/usr/bin/env python3
"""sonar_gate.py — SonarQube 측정 결과로 CLAUDE.md §9 게이트 평가."""
import json, sys
GATE = {
    "service_coverage_min": 0.70,    # SonarQube 의 coverage 는 전체 (별도 JaCoCo summary 보강 가능)
    "duplicated_lines_density_max": 3.0,
    "bugs_blocker_critical_max": 0,
    "vulnerabilities_max": 0,
    "hardcoded_secrets_max": 0,      # 보안 핫스팟 기반 휴리스틱
}
m = json.load(open(sys.argv[1] if len(sys.argv) > 1 else "/tmp/sonar_measures.json"))
measures = {mm["metric"]: float(mm["value"]) for mm in m["component"]["measures"]}
failed = []
if measures.get("coverage", 0) < 70.0:                  failed.append(("coverage < 70%", measures["coverage"]))
if measures.get("duplicated_lines_density", 0) > 3.0:   failed.append(("duplicated_lines_density > 3", measures["duplicated_lines_density"]))
if measures.get("bugs", 0) > 0:                          failed.append(("bugs > 0", measures["bugs"]))
if measures.get("vulnerabilities", 0) > 0:               failed.append(("vulnerabilities > 0", measures["vulnerabilities"]))
print("Gate:", "PASS" if not failed else "FAIL")
for k, v in failed: print(f"  - {k} (actual={v})")
print(f"  coverage={measures.get('coverage')}, duplicated_lines_density={measures.get('duplicated_lines_density')}")
print(f"  bugs={measures.get('bugs')}, vulnerabilities={measures.get('vulnerabilities')}")
print(f"  code_smells={measures.get('code_smells')}, security_hotspots={measures.get('security_hotspots')}")
```

### 4. 결과 보고서 — `docs/QUALITY_GATE_REPORT.md`

다음 섹션:
- 환경 (SonarQube 버전·실행 위치·분석 일시).
- 측정 결과 표 (line coverage / duplicated_lines_density / bugs / vulnerabilities / code_smells / security_hotspots / ratings).
- 게이트 평가 (CLAUDE.md §9 기준 PASS / FAIL).
- 분석 한계:
  - 로컬 SonarQube 부팅 어려운 환경에서는 CI workflow 가 권한 있는 secret 으로 sonarcloud.io 또는 사내 인스턴스 사용.
  - 시크릿 탐지는 SonarQube 의 security hotspot + 추가 도구(예: gitleaks) 보강 필요 — 별도 ADR.
- 후속 작업:
  - 미달 항목 보강 계획 (어떤 모듈의 테스트가 부족한지).
  - sonarcloud.io 또는 사내 서버 등록.
  - workflow 가 PR 차단 게이트로 동작하도록 GitHub branch protection 설정.
- 산출물:
  - `backend/build.gradle` (sonar + jacoco)
  - `backend/SONAR_BASELINE.md`
  - `.github/workflows/sonarqube.yml`
  - `docker-compose.sonar.yml`
  - `docs/SONAR_CI_GUIDE.md`·`docs/QUALITY_GATE_REPORT.md`
  - `scripts/jacoco_summary.py`·`scripts/sonar_gate.py`

### 5. 분석 실행이 환경 제약으로 불가능한 경우의 fallback

- 로컬 SonarQube 컨테이너 미기동 (메모리 < 4GB 등) 또는 분석 도중 timeout → 본 step 의 코드 산출(workflow yml·compose yml·script·report) 자체가 완비되면 **completed 로 간주**. 보고서에 "본 phase 의 산출물은 정착, 실 분석은 CI 환경 또는 후속 운영에서 수행" 명시.
- step status 를 무리하게 blocked 로 두지 마라. baseline 의 의미는 코드 자산 정착.

### 6. 변경 범위

- `scripts/sonar_gate.py` — 신규.
- `docs/QUALITY_GATE_REPORT.md` — 신규.
- 운영 코드(backend/frontend) 수정 금지.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM

# 1) 스크립트
test -f scripts/sonar_gate.py

# 2) 보고서
test -s docs/QUALITY_GATE_REPORT.md
grep -qE "PASS|FAIL|미달|미분석" docs/QUALITY_GATE_REPORT.md
grep -q "coverage\|커버리지" docs/QUALITY_GATE_REPORT.md
grep -q "PRD §9 M6\|CLAUDE.md §9" docs/QUALITY_GATE_REPORT.md

# 3) JaCoCo summary 도구 회귀
python3 scripts/jacoco_summary.py backend/build/reports/jacoco/test/jacocoTestReport.xml \
  | grep -q "SERVICE"

# 4) workflow / compose yaml syntax 회귀
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/sonarqube.yml'))"
python3 -c "import yaml; yaml.safe_load(open('docker-compose.sonar.yml'))"

# 5) (옵션) 실 SonarQube 분석 실행 시도 — 환경 제약 시 fallback 명시
# SonarQube 컨테이너 부팅에 1~3분 + 메모리 부담 → 본 AC 는 산출물 정착으로 갈음
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - 측정 결과 표 또는 fallback 설명이 보고서에 명시?
   - 게이트 기준 (CLAUDE.md §9) 이 보고서에 인용?
   - 시크릿 토큰이 보고서·git 어디에도 노출 없음?
   - 후속 작업 항목이 구체적 (어느 모듈 보강 / 어느 게이트 등록)?
3. step 3 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "scripts/sonar_gate.py (measures.json 파싱 + CLAUDE.md §9 게이트 평가) + docs/QUALITY_GATE_REPORT.md (측정 결과 또는 fallback + 게이트 평가 + 후속 작업). step 0~2 의 산출물 (build.gradle plugins + SONAR_BASELINE + workflow yml + compose yml + GUIDE) 와 함께 M6 마일스톤 산출물 정착 완료. PRD §9 전 마일스톤(M1~M6) 종료."`

## 금지사항

- 보고서·git 에 SonarQube 토큰·실 운영 시크릿 노출 금지.
- 로컬 SonarQube 부팅 어려운 환경에서 step 을 무리하게 blocked 로 두지 마라. 산출물 정착이 본 step 의 핵심.
- workflow 의 `continue-on-error` 추가 금지 — 게이트가 의미를 잃는다.
- 임의로 게이트 기준을 완화하지 마라 (예: 70% → 50%). CLAUDE.md §9 의 값 유지.
- 운영 코드 수정 금지.
- 새 phase 시작 / 새 ADR 작성 금지.
- frontend/sonar 추가 분석 금지.
- 보고서에 실 운영 데이터 금지.
