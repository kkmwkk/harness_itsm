# Step 2: ci-integration-and-report

## 읽어야 할 파일

- `/docs/PRD.md` §6-3·§7 비기능
- `/docs/ARCHITECTURE.md` §17 UX 베이스라인
- `/docs/ADR.md` ADR-019·ADR-020
- `/phases/11-ux-baseline-and-playwright/step0~1.md`
- `/.github/workflows/sonarqube.yml` (기존 — 갱신 대상)

## 작업

이 step 의 목적은 **GitHub Actions workflow 에 Playwright 시각 회귀를 통합하고, UX 베이스라인·결함 카탈로그·운영 가이드를 정리한 `docs/QA_REPORT.md` 를 작성**하는 것이다.

### 1. GitHub Actions 갱신 — `.github/workflows/playwright.yml` (신규)

```yaml
name: Playwright Visual Regression

on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  e2e:
    name: Frontend e2e + Visual diff
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: itgdb
          POSTGRES_USER: itg
          POSTGRES_PASSWORD: itg1234
        ports: [ 5432:5432 ]
        options: >-
          --health-cmd "pg_isready -U itg -d itgdb"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Setup JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Setup Node 24 + pnpm
        uses: actions/setup-node@v4
        with:
          node-version: '24'
      - run: npm i -g pnpm@11

      - name: Apply DB schema + seed
        run: |
          docker exec ${{ job.services.postgres.id }} \
            bash -c 'cat > /tmp/all.sql' < /dev/stdin <<'EOF'
          EOF
          for f in sql/init/*.sql; do
            docker exec -i ${{ job.services.postgres.id }} \
              psql -U itg -d itgdb < "$f"
          done

      - name: Backend build + bootRun (background)
        working-directory: backend
        run: |
          ./gradlew build -x test --no-daemon
          SPRING_PROFILES_ACTIVE=local nohup ./gradlew bootRun --no-daemon \
            > /tmp/bootrun.log 2>&1 &
          # 대기
          for i in $(seq 1 30); do
            curl -fsS http://localhost:8080/actuator/health 2>/dev/null \
              | grep -q '"status":"UP"' && break
            sleep 2
          done

      - name: Frontend install + build
        working-directory: frontend
        run: |
          pnpm install --frozen-lockfile
          pnpm build

      - name: Install Playwright browsers
        working-directory: frontend
        run: pnpm exec playwright install --with-deps chromium

      - name: Run Playwright (visual diff)
        working-directory: frontend
        env:
          E2E_BASE_URL: http://localhost:5173
        run: |
          # dev 서버를 백그라운드로
          nohup pnpm preview --port 5173 > /tmp/preview.log 2>&1 &
          for i in $(seq 1 15); do
            curl -fsS http://localhost:5173 > /dev/null 2>&1 && break
            sleep 2
          done
          pnpm exec playwright test

      - name: Upload Playwright report on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-report
          path: frontend/playwright-report
          retention-days: 7
```

> 본 workflow 는 phase 8 의 `sonarqube.yml` 과 별개. `sonarqube.yml` 은 build+test+SonarQube, 본 `playwright.yml` 은 build+e2e+visual diff.

> CI 환경 폰트·렌더링 차이로 false positive 가능 — `playwright.config.ts` 의 `maxDiffPixelRatio: 0.02` + 추후 임계치 조정.

### 2. baseline 갱신 가이드 — `docs/QA_GUIDE.md` (선택)

운영자가 의도된 UI 변경 시 baseline 을 어떻게 갱신하는지:

```
1) 로컬에서 변경된 화면 확인.
2) frontend/ 에서 backend + dev 부팅 후:
     pnpm e2e:update
3) e2e/*-snapshots/ 의 새 PNG 가 git status 에 보임.
4) PR 본문에 baseline 갱신 사유 명시.
5) 리뷰어가 diff 검토 후 머지.
```

### 3. 종합 보고서 — `docs/QA_REPORT.md`

다음 섹션:

```markdown
# QA·UX 베이스라인 보고서 (v2.1)

## 1. 적용 범위
- UX 메시지 카탈로그 — frontend/src/lib/ui-messages.ts (ADR-020)
- Playwright 시각 회귀 — frontend/e2e/ (ADR-019)
- CI 게이트: GitHub Actions playwright.yml 통합

## 2. 1차 빌드 결함 카탈로그 처리 결과 (PRD 부록 A 갱신)
| ID | 결함 | 처리 |
|---|---|---|
| F-001 | Tailwind v4 @source 누락 | ✅ hotfix |
| F-002 | DialogOverlay 너무 투명 | ✅ hotfix |
| F-003 | shadcn 시멘틱 변수 @theme 외부 | ✅ hotfix |
| F-004 | usePageMeta notPublished 매칭 좁음 | ✅ hotfix + 카탈로그 매핑 (phase 11 step 0) |
| F-005 | HomePage placeholder 잔존 | ✅ hotfix |
| F-006 | DynamicPage raw 에러 노출 | ✅ hotfix + 카탈로그 |
| F-007 | system/MetaPage 의도 불분명 | ✅ hotfix |
| F-008 | 권한 가드 부재 | ✅ M7 (phase 9·10) |
| F-009 | 자동 e2e 가 시각 결함 못 잡음 | ✅ Playwright (phase 11 step 1·2) |

## 3. Playwright spec 일람
- home.spec — 비인증 리다이렉트·admin 홈
- itsm.spec — admin 그리드·user 메뉴 차이
- itam.spec — admin 자산 그리드
- system.spec — 사용자 목록·메뉴 트리
- dialog.spec — 등록 다이얼로그 (투명 박살 회귀 차단)

## 4. baseline 운영 규칙
- baseline 변경은 PR 본문에 사유 명시.
- maxDiffPixelRatio 0.02 (2%) — 폰트 렌더링 OS 차이 흡수.
- 변경 의도 없는 차이는 자동 PR 차단.

## 5. 한계
- Playwright 가 도메인 결함(잘못된 endpoint 호출 등)은 시각만으로 못 잡음.
- 시나리오 e2e (액션 체인 검증)는 향후 ADR.
- 모바일 시각 회귀는 별도 viewport 추가 시 가능.
```

### 4. CLAUDE.md 갱신 — `pnpm e2e` 명령 추가

기존 "명령어" 섹션에 한 줄:
```
pnpm e2e        # Playwright 시각 회귀
```

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM

# 1) workflow 파일
test -f .github/workflows/playwright.yml
grep -q "playwright" .github/workflows/playwright.yml
grep -q "java-version: '21'\|java-version: 21" .github/workflows/playwright.yml

# 2) 보고서
test -s docs/QA_REPORT.md
grep -q "F-001" docs/QA_REPORT.md
grep -q "Playwright" docs/QA_REPORT.md

# 3) (선택) 가이드
test -s docs/QA_GUIDE.md || true

# 4) yaml syntax
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/playwright.yml'))"

# 5) frontend 회귀
cd frontend
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 6) CLAUDE.md 명령 추가
grep -q "pnpm e2e" /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/CLAUDE.md
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - workflow 가 backend bootRun + frontend preview 둘 다 띄운 뒤 Playwright 실행?
   - 실패 시 playwright-report artifact 업로드?
   - SonarQube workflow 와 분리?
   - QA_REPORT 가 결함 카탈로그 처리 결과 명시?
3. step 2 업데이트:
   - 성공 → `"status": "completed"`, `"summary": ".github/workflows/playwright.yml (backend bootRun + frontend preview + Playwright 시각 회귀 + 실패 시 artifact 업로드) + docs/QA_REPORT.md (F-001~F-009 처리 결과 + Playwright spec 일람 + baseline 운영 규칙 + 한계) + (선택) docs/QA_GUIDE.md + CLAUDE.md pnpm e2e 명령 추가. v2.1 UX 베이스라인·Playwright 시각 회귀 정착."`

## 금지사항

- workflow 의 PR 차단 게이트(`continue-on-error: true`) 추가 금지.
- baseline 자동 갱신(`--update-snapshots` 를 CI 에서) 금지 — 의도된 변경만 사람이 갱신.
- 동일 workflow 에 SonarQube 까지 묶지 마라 — 별도 jobs/file.
- e2e 안에 새 시드 INSERT 금지 — phase 9/10 의 시드 활용.
- 운영 코드 수정 금지.
- 보고서에 실 운영 데이터 금지.
