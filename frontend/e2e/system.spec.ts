import { test, expect } from './fixtures/auth';

test.describe('시스템 관리', () => {
  test('admin /system/users — 시드 사용자 표시', async ({ page, asAdmin }) => {
    await page.goto('/system/users');
    await expect(page.getByText('admin').first()).toBeVisible();
    await expect(page).toHaveScreenshot('system-users.png', { fullPage: true });
  });

  test('admin /system/menus 메뉴 트리', async ({ page, asAdmin }) => {
    await page.goto('/system/menus');
    await expect(page.getByText('시스템 관리').first()).toBeVisible();
    await expect(page).toHaveScreenshot('system-menus.png', { fullPage: true });
  });
});
