import { test, expect } from './fixtures/auth';

test.describe('차트 갤러리', () => {
  test('/_dev/charts — 데이터 시각화 컴포넌트 갤러리', async ({ page, asAdmin }) => {
    await page.goto('/_dev/charts');
    await expect(page.getByRole('heading', { name: /Charts.*Gallery/i })).toBeVisible();
    // ECharts 캔버스 렌더 안정화 대기
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);
    await expect(page).toHaveScreenshot('chart-gallery.png', { fullPage: true });
  });
});
