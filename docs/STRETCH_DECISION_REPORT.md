# M11 Stretch 종합 결정 보고서 — WYSIWYG · BPMN

> Phase 15 (stretch-wysiwyg-bpmn) Step 2 — `stretch-decision-report`
> 작성일: 2026-05-30 · 대상: PRD §10 M11 Stretch (WYSIWYG·BPMN 엔진)
>
> 본 보고서는 M11 Stretch 두 항목의 **PoC·평가 결과를 종합하여 도입·보류를 명시적으로 결정**한다.
> 두 PoC 의 산출물은 step 0(WYSIWYG)·step 1(BPMN)이며, 본 step 은 신규 구현 없이 **결정·문서**만 다룬다.

---

## 0. 환경·평가 일시

| 항목 | 값 |
|------|-----|
| 작업 디렉토리 | `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM` |
| 브랜치 | `feat-15-stretch-wysiwyg-bpmn` |
| 평가 일시 | 2026-05-30 |
| 평가 근거 (WYSIWYG) | step 0 산출물 — `frontend/src/components/editor/WysiwygPreview.vue`, `composables/useWysiwygPreview.ts`, `/system/meta-editor/:metaId/wysiwyg` 라우트, 단위 테스트(160 PASS) |
| 평가 근거 (BPMN) | step 1 산출물 — `docs/BPMN_EVAL_NOTES.md` (자체 엔진 코드 실측 + Camunda 7 통합 시뮬) |
| 결정 기준 | ADR-016(3단계 WYSIWYG) · ADR-017(BPMN deferred) · PRD §5-4 · §10 M11 |

---

## 1. 종합 결정 (Executive Summary)

| Stretch 항목 | 결정 | 한 줄 근거 |
|--------------|------|-----------|
| **WYSIWYG (No-code 3단계)** | **보류** (M9·M10 폼 UI + 인플레이스 PoC 로 충분) | 비개발자 진입 장벽은 M9 폼 UI·M10 드래그로 이미 해소. 본격 page builder(Builder.io 급) 채택 비용 > 한계 이득. PoC 인플레이스 편집은 평가 자산으로 보존 |
| **BPMN/Camunda 엔진** | **보류** (자체 모델 점진 확장) | 자체 모델 표현력 천장(병렬·타이머 자동·서브프로세스 3/3 불가)은 확인됐으나 ADR-017 실 수요 트리거 0건. ~80MB·40여 테이블·별도 REST/UI 운영 부담이 현 수요 대비 과대 |

> **두 항목 모두 도입 보류**. 단, 각각 **PoC 자산 보존 + 자체 점진 확장 + 명시적 재도입 트리거**를 동반한
> *조건부 보류(Conditional Defer)* 다. "포기"가 아니라 "지금은 아니다 + 이런 신호가 오면 그때"의 형태로 닫는다.

---

## 2. WYSIWYG PoC 결과 (step 0)

### 2-1. 인플레이스 편집 가능 범위

step 0 의 `WysiwygPreview.vue` 는 **좌측 기존 편집기 패널(FormFieldEditor·GridColumnEditor 축약) + 우측
실 미리보기·편집 모드 토글** 구조다. 인플레이스 편집은 `useWysiwygPreview.ts` 순수 헬퍼 5종으로 한정된다:

| 기능 | 헬퍼 | 가능 |
|------|------|------|
| 폼 필드 라벨 인플레이스 편집 | `setFieldLabel` | ✅ |
| 폼 필드 삭제 | `removeFieldAt` | ✅ |
| 그리드 컬럼 헤더 라벨 편집 | `setColumnLabel` | ✅ |
| 그리드 컬럼 width(px) 편집 (flex 자동 해제) | `setColumnWidth` | ✅ |
| 그리드 컬럼 삭제 | `removeColumnAt` | ✅ |
| **신규 필드/컬럼 추가** | — | ❌ (좌측 패널 의존) |
| **레이아웃·span·중첩 구조 직접 편집** | — | ❌ (좌측 패널/JSON 의존) |
| **드래그로 순서 변경** | — | M10 좌측 패널이 담당 |

→ **PoC 는 "실 미리보기 위에서 라벨·width 인플레이스 편집 + 삭제"까지**다. 신규 추가·복잡 레이아웃은
설계대로 좌측 패널에 위임한다(step 0 한계 §2 명시 — 본격 WYSIWYG 아님).

### 2-2. UX 만족도·일관성·디버깅 난이도 (PoC 평가표)

| 평가 축 | 점수(5점) | 평가 |
|---------|-----------|------|
| **실 사용자 만족도** | 3.5 | "보이는 화면에서 바로 라벨 고치기"의 직관성은 분명한 이득. 그러나 신규 필드 추가가 안 돼 결국 좌측 패널을 오가야 함 → 단독 완결성 부족 |
| **좌측 패널과의 일관성** | 4.5 | 인플레이스 편집이 좌측 패널과 **같은 body draft 배열을 공유**해 한쪽 편집이 즉시 양쪽 반영. M9·M10 자산(FormFieldEditor·GridColumnEditor·`reorder`)을 그대로 재사용 → 일관성 높음 |
| **구현 복잡도** | 3.0 | PoC 수준(435줄 컴포넌트 + 75줄 헬퍼)은 감당 가능. 그러나 contenteditable/overlay 기반 인플레이스를 **모든 필드 타입·중첩·조건 표시까지 확장**하면 복잡도가 비선형 증가 |
| **디버깅 난이도** | 3.0 | 순수 헬퍼(불변·범위 밖 no-op)는 단위 테스트로 안정. 다만 실 DynamicPage 위 오버레이 좌표/포커스/IME(한글 조합) 이슈는 본격화 시 디버깅 부담이 큼 |

### 2-3. 권장 — **보류: 폼 UI(M9·M10)로 충분 + PoC 자산 보존**

- **M9(폼 UI)·M10(드래그)이 비개발자 핵심 요구를 이미 충족**한다. PRD §8 성공 지표("비개발자가 메타 1건
  발행 평균 10분 이내")는 phase 13·14 e2e 로 검증 완료(라벨·옵션 한글화·필드 순서·컬럼 width). WYSIWYG 가
  메우는 추가분은 "미리보기에서 바로 편집"의 **편의**이지 새로운 능력이 아니다.
- **본격 WYSIWYG(Builder.io / GrapeJS 급)은 별도 솔루션 수준 작업**(ADR-016 트레이드오프). 모든 필드 타입의
  인플레이스 편집·신규 추가·레이아웃 직접 조작·IME 안정화까지 끌어올리는 비용이 한계 이득을 초과한다.
- **PoC(`WysiwygPreview.vue` + `useWysiwygPreview.ts` + 라우트)는 제거하지 않고 평가 자산으로 보존**한다.
  라벨·width 인플레이스 편집은 이미 동작하며 향후 정식 채택 시 토대가 된다.
- **"별도 외부 도구 임베드"는 현 시점 비채택**. v2.1 의 메타 모델(JSONB body draft)과 외부 빌더의 자체
  데이터 모델 간 양방향 매핑 비용이 크고, 시각 회귀(ADR-019)·DRAFT/publish 흐름(ADR-006) 통합 부담이 있다.

---

## 3. BPMN 평가 결과 (step 1)

> 상세 측정·근거는 `docs/BPMN_EVAL_NOTES.md`. 본 절은 종합 결정 관점에서 요약한다.

### 3-1. 자체 모델 한계 측정

자체 엔진(`WorkflowEngineService.applyTransition()`)은 전이가 `next = stepIndex + 1` 단일 선형뿐이며,
`steps[]` JSONB 에 `next`·`condition`·`branch` 필드가 없다. step 1 §3 결정 기준 3요소 판정:

| # | 요구 | 자체 모델 | 판정 |
|---|------|-----------|------|
| 1 | 병렬 분기 (보안·운영팀 동시 승인) | 후속 단일 index — 동시 활성 불가 | ❌ 불가 |
| 2 | 타이머 이벤트 (24h 미응답 자동 escalation) | `findOverdue` 조회까지만, 자동 동작 없음(stub) | ❌ 불가(자동 기준) |
| 3 | 서브프로세스 (변경요청 내 위험평가 워크플로우) | 중첩 워크플로우 모델 없음 | ❌ 불가 |

→ **3/3 표현 불가** = 능력 기준(≥2) 초과. 자체 엔진의 표현력 **천장**이 코드 실측으로 확인됐다.

### 3-2. Camunda 도입 비용·이득 평가

| | 도입 시 이득 | 도입 시 비용 |
|---|---|---|
| | 병렬·서브프로세스·타이머/메시지 이벤트 표준 표현력 · Camunda Modeler 생태계 · 엔진 안정성 | `camunda-bpm-spring-boot-starter` ~80MB · `ACT_*` 40여 테이블 · `/engine-rest` 가 `ApiResponse<T>` 래퍼와 계약 불일치(어댑터 필요) · Cockpit/Tasklist 별도 인증·SPA 외 화면 · Job Executor 운영·버전 업그레이드 부담 |

**현 시점 평가: 비용 > 이득.** 실 수요가 0건인 상태에서 인프라 결 불일치(ADR-017 보류 사유)를 그대로
떠안게 된다. 선형 단계는 BPMN sequence flow 와 1:1 매핑되므로 마이그레이션은 가능하나, 병렬·서브프로세스를
실제로 쓰지 않는 한 옮겨도 표현력 이득이 없다.

### 3-3. ADR-017 트리거 미충족

| ADR-017 트리거 | 현황 | 충족 |
|----------------|------|------|
| 표현 불가 요구 3건 이상 누적 | phase 12 까지 실 요구 0건(이론적 한계만 식별) | ❌ |
| 운영 측 BPMN 모델러 사용 요구 명시 | 기록된 요구 없음 | ❌ |
| 엔터프라이즈 고객 운영 비용 정당화 | v2.1 단일 테넌트(ADR-018), 해당 없음 | ❌ |

### 3-4. 권장 — **보류 + 자체 모델 점진 확장**

- **지금 도입하면 비용 > 이득** — 실 수요 0건. ADR-017 보류 사유 유효.
- **자체 모델로 메울 수 있는 것은 BPMN 없이 먼저 메운다**: ① 이미 있는 `findOverdue()` 에 Spring
  `@Scheduled` polling + 자동 전이/알림 위임 → "타이머 미응답 escalation" 상당 부분 충족, ② `steps[]` JSONB 에
  `next`/`condition` 필드 추가 + `applyTransition` 의 `+1` 을 정의 기반 전이로 교체 → exclusive gateway 수준
  조건 분기 자체 표현. 둘 다 선형 모델의 점진 확장이라 추후 BPMN 마이그레이션을 막지 않는다.
- 보류는 **자체 모델 deprecate 가 아니다** — 자체 단계 엔진(ADR-015)은 유지·확장한다.

---

## 4. 다음 단계 권고

### 4-1. WYSIWYG — 보류 (별도 phase 신설 안 함)

- **즉시 신설할 phase 없음.** M9·M10 폼 UI 가 비개발자 요구를 충족하므로 `16-wysiwyg` 신설은 보류.
- **PoC 자산 보존**: `WysiwygPreview.vue`·`useWysiwygPreview.ts`·`/system/meta-editor/:metaId/wysiwyg`
  라우트·단위 테스트는 그대로 유지(다음 phase 또는 미래 재평가 토대).
- **재도입 트리거**(아래 중 하나 충족 시 `16-wysiwyg` PoC 실 착수):
  1. 비개발 사용자가 "좌측 패널 왕복 없이 미리보기에서 신규 필드 추가까지" 를 명시 요구할 때.
  2. 폼 UI(M9·M10)로 표현 못 하는 복잡 레이아웃(중첩 그리드·자유 배치) 요구가 누적될 때.
  3. 외부 빌더(Builder.io/GrapeJS) 임베드의 ROI 가 메타 모델 매핑 비용을 정당화할 때.

### 4-2. BPMN — 보류 (자체 확장 우선)

- **즉시 신설할 phase 없음.** `16-bpmn-camunda` 는 ADR-017 트리거 충족 시에만 착수.
- **자체 확장 우선**(별도 phase 후보, BPMN 없이): SLA 스케줄 escalation(`@Scheduled` + `findOverdue`),
  `steps[].next`/`condition` 조건 분기 필드.
- **재도입 트리거**: 병렬 분기·서브프로세스 실 수요 3건 이상 누적 **또는** 운영 측 BPMN 모델러 요구 명시
  → Camunda 7 PoC 실 착수.

---

## 5. 문서 갱신 (본 step 산출물)

| 문서 | 갱신 내용 |
|------|-----------|
| `docs/ADR.md` ADR-016 | 3단계(WYSIWYG)에 **M11 PoC 완료 — 결정: 보류** 이력 + 재도입 트리거 추가 |
| `docs/ADR.md` ADR-017 | step 1 에서 평가 이력 추가 완료. 본 step 에서 **최종 결정: 보류** 명시 보강 |
| `docs/PRD.md` §10 M11 | M11 Stretch 상태를 **"PoC 완료 — 결정: 보류(WYSIWYG·BPMN 둘 다)"** 로 갱신 |
| `docs/STRETCH_DECISION_REPORT.md` | **본 보고서** |

---

## 6. 결론

- **PRD §10 M11(WYSIWYG·BPMN Stretch) PoC 완료.** 두 항목 모두 **도입 보류**로 결정한다.
- WYSIWYG 는 M9·M10 폼 UI 가 비개발자 핵심 요구를 충족하므로 본격 채택 비용이 한계 이득을 초과한다 —
  PoC 자산은 보존하고 재도입 트리거를 명시한다.
- BPMN 은 자체 모델 표현력 천장은 확인됐으나 실 수요 0건으로 ADR-017 트리거 미충족 — 자체 모델 점진
  확장을 우선하고 병렬·서브프로세스 실 수요 누적 시 Camunda PoC 를 착수한다.
- 두 결정 모두 *조건부 보류* 다. 능력 천장이 곧 도입 시점은 아니며, **실 수요 신호가 도입 시점**임을
  ADR-016·ADR-017 에 트리거로 박아 둔다.
