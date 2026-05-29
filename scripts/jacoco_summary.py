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
