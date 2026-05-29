import { test, expect } from './fixtures/auth';

test.describe('ITSM', () => {
  test('admin /itsm 진입 — 메타·시드 그리드 표시', async ({ page, asAdmin }) => {
    await page.goto('/itsm');
    await expect(page.getByText(/ITSM 티켓 관리|티켓/).first()).toBeVisible();
    await expect(page).toHaveScreenshot('itsm-admin.png', { fullPage: true });
  });

  test('user 는 시스템 관리 메뉴 미노출', async ({ page, asUser }) => {
    await page.goto('/');
    const sidebar = page.locator('aside, nav').first();
    await expect(sidebar).not.toContainText('시스템 관리');
  });
});
