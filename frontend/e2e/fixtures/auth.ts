import { test as base, type Page } from '@playwright/test';

export interface AuthOptions {
  username: string;
  password: string;
}

async function loginAs(page: Page, opts: AuthOptions) {
  await page.goto('/login');
  await page.getByLabel('아이디').fill(opts.username);
  await page.getByLabel('비밀번호').fill(opts.password);
  await page.getByRole('button', { name: '로그인' }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}

export const test = base.extend<{ asAdmin: void; asUser: void }>({
  asAdmin: async ({ page }, use) => {
    await loginAs(page, { username: 'admin', password: 'admin-sample-1234' });
    await use();
  },
  asUser: async ({ page }, use) => {
    await loginAs(page, { username: 'user-sample-1', password: 'user-sample-1234' });
    await use();
  },
});

export { expect } from '@playwright/test';
