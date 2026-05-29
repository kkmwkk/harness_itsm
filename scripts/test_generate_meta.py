#!/usr/bin/env python3
"""generate_meta.py 단위 테스트 — 매핑 정확도·필수 규칙 보증.

pytest / unittest 양쪽에서 동작하도록 unittest.TestCase + test_ 접두사 사용.
"""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

import generate_meta as gm  # noqa: E402

GENERATE_SCRIPT = SCRIPT_DIR / "generate_meta.py"


def _sample_spec() -> dict:
    """테스트용 OpenAPI 스키마 (request/response DTO)."""
    return {
        "components": {
            "schemas": {
                "SampleCreateRequest": {
                    "type": "object",
                    "required": ["title", "priority"],
                    "properties": {
                        "title": {"type": "string", "description": "제목", "maxLength": 200},
                        "priority": {"type": "string", "enum": ["LOW", "HIGH"], "description": "우선순위"},
                        "status": {"type": "string", "enum": ["OPEN", "CLOSED"], "description": "상태"},
                        "assigneeId": {"type": "string", "description": "담당자 ID"},
                        "longText": {"type": "string", "maxLength": 1000, "description": "긴 본문"},
                    },
                },
                "SampleSummary": {
                    "type": "object",
                    "properties": {
                        "id": {"type": "integer", "format": "int64"},
                        "title": {"type": "string", "description": "제목"},
                        "content": {"type": "string", "description": "본문"},
                        "createdAt": {"type": "string", "format": "date-time"},
                    },
                },
            }
        }
    }


class DetectFieldTypeTest(unittest.TestCase):
    def test_detect_field_type_priority_name_은_priority(self):
        self.assertEqual(gm.detect_field_type("priority", {"type": "string"}), "priority")

    def test_detect_field_type_status_name_은_status(self):
        self.assertEqual(gm.detect_field_type("status", {"type": "string"}), "status")

    def test_detect_field_type_enum_string_은_select(self):
        self.assertEqual(
            gm.detect_field_type("category", {"type": "string", "enum": ["A", "B"]}), "select"
        )

    def test_detect_field_type_date_format_은_date(self):
        self.assertEqual(
            gm.detect_field_type("createdAt", {"type": "string", "format": "date-time"}), "date"
        )
        self.assertEqual(
            gm.detect_field_type("dueDate", {"type": "string", "format": "date"}), "date"
        )

    def test_detect_field_type_number_integer_은_number(self):
        self.assertEqual(gm.detect_field_type("count", {"type": "integer"}), "number")
        self.assertEqual(gm.detect_field_type("amount", {"type": "number"}), "number")

    def test_detect_field_type_boolean_은_checkbox(self):
        self.assertEqual(gm.detect_field_type("active", {"type": "boolean"}), "checkbox")

    def test_detect_field_type_assigneeId_은_user_picker(self):
        self.assertEqual(gm.detect_field_type("assigneeId", {"type": "string"}), "user-picker")

    def test_detect_field_type_long_string_은_textarea(self):
        self.assertEqual(
            gm.detect_field_type("body", {"type": "string", "maxLength": 1000}), "textarea"
        )


class BuildFieldsTest(unittest.TestCase):
    def test_build_form_fields_required_표시(self):
        req = _sample_spec()["components"]["schemas"]["SampleCreateRequest"]
        fields = {f["name"]: f for f in gm.build_form_fields(req)}
        self.assertTrue(fields["title"].get("required"))
        self.assertTrue(fields["priority"].get("required"))
        self.assertNotIn("required", fields["assigneeId"])

    def test_build_form_fields_enum_options_생성(self):
        req = _sample_spec()["components"]["schemas"]["SampleCreateRequest"]
        fields = {f["name"]: f for f in gm.build_form_fields(req)}
        self.assertEqual(
            fields["priority"]["options"],
            [{"value": "LOW", "label": "LOW"}, {"value": "HIGH", "label": "HIGH"}],
        )

    def test_build_grid_columns_content_제외(self):
        res = _sample_spec()["components"]["schemas"]["SampleSummary"]
        cols = {c["field"] for c in gm.build_grid_columns(res)}
        self.assertNotIn("content", cols)
        self.assertIn("title", cols)


class MainCliTest(unittest.TestCase):
    def _run_cli(self, *extra, expect_ok=True):
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as tf:
            json.dump(_sample_spec(), tf, ensure_ascii=False)
            spec_path = tf.name
        cmd = [
            sys.executable, str(GENERATE_SCRIPT),
            "--openapi", spec_path,
            "--request-dto", "SampleCreateRequest",
            "--response-dto", "SampleSummary",
            "--group-id", "itg-sample",
            "--title", "샘플 화면",
            "--system-type", "ITSM",
            "--package-type", "PACKAGE",
            "--major", "1", "--minor", "1",
            "--api", "/api/samples",
            *extra,
        ]
        return subprocess.run(cmd, capture_output=True, text=True)

    def test_main_metaStatus_항상_DRAFT(self):
        r = self._run_cli()
        self.assertEqual(r.returncode, 0, r.stderr)
        meta = json.loads(r.stdout)
        self.assertEqual(meta["metaStatus"], "DRAFT")

    def test_main_id_패턴_groupId_v_major_minor(self):
        r = self._run_cli()
        meta = json.loads(r.stdout)
        self.assertEqual(meta["id"], "itg-sample-v1-1")

    def test_main_invalid_system_type_종료(self):
        with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as tf:
            json.dump(_sample_spec(), tf, ensure_ascii=False)
            spec_path = tf.name
        cmd = [
            sys.executable, str(GENERATE_SCRIPT),
            "--openapi", spec_path,
            "--request-dto", "SampleCreateRequest",
            "--response-dto", "SampleSummary",
            "--group-id", "itg-sample",
            "--title", "샘플 화면",
            "--system-type", "INVALID",
            "--package-type", "PACKAGE",
            "--major", "1", "--minor", "1",
            "--api", "/api/samples",
        ]
        r = subprocess.run(cmd, capture_output=True, text=True)
        self.assertNotEqual(r.returncode, 0)


if __name__ == "__main__":
    unittest.main()
