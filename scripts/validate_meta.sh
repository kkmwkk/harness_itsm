#!/usr/bin/env bash
# validate_meta.sh — PageMeta 후보 JSON 을 dry-run 엔드포인트로 검증한다.
#
# 사용: scripts/validate_meta.sh <meta-json-file>
# 전제: bootRun 이 떠 있어야 함 (http://localhost:8080).
#
# generate_meta.py 산출물(평탄화 형태) 또는 metaJson 래퍼 형태 모두 허용된다.
# DB INSERT 는 일어나지 않는다 — 검증만 수행한다.
set -euo pipefail

META_FILE="${1:?meta JSON 파일 경로}"
BASE_URL="${ITG_BASE_URL:-http://localhost:8080}"

curl -fsS -X POST "${BASE_URL}/api/meta/dry-run" \
  -H 'Content-Type: application/json' \
  --data @"${META_FILE}" | python3 -m json.tool
