import { test, expect } from './fixtures/auth';

test.describe('Home (대시보드)', () => {
  test('비인증 접근은 /login 으로 리다이렉트', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login(\?|$)/);
    await expect(page).toHaveScreenshot('login.png');
  });

  test('admin 로그인 후 홈 대시보드', async ({ page, asAdmin }) => {
    await page.goto('/');
    // 디자인 v2 대시보드 재설계(step 3) — 환영 헤더로 진입 확인.
    await expect(page.getByRole('heading', { name: /안녕하세요/ })).toBeVisible();
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveScreenshot('home-admin.png', { fullPage: true });
  });
});
