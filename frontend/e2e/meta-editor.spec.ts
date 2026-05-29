import { execFileSync } from 'node:child_process';
import { test, expect } from './fixtures/auth';

/**
 * No-code 메타 편집기 e2e (M9 / ADR-016 1단계 · phase 13 step 5).
 * 비개발 사용자가 JSON 한 줄 보지 않고 GUI 만으로 새 메타 그룹을 만들고
 * 폼·그리드·액션을 편집 → 저장(dry-run) → 발행 → 화면 노출까지 검증한다.
 *
 * 시드 메타(itg-test-meta-editor-*)는 보존한다(다음 phase 활용). 재실행 안정성을 위해
 * 시작 전 동일 그룹을 정리하고, 테스트가 다시 생성·발행해 PUBLISHED 로 끝나도록 한다.
 */
const GROUP_ID = 'itg-test-meta-editor';
const META_ID = 'itg-test-meta-editor-v1-1';

test.beforeAll(() => {
  // 재실행 시 그룹 ID 충돌을 피하기 위해 시작 전 정리(끝에서는 정리하지 않는다).
  try {
    execFileSync('docker', [
      'exec',
      '-i',
      'itg-postgres',
      'psql',
      '-U',
      'itg',
      '-d',
      'itgdb',
      '-c',
      `DELETE FROM page_meta WHERE group_id='${GROUP_ID}';`,
    ]);
  } catch {
    // docker 가 없거나 행이 없으면 무시 — 생성 단계에서 어차피 새로 만든다.
  }
});

test('비개발 사용자: GUI 만으로 메타 생성 → 편집 → 발행 → 화면 노출', async ({ page, asAdmin }) => {
  // 1) admin 으로 메타 편집기 진입.
  await page.goto('/system/meta-editor');
  await expect(page.getByRole('button', { name: '신규 그룹 만들기' })).toBeVisible();

  // 2) 신규 그룹 만들기 다이얼로그.
  await page.getByRole('button', { name: '신규 그룹 만들기' }).click();
  const createDialog = page.getByRole('dialog');
  await expect(createDialog).toBeVisible();

  // 3) 입력: groupId/title/systemType/packageType/version (id 는 패턴으로 자동 합성).
  await page.locator('#g-group').fill(GROUP_ID);
  await page.locator('#g-title').fill('테스트 메타');
  await page.locator('#g-system').selectOption('COMMON');
  await page.locator('#g-package').selectOption('PACKAGE');
  await page.locator('#g-major').fill('1');
  await page.locator('#g-minor').fill('1');

  // 4) 생성 → 버전 이력의 DRAFT 편집 버튼으로 편집 페이지 진입.
  await createDialog.getByRole('button', { name: '생성' }).click();
  await expect(page.getByText('새 메타 그룹(DRAFT)이 생성되었습니다.')).toBeVisible();
  await page.getByRole('button', { name: '편집', exact: true }).click();
  await page.waitForURL(`**/system/meta-editor/${META_ID}`);
  await expect(page.getByText(META_ID).first()).toBeVisible();

  // 5) FormFieldEditor — 필드 3개.
  await page.getByRole('button', { name: '필드 추가' }).click();
  await page.getByRole('button', { name: '필드 추가' }).click();
  await page.getByRole('button', { name: '필드 추가' }).click();

  //  5-a) 필드 1: title / text / 필수
  await page.locator('#f-name-0').fill('title');
  await page.locator('#f-label-0').fill('제목');
  await page.locator('#f-type-0').selectOption('text');
  await page.locator('#f-req-0').click();

  //  5-b) 필드 2: priority / radio / 옵션 4개
  await page.locator('#f-name-1').fill('priority');
  await page.locator('#f-label-1').fill('우선순위');
  await page.locator('#f-type-1').selectOption('radio');
  const optBtn = page.getByRole('button', { name: '옵션 추가' });
  for (let i = 0; i < 4; i += 1) await optBtn.click();
  const optValues = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  const optLabels = ['낮음', '보통', '높음', '긴급'];
  const valueInputs = page.getByPlaceholder('value (영문 코드)');
  const labelInputs = page.getByPlaceholder('label (표시 한글)');
  for (let i = 0; i < 4; i += 1) {
    await valueInputs.nth(i).fill(optValues[i]);
    await labelInputs.nth(i).fill(optLabels[i]);
  }

  //  5-c) 필드 3: content / textarea / 전체 폭(span 2)
  await page.locator('#f-name-2').fill('content');
  await page.locator('#f-label-2').fill('내용');
  await page.locator('#f-type-2').selectOption('textarea');
  await page.locator('input[name="f-span-2"]').nth(1).check();

  // 6) GridColumnEditor — 컬럼 2개.
  await page.getByRole('button', { name: '그리드 편집' }).click();
  await page.getByRole('button', { name: '컬럼 추가' }).click();
  await page.getByRole('button', { name: '컬럼 추가' }).click();

  //  6-a) 컬럼 1: title / text / flex 1
  await page.locator('#c-field-0').fill('title');
  await page.locator('#c-label-0').fill('제목');
  await page.locator('#c-type-0').selectOption('text');
  await page.locator('#c-flex-0').fill('1');

  //  6-b) 컬럼 2: priority / priority / width 110
  await page.locator('#c-field-1').fill('priority');
  await page.locator('#c-label-1').fill('우선순위');
  await page.locator('#c-type-1').selectOption('priority');
  await page.locator('#c-width-1').fill('110');

  // 7) ActionEditor — create / dialog-form 액션.
  await page.getByRole('button', { name: '액션 편집' }).click();
  await page.getByRole('button', { name: '액션 추가' }).click();
  await page.locator('#a-id-0').fill('create');
  await page.locator('#a-label-0').fill('등록');
  await page.locator('#a-type-0').selectOption('dialog-form');

  // 8) 저장 → dry-run 통과 → 성공 토스트.
  const saveBtn = page.getByRole('button', { name: '저장', exact: true });
  await expect(saveBtn).toBeEnabled();
  await saveBtn.click();
  await expect(page.getByText('저장되었습니다.')).toBeVisible();

  // 9) 발행 → 확인 다이얼로그 → 성공 토스트.
  const publishBtn = page.getByRole('button', { name: '발행', exact: true });
  await expect(publishBtn).toBeEnabled();
  await publishBtn.click();
  const publishDialog = page.getByRole('dialog');
  await expect(publishDialog).toBeVisible();
  await publishDialog.getByRole('button', { name: '발행', exact: true }).click();
  await expect(page.getByText('배포되었습니다.', { exact: false })).toBeVisible();

  // 10) /system/meta viewer 에서 PUBLISHED 메타 노출 확인.
  await page.goto(`/system/meta?groupId=${GROUP_ID}`);
  await expect(page.getByText(`테스트 메타 (${META_ID})`)).toBeVisible();
  await expect(page.getByText('PUBLISHED').first()).toBeVisible();

  // 11) 시각 baseline.
  await expect(page).toHaveScreenshot('meta-editor-flow.png', { fullPage: true });
});
