# Step 10: playwright-baseline-and-final-report

## 읽어야 할 파일
- `/phases/11-ux-baseline-and-playwright/` (Playwright 도입)
- `/phases/16-design-system-v2/step0~9.md` 산출물

## 작업
**Playwright baseline 전면 갱신 + 디자인 v2 종합 보고서**.

### 1. Playwright spec 추가·갱신
기존 spec(home·itsm·itam·system·dialog) 외 새 spec:
- `dashboard.spec.ts` — KPI 카드·차트·활동 피드 시각 baseline.
- `kanban.spec.ts` — `/itsm/board` 칸반 4 컬럼.
- `gantt.spec.ts` — `/pms` 간트 차트.
- `dark-mode.spec.ts` — light/dark 토글 후 주요 라우트 시각.
- `notification.spec.ts` — 알림 패널 open/close.
- `chart-gallery.spec.ts` — `/_dev/charts` 갤러리.

### 2. baseline 갱신
```bash
cd frontend
pnpm exec playwright test --update-snapshots
```
모든 baseline 새로 저장. v1 baseline 은 `.png.bak` 으로 백업 후 교체.

### 3. before/after 비교 보고서 — `docs/DESIGN_V2_REPORT.md`
섹션:
- **v1 (이전) → v2 (현재) 비교 표**
  - HomePage: 5 카드 그리드 → 위젯 그리드 (KPI 4 + 차트 4 + 활동 피드)
  - ITSM: 그리드 only → 그리드 + 칸반 보드 + 워크플로우 패널
  - ITAM: 그리드 only → 분류 트리 + 라이프사이클 타임라인
  - PMS: 빈 페이지 → Gantt 차트
  - 색: 단일 액센트 → 모듈 컬러 5종
  - 모드: light only → light/dark/system 토글
  - 마이크로 인터랙션: 거의 없음 → Skeleton·Optimistic·Page transition·Hover detail
- **시각 baseline 스크린샷 인덱스** (Playwright `playwright-report` URL)
- **잔존 한계** (사용자 시각 검토에서 발견될 수 있는 영역):
  - 실시간 알림 (websocket) — 폴링 stub
  - 모바일 viewport baseline — 추후
  - 일러스트 자산 (벡터 그림) — 현재 lucide 아이콘 + 색 강조만
- **다음 단계 후보**: 모바일 / 일러스트 / commands palette (cmd+k) / 키보드 단축키 풀세트

### 4. UI_GUIDE 의 변경 사항 요약
docs/UI_GUIDE.md 의 v2 섹션이 적용된 결과 정착.

### 5. CLAUDE.md 갱신
- 시드 사용자 옆에 다크 모드 toggle 안내 한 줄.
- 명령어 섹션에 `pnpm e2e` 외 추가 없음 (기존 유지).

## Acceptance Criteria
```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
cd frontend
test -f e2e/dashboard.spec.ts
test -f e2e/kanban.spec.ts
test -f e2e/gantt.spec.ts
test -f e2e/dark-mode.spec.ts
test -f e2e/notification.spec.ts

# baseline 새로 만들어졌는지
ls e2e/dashboard.spec.ts-snapshots 2>/dev/null || ls -d e2e/*snapshots* 2>&1 | head -5

# 보고서
test -s ../docs/DESIGN_V2_REPORT.md
grep -q "before\|이전\|v1" ../docs/DESIGN_V2_REPORT.md
grep -q "after\|현재\|v2" ../docs/DESIGN_V2_REPORT.md

pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- baseline 자동 갱신 (CI 에서 --update-snapshots) 금지 — 본 step 의 수동 갱신만.
- v1 baseline `.png` 삭제 금지 — `.bak` 으로 보관 (롤백 가능성).
- 보고서에 실 운영 데이터 금지.
- 운영 코드 수정 금지 (본 step 은 baseline + 보고서).
