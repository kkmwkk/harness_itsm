import { test, expect } from './fixtures/auth';

test.describe('PMS 간트 차트', () => {
  test('admin /pms — 프로젝트 그리드 + 간트', async ({ page, asAdmin }) => {
    await page.goto('/pms');
    await expect(page.getByText('PMS / 프로젝트').first()).toBeVisible();
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveScreenshot('pms-gantt.png', { fullPage: true });
  });
});
