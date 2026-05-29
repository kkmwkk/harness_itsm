# Step 2: stretch-decision-report

## 읽어야 할 파일
- `/docs/PRD.md` §10 M11 Stretch
- `/docs/ADR.md` ADR-015·ADR-016·ADR-017
- `/phases/15-stretch-wysiwyg-bpmn/step0~1.md`

## 작업
**M11 Stretch 종합 결정 보고서** — WYSIWYG·BPMN 의 도입·보류 결정.

### 1. 보고서 — `docs/STRETCH_DECISION_REPORT.md`

섹션:
- 환경·평가 일시
- WYSIWYG PoC 결과 (step 0):
  - 인플레이스 편집 가능 범위·UX 만족도.
  - 좌측 패널과의 일관성·디버깅 난이도.
  - 권장: 정식 채택 / 폼 UI(M9·M10) 로 충분 / 별도 외부 도구 임베드.
- BPMN 평가 결과 (step 1):
  - 자체 모델 한계 측정.
  - Camunda 도입 비용·이득 평가.
  - 권장: 도입 / 보류 / 자체 모델 확장.
- 다음 단계 권고:
  - 채택 시: 별도 phase 신설 (`16-wysiwyg` 또는 `16-bpmn-camunda`).
  - 보류 시: ADR 갱신 + 향후 트리거 조건 명시.

### 2. ADR 갱신
- ADR-016 (No-code 편집기) 의 "단계 3 WYSIWYG" 항목 결정 결과 반영.
- ADR-017 (BPMN deferred) 의 결정 트리거 갱신.

### 3. PRD §10 마일스톤 갱신
- M11 Stretch 의 상태를 "PoC 완료 — 결정: [채택/보류]" 로 명시.

## Acceptance Criteria
```bash
test -s docs/STRETCH_DECISION_REPORT.md
grep -q "WYSIWYG" docs/STRETCH_DECISION_REPORT.md
grep -q "BPMN" docs/STRETCH_DECISION_REPORT.md

# ADR 갱신
grep -q "M11\|Stretch" docs/ADR.md
```

## 금지사항
- 새 phase·구현 시작 금지 — 본 step 은 결정·문서.
- 결정 없이 보고서 종료 금지 (채택·보류 둘 중 명시).
- 운영 코드 수정 금지.
- 보고서에 실 운영 데이터 금지.
