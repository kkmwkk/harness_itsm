import { defineConfig, configDefaults } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import path from 'node:path';

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': path.resolve(__dirname, './src') } },
  test: {
    environment: 'node',          // 본 step 은 DOM 불필요
    globals: false,
    // e2e/ 는 Playwright 전용 — vitest(단위) 가 picking 하지 않도록 제외
    exclude: [...configDefaults.exclude, 'e2e/**'],
    coverage: { provider: 'v8' },
  },
});
