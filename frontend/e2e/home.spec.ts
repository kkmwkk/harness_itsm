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
