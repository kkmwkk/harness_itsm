import { test, expect } from './fixtures/auth';

test('ITSM 등록 다이얼로그 — 어두운 오버레이 + 흰 배경 카드', async ({ page, asAdmin }) => {
  await page.goto('/itsm');
  await page.getByRole('button', { name: '등록' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  // 시각 baseline 으로 투명 박살 회귀 차단
  await expect(page).toHaveScreenshot('itsm-create-dialog.png');
});
