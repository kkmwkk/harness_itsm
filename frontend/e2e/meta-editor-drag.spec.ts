import { execFileSync } from 'node:child_process';
import { test, expect, type Page } from './fixtures/auth';

/**
 * 드래그앤드롭 메타 편집 e2e (M10 / ADR-016 2단계 · phase 14 step 3).
 * 비개발 사용자가 기존(PUBLISHED) 메타의 DRAFT 사본을 만들고,
 * 폼 필드 순서를 grip 핸들로 바꾸고(드래그와 동일한 reorder 경로) 그리드 컬럼 width 를
 * 인라인 편집한 뒤 저장(dry-run) → 발행 → viewer 에서 새 순서·width 가 반영된 것까지 검증한다.
 *
 * NOTE: 컴포넌트의 순서 변경은 SortableJS 네이티브 HTML5 DnD 로 동작하는데, Playwright 의
 * 합성 입력은 네이티브 drag 시퀀스를 발생시키지 못한다(문서화된 한계). 그래서 본 e2e 는 같은
 * grip 핸들의 내장 키보드 affordance(Alt+↓)로 순서를 바꾼다 — moveHandleDown 주석 참고.
 *
 * 시드(itg-test-meta-editor)는 phase 13 step 5 와 공유한다. 재실행 안정성을 위해
 * beforeAll 이 v1-1 을 알려진 PUBLISHED 본문으로 normalize 하고 이전 실행의 사본만 정리한다
 * (v1-1 시드 자체는 삭제하지 않는다). 테스트가 끝나면 새 버전이 PUBLISHED 로 남는다.
 */
const GROUP_ID = 'itg-test-meta-editor';
const SEED_ID = 'itg-test-meta-editor-v1-1';

/** 시드 v1-1 의 알려진 본문 — 폼 필드 3개(title/priority/content)·그리드 컬럼 2개(title/priority). */
const SEED_BODY_JSON = JSON.stringify({
  api: '/api/itg-test-meta-editor',
  form: {
    layout: 'two-column',
    fields: [
      { name: 'title', label: '제목', type: 'text', span: 1, required: true },
      {
        name: 'priority',
        label: '우선순위',
        type: 'radio',
        span: 1,
        required: false,
        options: [
          { value: 'LOW', label: '낮음' },
          { value: 'MEDIUM', label: '보통' },
          { value: 'HIGH', label: '높음' },
          { value: 'CRITICAL', label: '긴급' },
        ],
      },
      { name: 'content', label: '내용', type: 'textarea', span: 2, required: false },
    ],
  },
  grid: {
    columns: [
      { field: 'title', label: '제목', type: 'text', flex: 1 },
      { field: 'priority', label: '우선순위', type: 'priority', width: 110 },
    ],
  },
  actions: [{ id: 'create', label: '등록', type: 'dialog-form' }],
});

function psql(sql: string): void {
  execFileSync('docker', ['exec', '-i', 'itg-postgres', 'psql', '-U', 'itg', '-d', 'itgdb', '-c', sql]);
}

test.beforeAll(() => {
  // 이전 실행에서 만든 사본(v1-2+)만 제거하고, v1-1 시드는 알려진 PUBLISHED 본문으로 normalize.
  // (시드 자체는 삭제하지 않는다 — 재실행마다 결정적인 시작 상태를 보장.)
  try {
    psql(`DELETE FROM page_meta WHERE group_id='${GROUP_ID}' AND id <> '${SEED_ID}';`);
    psql(
      `INSERT INTO page_meta (id, title, system_type, package_type, group_id, major_version, minor_version, meta_status, active, meta_json) ` +
        `VALUES ('${SEED_ID}','테스트 메타','COMMON','PACKAGE','${GROUP_ID}',1,1,'PUBLISHED',true,'${SEED_BODY_JSON.replace(/'/g, "''")}'::jsonb) ` +
        `ON CONFLICT (id) DO UPDATE SET meta_status='PUBLISHED', active=true, meta_json=EXCLUDED.meta_json, title=EXCLUDED.title;`,
    );
  } catch {
    // docker 가 없으면 무시 — 실제 검증은 백엔드/DB 가동 환경에서만 의미가 있다.
  }
});

/**
 * grip 핸들을 잡아 항목을 한 칸 아래로 내린다.
 *
 * 컴포넌트의 드래그는 SortableJS 네이티브 HTML5 DnD 모드로 동작한다(드래그 시작 시 dragEl 에
 * draggable="true" 가 부여되는 것을 확인). Playwright 의 합성 입력은 브라우저의 네이티브
 * dragstart/dragover/drop 시퀀스를 발생시키지 못하므로(문서화된 한계), 합성 마우스로는 정렬이
 * 커밋되지 않는다. 따라서 e2e 는 동일한 grip 핸들에 내장된 키보드 affordance(Alt+↓)로 순서를
 * 변경한다 — 이는 드래그와 같은 reorder() 경로(useFormFieldEditor·drag.ts)를 그대로 탄다.
 */
async function moveHandleDown(page: Page, handleSel: string, idx: number): Promise<void> {
  await page.locator(handleSel).nth(idx).focus();
  await page.keyboard.press('Alt+ArrowDown');
  await page.waitForTimeout(250); // animation 200ms
}

test('비개발 사용자: grip 핸들로 필드 순서 변경 + width 인라인 편집 → 저장 → 발행 → viewer 반영', async ({
  page,
  asAdmin,
}) => {
  // 1) admin → 메타 편집기 → itg-test-meta-editor 그룹 선택 → v1-1 복사(DRAFT) → 편집 진입.
  await page.goto('/system/meta-editor');
  await page.getByText(GROUP_ID, { exact: true }).first().click();
  // 우측 버전 패널이 선택 그룹으로 전환될 때까지 대기. versions 로딩은 async 이므로
  // 다른 그룹의 stale 행이 사라지고 v1-1 단일 행(복사 버튼 1개)이 될 때까지 기다린다.
  await expect(page.getByText(`(${GROUP_ID})`)).toBeVisible();
  await expect(page.getByRole('button', { name: '복사' })).toHaveCount(1);
  await page.getByRole('button', { name: '복사' }).click();
  await expect(page.getByText('새 DRAFT 버전이 생성되었습니다.')).toBeVisible();

  // v1-1 복사 → v1-2(DRAFT, findMaxMinorVersion+1). 결정적 id 로 편집 페이지에 진입한다
  // (beforeAll 이 그룹을 v1-1 단일로 normalize 하므로 사본은 항상 v1-2).
  await page.goto(`/system/meta-editor/${GROUP_ID}-v1-2`);
  await expect(page).toHaveURL(new RegExp(`/system/meta-editor/${GROUP_ID}-v1-2$`));
  // 폼 탭이 기본 — 복사된 본문의 필드 3개가 채워진다.
  await expect(page.locator('#f-name-0')).toHaveValue('title');
  await expect(page.locator('#f-name-1')).toHaveValue('priority');
  await expect(page.locator('#f-name-2')).toHaveValue('content');

  // 2) FormFieldEditor — 1번째 필드(title)를 grip 핸들로 끝까지 내린다.
  //    [title, priority, content] → (title↓) [priority, title, content] → (title↓) [priority, content, title].
  await moveHandleDown(page, '.field-drag-handle', 0); // title: idx0 → idx1
  await expect(page.locator('#f-name-1')).toHaveValue('title');
  await moveHandleDown(page, '.field-drag-handle', 1); // title: idx1 → idx2
  // 순서 변경 확인 — 첫 카드가 priority, 마지막 카드가 title.
  await expect(page.locator('#f-name-0')).toHaveValue('priority');
  await expect(page.locator('#f-name-1')).toHaveValue('content');
  await expect(page.locator('#f-name-2')).toHaveValue('title');

  // 3) GridColumnEditor — 1번째 컬럼(title)을 px 너비 모드로 전환 후 width 200 → 150 인라인 편집.
  await page.getByRole('button', { name: '그리드 편집' }).click();
  await expect(page.locator('#c-field-0')).toHaveValue('title');
  await page.getByRole('button', { name: 'px 너비' }).first().click();
  await page.locator('#c-width-0').fill('200');
  await expect(page.locator('#c-width-0')).toHaveValue('200');
  await page.locator('#c-width-0').fill('150');
  await expect(page.locator('#c-width-0')).toHaveValue('150');

  // 4) 저장 → dry-run 통과 → 성공 토스트.
  const saveBtn = page.getByRole('button', { name: '저장', exact: true });
  await expect(saveBtn).toBeEnabled();
  await saveBtn.click();
  await expect(page.getByText('저장되었습니다.')).toBeVisible();

  // 5) 발행 → 확인 다이얼로그 → 성공 토스트.
  const publishBtn = page.getByRole('button', { name: '발행', exact: true });
  await expect(publishBtn).toBeEnabled();
  await publishBtn.click();
  const publishDialog = page.getByRole('dialog');
  await expect(publishDialog).toBeVisible();
  await publishDialog.getByRole('button', { name: '발행', exact: true }).click();
  await expect(page.getByText('배포되었습니다.', { exact: false })).toBeVisible();

  // 6) viewer 에서 새 PUBLISHED 메타 본문의 form.fields 순서·grid width 가 반영됐는지 확인.
  await page.goto(`/system/meta?groupId=${GROUP_ID}`);
  await expect(page.locator('pre')).toBeVisible();
  const json = await page.locator('pre').textContent();
  const body = JSON.parse(json ?? '{}') as {
    form: { fields: Array<{ name: string }> };
    grid: { columns: Array<{ field: string; width?: number; flex?: number }> };
  };
  expect(body.form.fields.map((f) => f.name)).toEqual(['priority', 'content', 'title']);
  expect(body.grid.columns[0].field).toBe('title');
  expect(body.grid.columns[0].width).toBe(150);
  expect(body.grid.columns[0].flex).toBeUndefined();

  // 7) 시각 baseline.
  await expect(page).toHaveScreenshot('meta-editor-drag.png', { fullPage: true });
});
