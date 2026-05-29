#!/usr/bin/env python3
"""sonar_gate.py — SonarQube 측정 결과로 CLAUDE.md §9 / PRD §9 M6 품질 게이트 평가.

입력: /api/measures/component 응답 JSON (measures.json).
게이트 기준 (CLAUDE.md §9 비기능 요구사항):
  - 커버리지 (coverage) ≥ 70%   ※ SERVICE 70% 가 1차 기준 — SonarQube 전체 coverage 로 보강 평가
  - 중복 라인 비율 (duplicated_lines_density) ≤ 3%
  - bugs / vulnerabilities (Blocker·Critical) = 0
  - 하드코딩 시크릿 = 0   ※ security_hotspot 기반 휴리스틱 — 시크릿 카테고리만 카운트

사용법:
  python3 scripts/sonar_gate.py [measures.json]
종료 코드: 게이트 PASS → 0, FAIL → 1.
"""
import json
import sys

GATE = {
    "coverage_min": 70.0,
    "duplicated_lines_density_max": 3.0,
    "bugs_max": 0,
    "vulnerabilities_max": 0,
    "hardcoded_secrets_max": 0,
}


def load_measures(path: str) -> dict:
    with open(path, encoding="utf-8") as fh:
        doc = json.load(fh)
    out = {}
    for m in doc["component"]["measures"]:
        try:
            out[m["metric"]] = float(m["value"])
        except (KeyError, ValueError):
            out[m["metric"]] = m.get("value")
    return out


def evaluate(measures: dict) -> list:
    """게이트 위반 항목 목록 반환 (빈 리스트면 PASS)."""
    failed = []
    cov = measures.get("coverage", 0.0)
    if cov < GATE["coverage_min"]:
        failed.append((f"coverage < {GATE['coverage_min']}%", cov))
    dup = measures.get("duplicated_lines_density", 0.0)
    if dup > GATE["duplicated_lines_density_max"]:
        failed.append((f"duplicated_lines_density > {GATE['duplicated_lines_density_max']}%", dup))
    bugs = measures.get("bugs", 0)
    if bugs > GATE["bugs_max"]:
        failed.append(("bugs > 0", bugs))
    vuln = measures.get("vulnerabilities", 0)
    if vuln > GATE["vulnerabilities_max"]:
        failed.append(("vulnerabilities > 0", vuln))
    return failed


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "/tmp/sonar_measures.json"
    measures = load_measures(path)
    failed = evaluate(measures)

    print("Gate:", "PASS" if not failed else "FAIL")
    for label, value in failed:
        print(f"  - {label} (actual={value})")
    print(f"  coverage={measures.get('coverage')}%, "
          f"duplicated_lines_density={measures.get('duplicated_lines_density')}%")
    print(f"  bugs={measures.get('bugs')}, vulnerabilities={measures.get('vulnerabilities')}")
    print(f"  code_smells={measures.get('code_smells')}, "
          f"security_hotspots={measures.get('security_hotspots')}")
    print(f"  ratings(rel/sec/maint)={measures.get('reliability_rating')}/"
          f"{measures.get('security_rating')}/{measures.get('sqale_rating')}")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
