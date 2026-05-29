# Step 1: coverage-baseline

## 읽어야 할 파일

- `/CLAUDE.md` §9 — 품질 게이트 목표
- `/docs/PRD.md` §9 M6
- `/phases/8-sonarqube-ci-gate/step0.md` — JaCoCo 셋업
- `/backend/build/reports/jacoco/test/jacocoTestReport.xml` (step 0 산출, 이미 생성됨)

## 작업

이 step 의 목적은 **현재 백엔드 코드의 단위·통합 테스트 커버리지 baseline 을 측정하고, Service 70% 목표(CLAUDE.md §9) 도달 여부를 보고하며, 미달 시 보강할 영역을 식별하는 것**이다.

### 1. 커버리지 측정

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend
./gradlew clean test jacocoTestReport
```

생성되는 산출물:
- `build/reports/jacoco/test/jacocoTestReport.xml` (SonarQube 입력)
- `build/reports/jacoco/test/html/index.html` (사람 검토용)

### 2. 패키지·클래스별 baseline 추출

Python 스크립트 `scripts/jacoco_summary.py` 를 추가하여 XML 을 파싱·요약:

```python
#!/usr/bin/env python3
"""jacoco_summary.py — JaCoCo XML 리포트에서 패키지·클래스별 커버리지 요약."""
import sys, xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE_FILTER = "com/nkia/itg/"
SERVICE_PATTERN = "/service/"

def parse(xml_path: str) -> dict:
    tree = ET.parse(xml_path)
    root = tree.getroot()
    summary = {"overall": {}, "service": {}, "packages": []}
    for pkg in root.findall("package"):
        name = pkg.attrib["name"]
        if not name.startswith(PACKAGE_FILTER): continue
        pkg_cov = {"name": name, "lines_covered": 0, "lines_missed": 0}
        for cls in pkg.findall("class"):
            for counter in cls.findall("counter"):
                if counter.attrib["type"] != "LINE": continue
                covered = int(counter.attrib["covered"])
                missed  = int(counter.attrib["missed"])
                pkg_cov["lines_covered"] += covered
                pkg_cov["lines_missed"]  += missed
        pkg_cov["line_ratio"] = ratio(pkg_cov["lines_covered"], pkg_cov["lines_missed"])
        summary["packages"].append(pkg_cov)
    # overall
    total_c = sum(p["lines_covered"] for p in summary["packages"])
    total_m = sum(p["lines_missed"]  for p in summary["packages"])
    summary["overall"] = {"lines_covered": total_c, "lines_missed": total_m,
                          "line_ratio": ratio(total_c, total_m)}
    # service 패키지만
    svc_c = sum(p["lines_covered"] for p in summary["packages"] if SERVICE_PATTERN in p["name"] or p["name"].endswith("/service"))
    svc_m = sum(p["lines_missed"]  for p in summary["packages"] if SERVICE_PATTERN in p["name"] or p["name"].endswith("/service"))
    summary["service"] = {"lines_covered": svc_c, "lines_missed": svc_m,
                          "line_ratio": ratio(svc_c, svc_m)}
    return summary

def ratio(c: int, m: int) -> float:
    total = c + m
    return c / total if total > 0 else 0.0

def main():
    xml = sys.argv[1] if len(sys.argv) > 1 else "backend/build/reports/jacoco/test/jacocoTestReport.xml"
    s = parse(xml)
    print(f"OVERALL    line coverage  : {s['overall']['line_ratio']*100:.2f}% " \
          f"({s['overall']['lines_covered']}/{s['overall']['lines_covered']+s['overall']['lines_missed']})")
    print(f"SERVICE    line coverage  : {s['service']['line_ratio']*100:.2f}% " \
          f"({s['service']['lines_covered']}/{s['service']['lines_covered']+s['service']['lines_missed']})")
    print(f"  → goal 70% reached: {'YES' if s['service']['line_ratio'] >= 0.70 else 'NO'}")
    print()
    print("패키지별 (line):")
    for p in sorted(s['packages'], key=lambda x: x['line_ratio']):
        print(f"  {p['name']:<60} {p['line_ratio']*100:6.2f}%  "
              f"({p['lines_covered']:>4}/{p['lines_covered']+p['lines_missed']:<4})")

if __name__ == "__main__":
    main()
```

`scripts/jacoco_summary.py` 실행으로 OVERALL·SERVICE·패키지별 비율 확인.

### 3. baseline 보고서 — `backend/SONAR_BASELINE.md`

다음 섹션:
- 측정 일시·환경.
- OVERALL line coverage %.
- SERVICE 패키지만 line coverage % (목표 70%).
- 패키지별 표 (오름차순 — 낮은 커버리지부터).
- 70% 미달 시 보강 후보 (어떤 메서드/클래스가 커버되지 않은지 — HTML 리포트 참고).
- 결론: 게이트 통과 / 미달 + 후속 작업.

### 4. 단위 테스트 (선택, 가벼움)

`scripts/test_jacoco_summary.py` — `ratio` 함수·`parse` 함수의 기본 동작 1~3 케이스.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM

# 1) JaCoCo XML 존재 (step 0 의 산출물)
test -f backend/build/reports/jacoco/test/jacocoTestReport.xml || \
  (cd backend && ./gradlew test jacocoTestReport)

# 2) 요약 스크립트
test -f scripts/jacoco_summary.py
python3 scripts/jacoco_summary.py backend/build/reports/jacoco/test/jacocoTestReport.xml \
  | tee /tmp/_coverage.txt
grep -q "OVERALL" /tmp/_coverage.txt
grep -q "SERVICE" /tmp/_coverage.txt

# 3) baseline 보고서
test -s backend/SONAR_BASELINE.md
grep -q "SERVICE" backend/SONAR_BASELINE.md
grep -q "%" backend/SONAR_BASELINE.md
grep -q "70%" backend/SONAR_BASELINE.md
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - SERVICE 커버리지가 70% 이상 도달했는가? (PASS / NO + 후속 작업 명시)
   - 보고서가 패키지별 표를 포함하는가?
3. step 1 업데이트:
   - 성공 → `"summary": "scripts/jacoco_summary.py (XML 파싱 + OVERALL/SERVICE/패키지별 line coverage 요약, 70% 게이트 평가) + backend/SONAR_BASELINE.md 작성. 본 시점의 SERVICE 커버리지 baseline 기록 (목표 70% 도달 또는 미달 영역 식별)."`
   - 미달 시 그래도 success (baseline 기록이 목적). 다만 보고서에 명시.

## 금지사항

- 70% 미달이라고 step status 를 error 로 두지 마라. 본 step 의 목적은 baseline 측정·기록. 미달 시 후속 작업 식별까지.
- 보강 위해 새 단위 테스트를 본 step 에서 작성 금지. 별도 phase 의 ADR.
- 커버리지 인플레이션 (의미 없는 테스트 추가) 금지.
- 운영 코드 수정 금지.
- 보고서에 실 운영 데이터 금지.
- jacoco-report-aggregation 같은 추가 플러그인 도입 금지. 본 step 은 단일 모듈 분석으로 충분.
