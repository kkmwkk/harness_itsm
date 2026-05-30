import { test, expect } from './fixtures/auth';

test.describe('ITSM 칸반 보드', () => {
  test('admin /itsm/board — 보드 페이지 시각 baseline', async ({ page, asAdmin }) => {
    await page.goto('/itsm/board');
    // 보드 진입(PageHeader)은 결정적으로 노출된다.
    await expect(page.getByRole('heading', { name: 'ITSM 티켓 보드' })).toBeVisible();
    await page.waitForLoadState('networkidle');
    // NOTE: 현재 BoardPage 는 mount 시 size=20→size=500 이중 fetch 로 첫 요청이 abort 되어
    // 프로덕션 빌드에서 dataLoad 에러 카드가 노출된다(F-PH16-BOARD, DESIGN_V2_REPORT §잔존 한계).
    // 본 step 은 운영 코드 수정 금지이므로 현 상태를 baseline 으로 고정하고 결함을 보고서에 기록한다.
    await expect(page).toHaveScreenshot('kanban-board.png', { fullPage: true });
  });
});
