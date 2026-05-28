# Step 0: vue-vite-skeleton

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "기술 스택" (Vue 3.5+, Vite 6, TypeScript 5 strict, pnpm), "절대 규칙" (Frontend: `<script setup lang="ts">` 전용, `any` 금지, 컴포넌트 PascalCase, composable `use` 접두사, console.log 금지)
- `/docs/ARCHITECTURE.md` — §2-2 Frontend 디렉토리 구조 (components·composables·stores·types·lib·pages)
- `/docs/ADR.md` — ADR-001 (Vue 3 + Vite + shadcn/vue + Tailwind v4 채택 이유), ADR-004 (No-code 동적 렌더링 원칙 — 신규 화면은 Vue 파일 새로 작성하지 않는다)

레포 현황:
- 작업 루트: `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM/`
- 이미 존재: `backend/`(M1 완료), `docs/`, `phases/`, `scripts/`, `sql/`, `docker-compose.yml`
- 이번 phase 에서 처음 생성: `frontend/`
- Node.js 는 시스템에 v24 가 설치되어 있다고 가정 (CLAUDE.md "Node.js v24 (공용 운영 버전 — 변경 금지)")

## 작업

이 step 의 목적은 **Vite 6 + Vue 3.5 + TypeScript strict 의 빈 프론트엔드 스켈레톤을 만들고, `pnpm install && pnpm type-check && pnpm build` 가 통과하게 만드는 것**이다. 이 step 에서는 Tailwind·shadcn·라우터·Pinia·API 클라이언트는 도입하지 않는다.

### 1. 디렉토리·도구 준비

루트에 `frontend/` 디렉토리를 만든다. pnpm 을 사용한다 (시스템에 미설치면 `npm install -g pnpm` 또는 corepack 으로 활성화).

### 2. 프로젝트 초기화

`pnpm create vite frontend --template vue-ts` 로 초기화하되, 결과를 그대로 두지 않고 다음을 수동 정리한다:

- `frontend/package.json` 의 `name` 을 `"itg-frontend"`, `private: true`, `type: "module"`.
- `vue@^3.5`, `vite@^6`, `typescript@~5.6`, `vue-tsc@^2`, `@vitejs/plugin-vue@^5` 로 버전 핀.
- 스크립트는 다음을 포함하라:
  ```json
  {
    "scripts": {
      "dev": "vite",
      "build": "vue-tsc -b && vite build",
      "preview": "vite preview --port 5173",
      "type-check": "vue-tsc --noEmit -p tsconfig.app.json",
      "lint": "eslint . --max-warnings=0",
      "format": "prettier --write \"src/**/*.{ts,vue,css}\""
    }
  }
  ```
- 기본 생성된 `src/App.vue`·`src/main.ts`·`src/components/HelloWorld.vue` 는 정리하고, 이번 step 에서는:
  - `src/main.ts` — `createApp(App).mount('#app')`
  - `src/App.vue` — `<script setup lang="ts"> ... </script>` + 한 줄 메시지 "ITG Frontend — Foundation step 0" (Tailwind 없이 plain HTML)
  - `src/vite-env.d.ts` 유지
- `src/style.css` 의 Vite 템플릿 기본 스타일은 모두 삭제 (다음 step 의 Tailwind v4 가 책임진다). 빈 파일로 두거나 한 줄 코멘트만.

### 3. TypeScript strict 설정

`frontend/tsconfig.json` (루트 tsconfig, references 만 정의):

```json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
```

`frontend/tsconfig.app.json`:

```json
{
  "extends": "@vue/tsconfig/tsconfig.dom.json",
  "compilerOptions": {
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo",
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "strict": true,
    "noImplicitAny": true,
    "noUncheckedIndexedAccess": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "exactOptionalPropertyTypes": true,
    "useDefineForClassFields": true,
    "isolatedModules": true,
    "verbatimModuleSyntax": true,
    "skipLibCheck": true,
    "baseUrl": ".",
    "paths": { "@/*": ["./src/*"] }
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue", "src/**/*.d.ts"],
  "exclude": ["src/**/__tests__/*"]
}
```

`frontend/tsconfig.node.json` (vite.config.ts 전용):

```json
{
  "extends": "@tsconfig/node22/tsconfig.json",
  "include": ["vite.config.ts"],
  "compilerOptions": {
    "composite": true,
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.node.tsbuildinfo",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "types": ["node"]
  }
}
```

> `noUncheckedIndexedAccess` 와 `exactOptionalPropertyTypes` 는 strict 의 권장 옵션. 향후 메타 본문 접근 시 안전성을 높인다.

### 4. Vite 설정

`frontend/vite.config.ts`:

```ts
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'node:path';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    strictPort: true,
  },
});
```

> `strictPort: true` 로 5173 점유 시 즉시 실패 — 다른 포트로 흘러가는 사고 방지.

### 5. ESLint + Prettier

`frontend/.eslintrc.cjs` 또는 (권장) flat config `frontend/eslint.config.js` 로 ESLint 9 사용:

```js
import vue from 'eslint-plugin-vue';
import ts from 'typescript-eslint';

export default ts.config(
  { ignores: ['dist', 'node_modules'] },
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
    },
  },
);
```

> `no-console: error` (allow warn/error) — CLAUDE.md 절대 규칙 (`console.log` 운영 코드 잔류 금지). `@typescript-eslint/no-explicit-any` 도 `error` — `any` 금지 규칙.

`frontend/.prettierrc.json`:

```json
{
  "singleQuote": true,
  "semi": true,
  "trailingComma": "all",
  "printWidth": 100
}
```

`frontend/.editorconfig` (옵션이지만 권장):

```ini
root = true
[*]
charset = utf-8
indent_style = space
indent_size = 2
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
```

### 6. `.gitignore` (frontend)

`frontend/.gitignore`:

```
node_modules
dist
dist-ssr
*.local
.DS_Store
.vite
.cache
.eslintcache
.tsbuildinfo
node_modules/.tmp
```

> 루트 `.gitignore` 는 손대지 않는다 (이미 backend 와 docker 항목 정리됨). frontend 만 별도.

### 7. pnpm 워크스페이스 (선택, 권장)

루트에 `pnpm-workspace.yaml`:

```yaml
packages:
  - 'frontend'
```

> 향후 추가 패키지(예: shared types, e2e tests) 를 받아도 자연스럽게 확장. 단, 이번 step 에서는 `frontend` 하나만.

### 8. 부팅 검증용 컨텐츠

`src/App.vue` 최소 형태:

```vue
<script setup lang="ts">
const stage = 'ITG Frontend — Foundation step 0';
</script>

<template>
  <main>
    <h1>{{ stage }}</h1>
    <p>Vue 3 · Vite 6 · TypeScript strict · pnpm</p>
  </main>
</template>
```

> 스타일·외부 자원 일체 없음. Tailwind 적용은 step 1.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 설치
pnpm install

# 2) 타입 체크 — 0 errors
pnpm type-check

# 3) 린트 — 0 errors / 0 warnings
pnpm lint

# 4) 빌드 — dist/ 생성 성공
pnpm build
test -f dist/index.html

# 5) dev 서버 부팅 확인 (백그라운드, 5173 strict)
pnpm dev &
DEV_PID=$!
sleep 5
curl -fsS -o /dev/null -w '%{http_code}\n' http://localhost:5173 | grep -q '200'
kill $DEV_PID
```

## 검증 절차

1. 위 AC 커맨드를 순서대로 실행한다. 모든 단계가 통과해야 한다.
2. 아키텍처 체크리스트:
   - `frontend/package.json` 의 Vue 3.5+ · Vite 6 · TypeScript 5 · vue-tsc 2 인가? `pnpm` 락파일(`pnpm-lock.yaml`) 이 만들어졌는가?
   - `tsconfig.app.json` 의 `strict: true` · `noImplicitAny: true` · `noUncheckedIndexedAccess: true` 인가?
   - ESLint 규칙에 `no-console: error`, `@typescript-eslint/no-explicit-any: error` 가 들어있는가? (CLAUDE.md 절대 규칙)
   - `src/App.vue` 가 `<script setup lang="ts">` 형식인가? (Options API 사용 금지)
   - `node_modules/`·`dist/`·`pnpm-lock.yaml` 중 `pnpm-lock.yaml` 만 git 에 커밋되도록 `.gitignore` 정합한가? (`pnpm-lock.yaml` 는 커밋한다)
3. 결과에 따라 `phases/1-frontend-foundation/index.json` 의 step 0 을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "frontend/ Vite 6 + Vue 3.5 + TypeScript 5 strict + pnpm 스켈레톤 + ESLint flat config (no-console·no-explicit-any error) + Prettier + tsconfig 분리(app/node), strict server port 5173, pnpm type-check·lint·build 통과"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`
   - 사용자 개입 필요 (Node v24 미설치, pnpm 권한 문제 등) → `"status": "blocked"`, `"blocked_reason": "<구체적 사유>"` 후 즉시 중단

## 금지사항

- `backend/`·`sql/`·`docker-compose.yml`·`docs/`·`CLAUDE.md` 를 수정하지 마라. 이유: 이번 phase 의 범위는 `frontend/` 한정.
- Tailwind CSS·shadcn/vue·Pinia·Vue Router·VueUse·VeeValidate·Zod 를 이 step 에서 설치하지 마라. 이유: 각각 step 1·2·3·4 의 책임. 한 step 에 한 레이어 원칙.
- Options API (`export default { data() { ... } }`) 를 사용하지 마라. CLAUDE.md 절대 규칙. `<script setup lang="ts">` 전용.
- `any` 타입을 사용하지 마라. ESLint 가 `error` 로 강제. 불가피하면 `unknown` 후 좁히기.
- `console.log` 를 운영 코드에 남기지 마라. ESLint 가 error. 디버그용은 `console.warn`/`console.error` 만 (의도가 명확할 때).
- npm/yarn 사용하지 마라. pnpm 통일. 이유: 사용자가 명시적으로 결정.
- ESLint 의 `no-explicit-any` 를 `warn` 으로 약화하지 마라. 이유: 절대 규칙. `error` 유지.
- React 잔재(예: jsx 설정, `react` 의존성, `tsconfig` 의 `"jsx"` 옵션 활성화) 를 남기지 마라. 이유: 순수 Vue 3.
- 운영 산출물(`dist/`·`node_modules/`·`.vite/`·`.tsbuildinfo`) 을 git 에 커밋하지 마라.
- `pnpm-lock.yaml` 을 `.gitignore` 에 넣지 마라. 이유: 재현 가능한 빌드를 위해 락파일은 커밋한다.
