# Step 1: playwright-setup-and-specs

## 읽어야 할 파일

- `/docs/PRD.md` §6-3 시각 회귀 자동 검증
- `/docs/ARCHITECTURE.md` §17-3 Playwright
- `/docs/ADR.md` ADR-019 (Playwright 시각 회귀)
- `/phases/11-ux-baseline-and-playwright/step0.md` — UX 카탈로그
- `/CLAUDE.md` 의 시드 사용자(`admin`/`admin-sample-1234` 등)

## 작업

이 step 의 목적은 **Playwright e2e 도구를 frontend 에 도입하고, 핵심 라우트의 시각 회귀 spec 을 작성하며, baseline 스크린샷을 생성하는 것**이다.

### 1. 의존성

```bash
cd frontend
pnpm add -D @playwright/test
npx playwright install chromium    # 또는 --with-deps (CI 에서)
```

### 2. 설정 — `frontend/playwright.config.ts`

```ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,                    // 백엔드 상태 의존 — 직렬
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    viewport: { width: 1440, height: 900 },
    colorScheme: 'light',
    locale: 'ko-KR',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  expect: {
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.02,             // 2% 이내 픽셀 diff 허용 (폰트 렌더 차이 흡수)
      animations: 'disabled',
    },
  },
});
```

### 3. 인증 fixture — `frontend/e2e/fixtures/auth.ts`

각 spec 이 admin·user 로 로그인 상태를 손쉽게 시작할 수 있도록.

```ts
import { test as base, type Page } from '@playwright/test';

export interface AuthOptions { username: string; password: string; }

async function loginAs(page: Page, opts: AuthOptions) {
  await page.goto('/login');
  await page.getByLabel('아이디').fill(opts.username);
  await page.getByLabel('비밀번호').fill(opts.password);
  await page.getByRole('button', { name: '로그인' }).click();
  await page.waitForURL(url => !url.pathname.startsWith('/login'));
}

export const test = base.extend<{ asAdmin: void; asUser: void }>({
  asAdmin: async ({ page }, use) => {
    await loginAs(page, { username: 'admin', password: 'admin-sample-1234' });
    await use();
  },
  asUser: async ({ page }, use) => {
    await loginAs(page, { username: 'user-sample-1', password: 'user-sample-1234' });
    await use();
  },
});
export { expect } from '@playwright/test';
```

### 4. spec 파일들 — `frontend/e2e/`

#### `home.spec.ts`
```ts
import { test, expect } from './fixtures/auth';

test.describe('Home (대시보드)', () => {
  test('비인증 접근은 /login 으로 리다이렉트', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login(\?|$)/);
    await expect(page).toHaveScreenshot('login.png');
  });

  test('admin 로그인 후 홈 카드 그리드', async ({ page, asAdmin }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /대시보드|Polestar/ })).toBeVisible();
    await expect(page).toHaveScreenshot('home-admin.png', { fullPage: true });
  });
});
```

#### `itsm.spec.ts`
```ts
import { test, expect } from './fixtures/auth';

test.describe('ITSM', () => {
  test('admin /itsm 진입 — 메타·시드 그리드 표시', async ({ page, asAdmin }) => {
    await page.goto('/itsm');
    await expect(page.getByText(/ITSM 티켓 관리|티켓/)).toBeVisible();
    await expect(page).toHaveScreenshot('itsm-admin.png', { fullPage: true });
  });

  test('user 는 시스템 관리 메뉴 미노출', async ({ page, asUser }) => {
    await page.goto('/');
    const sidebar = page.locator('aside, nav').first();
    await expect(sidebar).not.toContainText('시스템 관리');
  });
});
```

#### `itam.spec.ts`
```ts
import { test, expect } from './fixtures/auth';

test.describe('ITAM', () => {
  test('admin /itam 그리드 + 시드 자산', async ({ page, asAdmin }) => {
    await page.goto('/itam');
    await expect(page.getByText(/자산|ITAM/)).toBeVisible();
    await expect(page).toHaveScreenshot('itam-admin.png', { fullPage: true });
  });
});
```

#### `system.spec.ts`
```ts
import { test, expect } from './fixtures/auth';

test.describe('시스템 관리', () => {
  test('admin /system/users — 시드 사용자 표시', async ({ page, asAdmin }) => {
    await page.goto('/system/users');
    await expect(page.getByText('admin')).toBeVisible();
    await expect(page).toHaveScreenshot('system-users.png', { fullPage: true });
  });

  test('admin /system/menus 메뉴 트리', async ({ page, asAdmin }) => {
    await page.goto('/system/menus');
    await expect(page.getByText('시스템 관리')).toBeVisible();
    await expect(page).toHaveScreenshot('system-menus.png', { fullPage: true });
  });
});
```

#### `dialog.spec.ts` — 사용자가 본 결함(투명) 재발 방지
```ts
import { test, expect } from './fixtures/auth';

test('ITSM 등록 다이얼로그 — 어두운 오버레이 + 흰 배경 카드', async ({ page, asAdmin }) => {
  await page.goto('/itsm');
  await page.getByRole('button', { name: '등록' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  // 시각 baseline 으로 투명 박살 회귀 차단
  await expect(page).toHaveScreenshot('itsm-create-dialog.png');
});
```

### 5. baseline 생성

```bash
cd frontend
# 백엔드 + frontend dev 가 떠 있어야 함
pnpm exec playwright test --update-snapshots
```

baseline 은 `frontend/e2e/<spec>-snapshots/*.png` 로 저장.

### 6. `package.json` scripts

```json
{
  "scripts": {
    "e2e":         "playwright test",
    "e2e:headed":  "playwright test --headed",
    "e2e:update":  "playwright test --update-snapshots",
    "e2e:report":  "playwright show-report"
  }
}
```

### 7. `.gitignore` 보강

```
frontend/playwright-report/
frontend/test-results/
```

baseline 스크린샷(`e2e/*-snapshots/`) 은 **커밋**.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 의존성
grep -q '"@playwright/test"' package.json

# 2) 설정
test -f playwright.config.ts
test -d e2e/fixtures
test -f e2e/fixtures/auth.ts
test -f e2e/home.spec.ts
test -f e2e/itsm.spec.ts
test -f e2e/itam.spec.ts
test -f e2e/system.spec.ts
test -f e2e/dialog.spec.ts

# 3) script
grep -q '"e2e":' package.json

# 4) baseline 생성 (백엔드+프런트 살아있어야 함 — 본 AC 에서는 dev 서버를 step 안에서 띄움)
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
BE_PID=$!
sleep 10
cd ../frontend
pnpm dev &
FE_PID=$!
sleep 6
pnpm exec playwright install --with-deps chromium 2>/dev/null || true
pnpm exec playwright test --update-snapshots 2>&1 | tail -20
test -d e2e/home.spec.ts-snapshots || ls e2e | grep -q snapshots
kill $FE_PID
kill $BE_PID

# 5) 정적
pnpm type-check
```

> Playwright chromium 다운로드는 처음 한 번. CI 환경에서는 `--with-deps` 가 필요할 수 있음.

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - 5 spec 파일 — home/itsm/itam/system/dialog?
   - asAdmin/asUser fixture 가 LoginPage 의 라벨·버튼명과 매칭?
   - baseline 스크린샷이 spec 별로 생성?
   - `maxDiffPixelRatio: 0.02` 등 임계치 합리적?
   - dialog.spec 이 사용자가 본 투명 박살 회귀 방지하는 baseline?
3. step 1 업데이트:
   - 성공 → `"summary": "@playwright/test 도입 + playwright.config.ts (1440x900, 한국어 locale, maxDiffPixelRatio 2%) + e2e/fixtures/auth.ts (asAdmin/asUser fixture) + 5 spec 파일(home/itsm/itam/system/dialog) + baseline 스크린샷 생성 + package.json scripts(e2e/headed/update/report) + .gitignore (report/test-results)."`

## 금지사항

- baseline 을 `.gitignore` 에 넣지 마라 — 회귀 비교 기준.
- spec 안에 시드 데이터 직접 INSERT 금지 — 백엔드 시드(M7) 활용.
- 시각 스크린샷 외에 video 자동 캡처 금지 (디스크 부담).
- 다국어·외부 IDP·복잡 시나리오 spec 추가 금지 — 본 step 은 시각 회귀 baseline.
- 백엔드 코드 수정 금지.
- chromium 외 다른 브라우저 추가 금지 — 본 phase 는 chromium 만 (CI 단순화).
