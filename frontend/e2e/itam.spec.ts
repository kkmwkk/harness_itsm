import { test, expect } from './fixtures/auth';

test.describe('ITAM', () => {
  test('admin /itam 그리드 + 시드 자산', async ({ page, asAdmin }) => {
    await page.goto('/itam');
    await expect(page.getByText(/자산|ITAM/).first()).toBeVisible();
    await expect(page).toHaveScreenshot('itam-admin.png', { fullPage: true });
  });
});
