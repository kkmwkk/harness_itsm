#!/usr/bin/env python3
"""test_jacoco_summary.py — jacoco_summary 의 ratio·parse 기본 동작 검증."""
import tempfile, os
from jacoco_summary import ratio, parse


def test_ratio_basic():
    assert ratio(7, 3) == 0.7
    assert ratio(0, 0) == 0.0          # 분모 0 → 0.0 (ZeroDivision 회피)
    assert ratio(5, 0) == 1.0


def test_parse_aggregates_service_and_overall():
    xml = """<?xml version="1.0"?>
<report name="t">
  <package name="com/nkia/itg/meta/service">
    <class name="com/nkia/itg/meta/service/MetaService">
      <counter type="LINE" missed="2" covered="8"/>
    </class>
  </package>
  <package name="com/nkia/itg/meta/controller">
    <class name="com/nkia/itg/meta/controller/MetaController">
      <counter type="LINE" missed="1" covered="9"/>
    </class>
  </package>
  <package name="org/other/ignored">
    <class name="org/other/ignored/X">
      <counter type="LINE" missed="100" covered="0"/>
    </class>
  </package>
</report>"""
    with tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False) as f:
        f.write(xml)
        path = f.name
    try:
        s = parse(path)
        # com/nkia 만 집계, org/other 무시 → overall 18/20
        assert s["overall"]["lines_covered"] == 17
        assert s["overall"]["lines_missed"] == 3
        # service 패키지만 → 8/10
        assert s["service"]["lines_covered"] == 8
        assert s["service"]["lines_missed"] == 2
        assert abs(s["service"]["line_ratio"] - 0.8) < 1e-9
        assert len(s["packages"]) == 2   # ignored 제외
    finally:
        os.unlink(path)


if __name__ == "__main__":
    test_ratio_basic()
    test_parse_aggregates_service_and_overall()
    print("ok")
