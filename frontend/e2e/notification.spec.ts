import { test, expect } from './fixtures/auth';

test.describe('알림 센터', () => {
  test('admin — 알림 패널 open/close', async ({ page, asAdmin }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /안녕하세요/ })).toBeVisible();
    await page.waitForLoadState('networkidle');

    // 종 클릭 → 패널 open
    await page.getByRole('button', { name: /알림/ }).first().click();
    const panel = page.getByRole('dialog', { name: '알림' });
    await expect(panel).toBeVisible();
    await expect(page).toHaveScreenshot('notification-panel-open.png');

    // 바깥 클릭 → close (onClickOutside)
    await page.mouse.click(10, 10);
    await expect(panel).toBeHidden();
  });
});
