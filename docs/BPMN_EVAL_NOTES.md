# BPMN/Camunda 엔진 통합 평가 (PoC 노트)

> Phase 15 (stretch-wysiwyg-bpmn) Step 1 — `bpmn-engine-evaluation`
> 작성일: 2026-05-30 · 대상: ADR-015(자체 단계 엔진 MVP) 운영 결과 기반 BPMN 도입 필요성 평가
>
> 본 노트는 **평가만** 수행한다. Camunda 의존성 실 추가·자체 모델 deprecate 결정·운영 코드 수정은
> 하지 않는다(step 1 금지사항). 자체 모델 운영 결과(phase 12 e2e)와 코드 실측을 근거로 BPMN 도입
> 필요성을 정량 평가하고, [[adr-017-bpmn-deferred]](ADR-017) 갱신 권고를 도출한다.

---

## 0. 평가 근거 (실측 출처)

| 출처 | 내용 |
|------|------|
| `backend/E2E_REPORT_PHASE12.md` | M8 워크플로우 e2e 15/15 PASS — 선형 4단계 자동 진행·역할 라우팅·CLOSED 가드 |
| `backend/.../workflow/service/WorkflowEngineService.java` | 전이 적용 로직(`applyTransition`)·SLA 산정(`findOverdue`) |
| `backend/.../workflow/entity/WorkflowInstance.java` | 전이 매트릭스 도메인 메서드 `advance/reject/complete/reopen` |
| `docs/PRD.md` §5-3 · `docs/ARCHITECTURE.md` §15 | 워크플로우 엔진 MVP 약속 |
| `docs/ADR.md` ADR-015(자체 엔진) · ADR-017(BPMN deferred) | 결정 배경·도입 트리거 조건 |

---

## 1. 자체 단계 모델의 한계 정량화

phase 12 산출물·운영 코드 실측 기준. "표현 가능 여부"는 **현재 코드로 표현 가능한가**로 판정한다.

### 1-1. 단계 정의 표현력

자체 엔진의 전이는 `WorkflowEngineService.applyTransition()` 의 다음 코드가 전부다:

```java
int next = stepIndex + 1;   // 단일 후속 단계 — 항상 +1
instance.advance(next);
```

즉 **단일 선형 시퀀스(`index → index+1`)** 만 표현한다. `WorkflowDefinition.steps[]` JSONB 는
`index·name·assignee_role_code·sla_minutes·allowed_actions` 5 필드뿐이고, 다음 단계를 가리키는
`next`·`condition`·`branch` 같은 필드가 없다.

| BPMN 표현 요소 | 자체 모델 표현 가능 여부 | 근거 |
|----------------|--------------------------|------|
| **선형 시퀀스** (sequence flow) | ✅ 가능 | `next = stepIndex + 1` (phase 12 e2e 4단계 PASS) |
| **병렬 분기** (parallel gateway / fork-join) | ❌ 불가 | 후속 단계가 단일 정수 index. 동시 2단계 활성·join 조건 표현 불가 |
| **서브프로세스** (call activity / embedded) | ❌ 불가 | `WorkflowInstance` 는 ticket 1:1, 단계 안에 별도 워크플로우 중첩 모델 없음 |
| **타이머 이벤트** (timer boundary/intermediate) | ⚠️ 부분 | `sla_due_at` 산정·`findOverdue` 조회는 있으나, 초과 시 자동 전이·escalation 트리거는 없음(알림 stub) |
| **메시지 이벤트** (message catch/throw) | ❌ 불가 | 외부 이벤트 수신·대기 단계 모델 없음. 액션은 사용자 POST 만 |
| **조건부 분기** (exclusive gateway / 조건식) | ❌ 불가 | REJECT 가 무조건 `REJECTED` 종결(이전 단계 복귀·조건 라우팅 없음), FORWARD 도 `+1` 동일 처리 |

> ADR-015·ARCHITECTURE §15-2 의 전이 매트릭스 표는 `REJECT → 이전 단계(정의에 따라)`,
> `FORWARD → 스킵 단계 옵션` 을 언급하지만, **실제 코드는 둘 다 미구현**이다(REJECT=종결, FORWARD=+1).
> 즉 문서상 매트릭스보다 실 표현력이 더 좁다.

### 1-2. SLA 처리: 현재 stub vs Camunda Timer Event

| 항목 | 자체 모델(현재) | Camunda 7 Timer Event |
|------|-----------------|------------------------|
| SLA 마감 시각 산정 | ✅ `sla_due_at = startedAt + sla_minutes` | ✅ ISO-8601 duration (`PT24H`) |
| 초과 단계 조회 | ✅ `findOverdue()` (polling/배치용 쿼리) | ✅ 엔진 내장 Job Executor |
| **초과 시 자동 동작** | ❌ **stub** — 이메일·웹훅·자동 escalation 없음 | ✅ Timer boundary event → 자동 전이·알림 |
| 스케줄러 | ❌ 없음 (`findOverdue` 호출 주체 미구현) | ✅ Job Executor(엔진 상주 스레드) |

→ SLA 는 "마감 계산 + 초과 조회"까지만 동작. **"24시간 미응답 자동 escalation"** 은 자체 모델 미지원.
   다만 `findOverdue` 가 이미 있으므로, **Spring `@Scheduled` polling + 액션 위임**으로 자체 확장 가능
   (BPMN 없이도 메우는 길이 있음).

### 1-3. 동적 단계 조건 분기·동적 assignee

| 요구 | 자체 모델 표현 가능 여부 | 근거 |
|------|--------------------------|------|
| 조건부 다음 단계 (예: 금액 ≥ X 면 임원 승인 추가) | ❌ 불가 | 전이가 무조건 `+1`. 단계 정의에 조건식 필드 없음 |
| 동적 assignee (사용자 직접 지정) | ⚠️ 부분 | `assignee_role_code` 는 **정적 역할**만. PRD §5-3 의 `assigned_to_user_id` 직접 지정은 스키마 컬럼만 있고 엔진 미사용 |
| 단계 건너뛰기(skip) | ❌ 불가 | FORWARD 가 스킵이 아닌 단순 `+1` 로 처리 |

### 1-4. 운영 측 BPMN 모델러 요구

- phase 12 e2e·이전 phase 산출물에서 **운영/사용자의 BPMN 모델러(Camunda Modeler) 사용 요구는
  명시적으로 기록된 바 없다.** ADR-017 도입 트리거 조건 2번("운영 측 BPMN 모델러 사용 요구 명시")
  미충족.
- 현재 단계 정의는 M9/M10 No-code 편집기(폼·드래그) 와 JSONB 직접 편집으로 관리. 시각 모델러 부재가
  운영 차단 요인으로 보고된 사례 없음.

---

## 2. Camunda 7 통합 시뮬레이션 (실 통합 없음 — 추정)

### 2-1. 의존성·번들·인프라 영향 (추정)

| 항목 | 추정치 |
|------|--------|
| `camunda-bpm-spring-boot-starter` 의존 트리 | ~80MB (엔진 + REST + 내장 webapp 일부) |
| 추가 DB 오브젝트 | Camunda 엔진 전용 테이블 ~40여 개(`ACT_*`) — `itgdb` 에 공존 또는 분리 |
| 추가 REST 표면 | `/engine-rest/**` (자체 `ApiResponse<T>` 래퍼와 계약 불일치 → 어댑터 필요, ADR-009) |
| UI tools | Cockpit/Tasklist/Admin webapp — Vue SPA 와 별도 인증·별도 화면(SSO 통합 부담) |
| 운영 부담 | Job Executor 스레드 튜닝·엔진 버전 업그레이드·히스토리 레벨/클린업 정책 |

### 2-2. 자체 모델 → BPMN 마이그레이션 변환 가능성

| 자체 모델 요소 | BPMN 매핑 | 변환 난이도 |
|----------------|-----------|-------------|
| `steps[].index` 선형 단계 | `userTask` + `sequenceFlow` | 낮음 (1:1 직접 매핑) |
| `assignee_role_code` | `userTask` candidateGroups | 낮음 |
| `sla_minutes` | timer boundary event (`PT{n}M`) | 중간 (escalation 동작 신규 정의) |
| `allowed_actions` | task 의 outgoing flow/조건 | 중간 |
| `WorkflowInstance`/`InstanceStep` 이력 | `ACT_HI_*` 히스토리 | 중간 (기존 이력 이관 또는 병행 보존) |

→ **선형 단계는 BPMN sequence flow 와 직접 매핑**되므로 변환 자체는 가능(ADR-017 트레이드오프와 일치).
   다만 병렬·서브프로세스를 쓰지 않는 한 BPMN 으로 옮겨도 얻는 표현력 이득이 없다.

---

## 3. 결정 기준 적용

**기준(step 1 §3): 자체 모델이 아래 3 요구사항 중 2개 이상을 표현 불가하면 BPMN 도입.**

| # | 요구사항 | 자체 모델 표현 | 판정 |
|---|----------|----------------|------|
| 1 | **병렬 분기** (변경 요청에서 보안·운영팀 동시 승인) | 후속 단일 index — 동시 활성 불가 | ❌ **표현 불가** |
| 2 | **타이머 이벤트** (24h 미응답 자동 escalation) | `findOverdue` 조회까지만, 자동 escalation 없음 | ❌ **표현 불가** (자동 동작 기준) |
| 3 | **서브프로세스** (변경 요청 안 별도 위험평가 워크플로우) | 중첩 워크플로우 모델 없음 | ❌ **표현 불가** |

→ **3/3 표현 불가** = 기준 임계(≥2)를 **기술적으로는 초과**. 자체 엔진의 표현력 천장이 확인됐다.

### 3-1. 그러나 — 실 수요는 ADR-017 트리거 미충족

기술적 표현력 천장(3/3 불가)과 별개로, **ADR-017 의 실제 도입 트리거 조건**은 아직 충족되지 않았다:

| ADR-017 트리거 | 현황 | 충족? |
|----------------|------|-------|
| 표현 불가 워크플로우 요구가 **실제 3건 이상 누적** | phase 12 까지 **실 요구 0건** (이론적 한계로만 식별) | ❌ |
| 운영 측 BPMN 모델러 사용 요구 명시 | 기록된 요구 없음 (§1-4) | ❌ |
| 엔터프라이즈 고객 적용으로 운영 비용 정당화 | v2.1 단일 테넌트(ADR-018), 해당 없음 | ❌ |

즉 §3 의 "표현 불가 2개 이상" 기준은 **능력(capability) 관점**으로는 초과하나, ADR-017 의 트리거는
**수요(demand) 누적**을 요구한다. 현 시점 실 수요는 0건이다. 능력 천장이 곧 도입 시점은 아니다.

---

## 4. 권장 — **보류 + 자체 모델 점진 확장 (Conditional Defer)**

> 권장: **도입 보류**. 단, 자체 모델로 메울 수 있는 항목(타이머 escalation·조건 분기)을 **자체 확장**으로
> 우선 보강하고, **병렬 분기·서브프로세스의 실 수요가 ADR-017 트리거(3건 이상 누적)에 도달하면 그때
> Camunda PoC 를 실 착수**한다.

근거:
- **지금 도입하면 비용 > 이득**: 실 수요 0건인데 ~80MB 의존·40여 테이블·별도 REST/UI·운영 부담을
  떠안는다. ADR-017 의 보류 사유(인프라 결 불일치·운영 부담·라이선스)가 그대로 유효.
- **천장은 분명하다**: 병렬·서브프로세스는 자체 모델로 **원리적으로 불가**. 이 수요가 실제로 들어오면
  부분 우회가 아니라 엔진 교체가 정답이다 — 그 시점에 BPMN 을 도입한다.
- **자체 확장으로 메울 수 있는 것은 먼저 메운다**(BPMN 없이):
  1. **SLA 자동 escalation**: 이미 있는 `findOverdue()` 에 Spring `@Scheduled` polling + 알림/자동
     전이 위임을 얹으면 "타이머 미응답 escalation" 의 상당 부분을 자체로 충족. (별도 phase 책임)
  2. **조건부 분기**: `steps[]` JSONB 에 `next`/`condition` 필드를 추가하고 `applyTransition` 의
     `+1` 을 정의 기반 전이로 바꾸면 exclusive gateway 수준은 자체로 표현 가능. (별도 ADR 후보)
  3. 위 둘은 **선형 모델의 점진 확장**이라 추후 BPMN 마이그레이션(§2-2)을 막지 않는다.

도입 보류는 **자체 모델 deprecate 결정이 아니다**(step 1 금지사항 준수 — 그 결정은 step 2 보고서 책임).

---

## 5. ADR-017 갱신 권고

본 평가 결과를 ADR-017 에 평가 이력으로 누적한다:

- **결정 시점 도래·평가 완료**: M8(phase 12) 완료 후 평가 시점이 도래했고, 본 노트로 1차 평가를 수행.
- **평가 결론**: 자체 모델 표현력 천장(병렬·타이머 자동·서브프로세스 3/3 불가) 확인. 그러나 실 수요
  0건으로 도입 트리거 미충족 → **보류 + 자체 모델 점진 확장** 권고.
- **재평가 시점**: 병렬 분기·서브프로세스 실 수요가 3건 이상 누적되거나, 운영 측 BPMN 모델러 요구가
  명시될 때 Camunda 7 PoC 실 착수.

> ADR.md 본문에는 본 노트 참조 링크와 "M8 후 1차 평가: 보류" 한 줄을 추가한다(아래 step 산출물).

---

## 6. 산출물

| 산출물 | 비고 |
|--------|------|
| `docs/BPMN_EVAL_NOTES.md` | **본 노트 — §1~§3 측정·평가, §4 권장** |
| `docs/ADR.md` ADR-017 | 평가 이력 한 줄 + 본 노트 참조 추가(본 step) |

> 본 step 은 평가만 수행했다. Camunda 의존성·운영 코드·자체 모델 deprecate 는 건드리지 않았다.
