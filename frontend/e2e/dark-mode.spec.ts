import { test, expect } from './fixtures/auth';

test.describe('다크 모드 토글', () => {
  test('admin 홈 — 다크 모드 전환 후 시각', async ({ page, asAdmin }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /안녕하세요/ })).toBeVisible();
    // TopBar 의 ThemeToggle 에서 다크 모드 선택
    await page.getByRole('button', { name: '다크 모드' }).click();
    await expect(page.locator('html')).toHaveClass(/dark/);
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveScreenshot('dashboard-dark.png', { fullPage: true });
  });

  test('admin ITSM — 다크 모드 그리드 시각', async ({ page, asAdmin }) => {
    await page.goto('/');
    await page.getByRole('button', { name: '다크 모드' }).click();
    await expect(page.locator('html')).toHaveClass(/dark/);
    await page.goto('/itsm');
    await expect(page.getByText(/ITSM 티켓 관리|티켓/).first()).toBeVisible();
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveScreenshot('itsm-dark.png', { fullPage: true });
  });
});
