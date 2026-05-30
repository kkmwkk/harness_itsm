import { test, expect } from './fixtures/auth';

test.describe('대시보드 (디자인 v2 위젯 그리드)', () => {
  test('admin 홈 — KPI 카드·차트·활동 피드 위젯 그리드', async ({ page, asAdmin }) => {
    await page.goto('/');
    // 환영 헤더 + 위젯 로딩 완료 대기
    await expect(page.getByRole('heading', { name: /안녕하세요/ })).toBeVisible();
    // 폴링 fetch 가 첫 데이터를 채울 때까지 대기 (스켈레톤 → 실 위젯)
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveScreenshot('dashboard-admin.png', { fullPage: true });
  });
});
