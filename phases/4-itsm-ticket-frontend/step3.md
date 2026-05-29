# Step 3: e2e-itsm-ticket-frontend

## 읽어야 할 파일

- `/CLAUDE.md` — "절대 규칙"
- `/docs/PRD.md` — §9 M3 (ITSM 티켓 모듈 e2e — 본 step 이 M3 마무리)
- `/docs/ADR.md` — ADR-004 (No-code), ADR-006 (DRAFT 노출 차단), ADR-007 (그리드 이원화), ADR-011 (민감정보)
- `/phases/3-itsm-ticket-backend/step3.md` — 백엔드 e2e 보고서·시드 5건
- `/phases/4-itsm-ticket-frontend/step0~2.md` — 프런트 통합 산출물 전체
- `/backend/E2E_REPORT_PHASE3.md` — 직전 백엔드 보고서 형식
- `/frontend/E2E_REPORT.md` (step4 phase 1)·`/frontend/E2E_REPORT_PHASE2.md` — 형식 참조

## 작업

이 step 의 목적은 **백엔드 + 프런트 동시 부팅 상태에서 ITSM 티켓 모듈의 e2e 가 브라우저 인터랙션까지 동작함을 시나리오로 검증하고, `frontend/E2E_REPORT_PHASE4.md` 보고서·산출물을 작성하는 것**이다. 운영 코드 변경 없음.

### 1. 사전 정리

- backend·frontend 모두 정상 부팅 가능 상태 (phase 3 의 hotfix 2건 반영, phase 4 step 0~2 산출물 적용).
- DB 의 시드(`itg-ticket-v1-1` 메타·`ITSM-SAMPLE-001~005`) 유지.

### 2. E2E 시나리오

#### A. 기본 환경
```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
BE_PID=$!
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'

cd ../frontend
pnpm install
pnpm type-check
pnpm lint
pnpm build
pnpm test
pnpm dev &
FE_PID=$!
sleep 6
```

#### B. cURL 으로 검증 가능한 부분 (자동)

1. `/itsm` SPA 라우트 200 응답.
2. 백엔드 직접 `GET /api/tickets` → `totalElements >= 5`.
3. 새 티켓 등록 시뮬레이션 — frontend 의 POST 가 동작하는지 검증을 위해 직접 백엔드에 POST:
   ```bash
   curl -fsS -X POST http://localhost:8080/api/tickets \
     -H 'Content-Type: application/json' \
     -d '{"title":"E2E Phase4 생성","content":"본문","priority":"MEDIUM","category":"REQ","assigneeId":"assignee-sample-9"}'
   ```
   응답 확인. (frontend 의 submit 통합은 수동 검증.)
4. 직후 `GET /api/tickets` → `totalElements >= 6`.
5. (선택) 정리: 위 E2E Phase4 생성 행을 시드와 구분하기 위해 보고서에 기록만 — 삭제 X (다음 phase 의 추가 시나리오에서 유용할 수 있음).

#### C. 브라우저 수동 검증 (보고서에 결과 기록)

`http://localhost:5173/itsm` 진입:

1. **그리드 5건 표시** — `ITSM-SAMPLE-001~005`. ticket_no·title·status·priority·assigneeId·createdAt 6 컬럼.
2. **status 컬럼 뱃지**: `OPEN`=접수(info), `IN_PROGRESS`=진행 중(warning), `RESOLVED`=해결됨(success), `CLOSED`=종료(neutral).
3. **priority 컬럼 뱃지**: `LOW`=낮음(무채색), `MEDIUM`=보통(무채색), `HIGH`=높음(무채색), `CRITICAL`=긴급(빨강).
4. **assigneeId null 행** (`SAMPLE-004`) 의 담당자 셀이 비어있거나 `-` 로 표시.
5. **등록 버튼 클릭** → 다이얼로그 열림 (form 6필드).
6. **빈 필수값 제출** → `title` 등 검증 메시지 (text-danger 12px).
7. **유효값 + 저장** → 성공 토스트 (우측 상단, 14px, success 보더). 다이얼로그 닫힘. 그리드에 새 행 등장. 총 6+건.
8. **백엔드 중단 후 등록 시도** → 에러 토스트 + 다이얼로그 유지.
9. **그리드 셀 클릭** → row 선택 표시 (`bg-surface-selected`).
10. **그리드 헤더 클릭(정렬)** — TanStack DataTable 의 client-side 정렬은 client 사이드, AG Grid 도 동일. 기본 동작 확인 (sort indicator + 행 순서 변경).
11. **`/system/meta?groupId=itg-ticket`** 진입 → 메타 카드 + JSON 미리보기. `metaStatus=PUBLISHED`, `v1.1`.

### 3. 결과 보고서 — `frontend/E2E_REPORT_PHASE4.md`

섹션:

```markdown
# E2E 검증 보고서 — Phase 4: itsm-ticket-frontend (M3 마무리)

## 환경
- 검증 일시
- backend Spring Boot 3.5 + Java 21 + Postgres 16 Docker
- frontend Vite 6 + Vue 3 + Tailwind v4 + shadcn/vue + TanStack + AG Grid Community
- 추가: vue-sonner (또는 shadcn toast)
- DB 시드: itg-ticket-v1-1 PUBLISHED + ITSM-SAMPLE-001~005

## 시나리오 결과 (자동 + 수동)
[표 — 위 §B·C 의 1~11 단계 PASS/FAIL]

## 핵심 검증 사실
- ADR-004 No-code 약속 이행: `/itsm` 라우트가 별도 Vue 파일 없이 router meta.groupId + DynamicPage + 백엔드 메타(itg-ticket-v1-1) + 백엔드 데이터(`/api/tickets`) 로 자동 동적 생성.
- ADR-007 그리드 이원화: itg-ticket 메타는 inlineEdit/export 없음·rows<=1000 → shadcn DataTable 분기.
- UI_GUIDE §5-5 뱃지: status/priority 컬럼이 시맨틱 토큰 pill 로 표시. 무채색 4단계 + CRITICAL 만 danger.
- UI_GUIDE §5-8 토스트: 우측 상단·14px·시맨틱 4px 보더·자동 사라짐 4초.
- 실 POST 통합: DynamicForm submit → useDataMutation → 백엔드 POST → 성공 토스트 + 그리드 reload.

## 한계 (다음 phase 범위)
- 상태 전이 UI (PATCH /status 트리거) — 본 phase 범위 아님. 다음 단계 검토.
- AG Grid 인라인 편집 실 저장.
- date/date-range/user-picker/file 의 풀 구현 (달력·사용자 검색·업로드).
- 인증 (JWT) — 별도 phase.
- ITAM 자산원장·이력 복원 시나리오 — phase 5+.

## 산출물
- `frontend/src/composables/{usePageData,useDataMutation}.ts`
- `frontend/src/components/common/{StatusBadge,PriorityBadge}.vue`
- `frontend/src/lib/badges.ts` (spec 함수)
- (sonner 또는 shadcn toast 통합)
- 본 보고서
```

### 4. 변경 가능 범위

- `frontend/E2E_REPORT_PHASE4.md` — 신규.
- (선택) 루트 `README.md` 의 현재 상태 한 줄 갱신 ("M3 완료").
- 운영 코드(composable·컴포넌트·라우터·메타·entity·service·controller·SQL) **수정 금지**.

결함 발견 시 `blocked` 처리 후 해당 step 으로 되돌린다.

## Acceptance Criteria

```bash
# 자동 검증 (위 §B 시나리오)
# 모든 cURL 단계 통과.

# 산출물
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend
test -s E2E_REPORT_PHASE4.md
grep -q "ADR-004" E2E_REPORT_PHASE4.md
grep -q "ADR-007" E2E_REPORT_PHASE4.md
grep -q "UI_GUIDE §5-5" E2E_REPORT_PHASE4.md
grep -q "ITSM-SAMPLE-" E2E_REPORT_PHASE4.md

# 정적 회귀
pnpm type-check
pnpm lint
pnpm build
pnpm test
```

수동 검증 — 위 §C 의 1~11 단계 모두 PASS 표기 (또는 결함이 있으면 blocked).

## 검증 절차

1. AC + §B 자동·§C 수동 검증 통과.
2. 아키텍처 체크리스트:
   - `/itsm` 라우트가 별도 Vue 파일 없이 동적 생성?
   - 시드 5건이 그리드에 표시되고 뱃지가 시맨틱 토큰을 따르는가?
   - 등록 폼 submit 이 실 POST 로 백엔드에 도달하고 그리드 reload 가 동작하는가?
   - 토스트가 UI_GUIDE §5-8 규격인가?
   - DRAFT 상태인 다른 메타(`itg-asset`·`itg-project` 등은 메타 미존재) 가 `/itam`·`/pms` 에서 `notPublished` 카드만 표시 — DRAFT 노출 차단 유지?
   - `system/meta?groupId=itg-ticket` 에 PUBLISHED 메타 카드 + JSON?
3. step 3 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "E2E 시나리오 11+단계 PASS — /itsm 진입 시 itg-ticket-v1-1 메타 + 시드 5건 그리드 + status/priority 뱃지 + 등록 폼 submit → 백엔드 POST → 그리드 reload + 토스트. UI_GUIDE §5-5/§5-8 규격 검증. frontend/E2E_REPORT_PHASE4.md 작성. M3 마일스톤 완료 (백엔드 + 프런트 통합)."`
   - 결함 → `"status": "blocked"`, `"blocked_reason"` 후 중단.

## 금지사항

- 운영 코드 수정 금지. 결함 발견 시 해당 step 으로 되돌린다.
- 자동화 e2e (Playwright 등) 도입 금지. 본 step 은 보고서 + cURL 자동 + 수동 시각. Playwright 도입은 별도 phase 의 ADR.
- 시나리오 종료 후 시드 메타(`itg-ticket-v1-1`·`ITSM-SAMPLE-001~005`) 정리 금지. 이유: 다음 phase 가 사용.
- E2E 보고서에 실 운영 데이터 적지 마라. 가상 샘플(`샘플 사용자`·`assignee-sample-*`).
- 새 라우트·새 엔드포인트 추가 금지.
- 상태 전이 UI 시나리오를 본 step 에 끼워넣지 마라.
- 인증 시나리오 (로그인) 추가 금지 — 다음 phase.
