import vue from 'eslint-plugin-vue';
import ts from 'typescript-eslint';

export default ts.config(
  {
    ignores: [
      'dist',
      'node_modules',
      'eslint.config.js',
      'src/components/ui/**',
      'src/lib/utils.ts',
      'e2e/**', // Playwright 전용 — 별도 tsconfig 미포함, lint 대상 외
      'playwright.config.ts', // Playwright 전용 설정 — tsconfig.app/node 미포함
    ],
  },
  ...ts.configs.recommendedTypeChecked,
  ...vue.configs['flat/recommended'],
  {
    languageOptions: {
      parserOptions: {
        parser: ts.parser,
        project: ['./tsconfig.app.json', './tsconfig.node.json'],
        tsconfigRootDir: import.meta.dirname,
        extraFileExtensions: ['.vue'],
      },
    },
    rules: {
      'vue/multi-word-component-names': 'off',
      'no-console': ['error', { allow: ['warn', 'error'] }],
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/consistent-type-imports': 'error',
      '@typescript-eslint/no-unsafe-argument': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
      '@typescript-eslint/no-unsafe-call': 'off',
      '@typescript-eslint/no-unsafe-return': 'off',
    },
  },
);
