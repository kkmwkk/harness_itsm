# Step 1: bpmn-engine-evaluation

## 읽어야 할 파일
- `/docs/PRD.md` §5-3·`/docs/ARCHITECTURE.md` §15
- `/docs/ADR.md` ADR-015 자체 엔진 MVP, ADR-017 BPMN deferred
- `/phases/12-domain-depth-workflow-category/step0~6.md` 산출물

## 작업
**BPMN/Camunda 엔진 통합 평가** — 자체 단계 모델 운영 결과를 바탕으로 BPMN 도입 필요성 평가. 실 통합은 본 step 에서 하지 않음. **평가 + ADR 갱신**.

### 1. 자체 단계 모델의 한계 정량화
phase 12 e2e 보고서에서 다음 항목 측정:
- 단계 정의 표현력: 병렬·서브프로세스·타이머·메시지 이벤트 표현 가능 여부.
- SLA 처리: 현재 stub vs Camunda Timer Event.
- 동적 단계 조건 분기: assignee 동적·조건부 다음 단계 — 자체 모델로 지원 가능 여부.
- 운영 측 BPMN 모델러 요구 사항.

### 2. Camunda 7 통합 시뮬레이션 (실 통합 X)
- Camunda 7 의 의존성 추가 시 빌드·번들 크기 변화 추정 (Camunda 의 spring-boot-starter ~80MB).
- 자체 모델 → BPMN 마이그레이션 시 변환 가능성 (선형 단계는 sequence flow 와 매핑).
- 운영 인프라: Camunda 자체 DB·REST API·UI tools.

### 3. 결정 기준
- 자체 모델이 다음 3 요구사항 중 2개 이상을 표현 불가하면 BPMN 도입:
  - 병렬 분기 (예: 변경 요청에서 보안·운영팀 동시 승인).
  - 타이머 이벤트 (예: 24시간 미응답 자동 escalation).
  - 서브프로세스 (예: 변경 요청 안에 별도 위험평가 워크플로우).

### 4. PoC 산출물 — `docs/BPMN_EVAL_NOTES.md`
- 위 §1~§3 의 측정·평가 결과.
- 권장: 도입 / 보류 / 자체 모델 확장.

## Acceptance Criteria
```bash
test -s docs/BPMN_EVAL_NOTES.md
grep -q "병렬\|타이머\|서브프로세스" docs/BPMN_EVAL_NOTES.md
grep -q "ADR-017" docs/BPMN_EVAL_NOTES.md
```

## 금지사항
- Camunda 의존성 실 추가 금지 — 본 step 은 평가만.
- 자체 단계 모델 deprecate 결정 금지 — step 2 보고서가 다룸.
- 운영 코드 수정 금지.
