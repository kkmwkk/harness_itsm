# Step 1: tailwind-v4-and-tokens

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "기술 스택" (Tailwind v4 CSS 변수 기반 테마)
- `/docs/UI_GUIDE.md` — 전체. 특히 §3 색상 토큰, §4 타이포그래피, §7 Radius 토큰, §2 금지 패턴 (그라데이션·glass morphism·gradient orb·보라/인디고·weight 500 본문 등)
- `/docs/ADR.md` — ADR-001 (Tailwind v4 채택 이유 — CSS 변수 기반 테마)
- `/phases/1-frontend-foundation/step0.md` — 이전 step 의 frontend 스켈레톤 구조

이전 step 의 결과로 `frontend/` 아래에 Vite 6 + Vue 3 + TS strict 스켈레톤이 있다. `src/App.vue` 는 plain 상태.

## 작업

이 step 의 목적은 **Tailwind CSS v4 를 frontend 에 통합하고, `UI_GUIDE.md` 의 디자인 토큰(색상·radius·타이포)을 `@theme` 블록에 CSS 변수로 매핑하며, 토큰이 올바르게 적용되는지 확인할 수 있는 미니 갤러리 페이지를 만드는 것**이다. shadcn/vue 와 폰트 임포트는 step 2.

### 1. Tailwind CSS v4 설치

Tailwind v4 는 PostCSS 가 아니라 **Vite 플러그인** 으로 동작한다 (v4 의 권장 통합). 의존성:

```bash
pnpm add -D tailwindcss@^4 @tailwindcss/vite@^4
```

> v3 의 `tailwind.config.js` / `postcss.config.cjs` 는 v4 에서 더 이상 필수가 아니다. 토큰은 CSS 의 `@theme` 블록으로 표현한다.

### 2. Vite 설정 변경

`frontend/vite.config.ts` 에 `@tailwindcss/vite` 플러그인 추가:

```ts
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import tailwindcss from '@tailwindcss/vite';
import path from 'node:path';

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: { port: 5173, strictPort: true },
});
```

### 3. 디자인 토큰 매핑 — `src/assets/styles/tokens.css`

새 디렉토리 `src/assets/styles/` 를 만들고, 다음 파일들을 둔다.

#### `src/assets/styles/tokens.css`

`UI_GUIDE.md §3·§4·§7` 의 CSS 변수를 `@theme` 블록으로 박는다. **라이트 토큰만 우선 정의**하고, 다크 토큰은 `.dark` 셀렉터 아래에 추가 (테마 토글은 이 step 범위 아님, 다음 phase 가능).

```css
@import "tailwindcss";

@theme {
  /* ── 액센트 ── */
  --color-primary:            #0066cc;
  --color-primary-hover:      #0057b3;
  --color-primary-foreground: #ffffff;

  /* ── 서피스 ── */
  --color-background:         #ffffff;
  --color-surface:            #ffffff;
  --color-surface-muted:      #f5f7fa;
  --color-surface-hover:      #eef2f7;
  --color-surface-selected:   #e6f0fb;

  /* ── 텍스트 ── */
  --color-foreground:         #1d1d1f;
  --color-foreground-muted:   #5a6270;
  --color-foreground-subtle:  #9097a3;
  --color-link:               #0066cc;

  /* ── 보더 ── */
  --color-border:             #e3e6ea;
  --color-border-subtle:      #eef0f3;
  --color-ring:               #0066cc;

  /* ── 시맨틱 (상태 전용) ── */
  --color-success:            #16a34a;
  --color-warning:            #d97706;
  --color-danger:             #dc2626;
  --color-info:               #0284c7;
  --color-neutral:            #525252;

  /* ── Radius ── */
  --radius-none:              0;
  --radius-sm:                6px;
  --radius-md:                8px;
  --radius-lg:                12px;
  --radius-pill:              9999px;
  --radius-full:              50%;

  /* ── 폰트 ── */
  --font-sans: "Pretendard Variable", "Inter", system-ui, -apple-system,
               "Segoe UI", "Apple SD Gothic Neo", sans-serif;
  --font-mono: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace;
}

/* 다크 모드 토큰 (옵션, 다음 phase 에서 토글 도입) */
.dark {
  --color-primary:            #3b9eff;
  --color-primary-hover:      #5cb0ff;
  --color-primary-foreground: #0b0d10;
  --color-background:         #0b0d10;
  --color-surface:            #15181d;
  --color-surface-muted:      #1a1e24;
  --color-surface-hover:      #1f242b;
  --color-surface-selected:   #13314f;
  --color-foreground:         #f5f7fa;
  --color-foreground-muted:   #a1a8b4;
  --color-foreground-subtle:  #6b7280;
  --color-link:               #3b9eff;
  --color-border:             #2a2f37;
  --color-border-subtle:      #1f242b;
  --color-ring:               #3b9eff;
}
```

> Tailwind v4 의 `@theme` 블록은 그대로 유틸리티 클래스로 노출된다 — 예를 들어 `--color-primary` 는 `bg-primary`·`text-primary`·`border-primary` 등으로 사용 가능. `--radius-lg` 는 `rounded-lg` 로 사용.

> Pretendard 폰트는 step 2 에서 import 한다 (CDN 또는 npm). 이 step 에서는 토큰 변수만 정의 — Pretendard 가 없으면 Inter → 시스템 폰트로 폴백.

#### `src/assets/styles/base.css`

베이스 reset + 본문 타이포 적용:

```css
@layer base {
  html, body, #app {
    height: 100%;
  }
  body {
    background-color: var(--color-background);
    color: var(--color-foreground);
    font-family: var(--font-sans);
    font-size: 14px;        /* UI_GUIDE §4-2 본문 14px */
    font-weight: 400;
    line-height: 1.5;
    -webkit-font-smoothing: antialiased;
    text-rendering: optimizeLegibility;
  }
  h1 { font-size: 28px; font-weight: 700; line-height: 1.25; letter-spacing: -0.2px; }
  h2 { font-size: 20px; font-weight: 600; line-height: 1.35; letter-spacing: -0.1px; }
  h3 { font-size: 17px; font-weight: 600; line-height: 1.4;  letter-spacing: -0.05px; }
  *:focus-visible {
    outline: 2px solid var(--color-ring);
    outline-offset: 2px;
  }
}
```

#### `src/main.ts` 에 import

```ts
import './assets/styles/tokens.css';
import './assets/styles/base.css';
import { createApp } from 'vue';
import App from './App.vue';

createApp(App).mount('#app');
```

> 이전 step 의 `src/style.css` (빈 파일) 는 삭제하거나 미사용 상태로 둔다. import 만 안 하면 번들에 포함되지 않음.

### 4. 토큰 미니 갤러리 — `src/views/_dev/TokenGallery.vue`

토큰이 실제로 동작하는지 한눈에 검증할 수 있는 단일 페이지. 라우터 도입은 step 3 이므로, 이 step 에서는 `App.vue` 에서 직접 렌더링한다 (개발 검증 전용).

요구사항:
- **색상 칩**: 모든 액센트·서피스·텍스트·보더·시맨틱 토큰을 12개 이상의 칩으로 표시. 각 칩에 토큰 이름과 hex 값 캡션.
- **Radius 샘플**: `sm`·`md`·`lg`·`pill`·`full` 5종을 별도 사각 박스로 표시.
- **타이포 위계**: H1·H2·H3·본문 14px·그리드 13px·캡션 12px·버튼 라벨 14px/500 견본.
- **금지 패턴 시각 부재 확인**: 갤러리에 그라데이션·gradient orb·glass morphism·보라/인디고 액센트가 일체 없는지 검토.

예시 스켈레톤 (시그니처):

```vue
<script setup lang="ts">
type ColorChip = { token: string; bg: string; text?: string };

const colorChips: ColorChip[] = [
  { token: '--color-primary',         bg: 'bg-primary',         text: 'text-primary-foreground' },
  { token: '--color-primary-hover',   bg: 'bg-primary-hover',   text: 'text-primary-foreground' },
  { token: '--color-background',      bg: 'bg-background',      text: 'text-foreground' },
  /* ... 나머지 토큰 ... */
];

const radii = ['sm', 'md', 'lg', 'pill', 'full'] as const;
</script>

<template>
  <main class="mx-auto max-w-5xl p-6 space-y-10">
    <section>
      <h1>Token Gallery</h1>
      <p class="text-foreground-muted">UI_GUIDE.md 토큰 적용 검증용 (dev only)</p>
    </section>

    <section>
      <h2>Color</h2>
      <div class="grid grid-cols-4 gap-3 mt-3">
        <div v-for="c in colorChips" :key="c.token"
             :class="[c.bg, c.text, 'rounded-lg border border-border p-4']">
          <div class="font-mono text-xs">{{ c.token }}</div>
        </div>
      </div>
    </section>

    <section>
      <h2>Radius</h2>
      <div class="flex gap-4 mt-3">
        <div v-for="r in radii" :key="r"
             :class="['size-16 bg-primary', 'rounded-' + r]" />
      </div>
    </section>

    <section>
      <h2>Typography</h2>
      <div class="mt-3 space-y-1">
        <h1>Heading 1 — 28px / 700</h1>
        <h2>Heading 2 — 20px / 600</h2>
        <h3>Heading 3 — 17px / 600</h3>
        <p>본문 14px / 400 — Pretendard Variable (없으면 Inter 폴백)</p>
        <p class="text-[13px]">그리드 셀 13px / 400</p>
        <p class="text-xs text-foreground-muted">캡션 12px / 400</p>
        <button class="text-[14px] font-medium text-primary">버튼 라벨 14px / 500</button>
      </div>
    </section>
  </main>
</template>
```

`src/App.vue` 는 이 갤러리만 렌더링:

```vue
<script setup lang="ts">
import TokenGallery from './views/_dev/TokenGallery.vue';
</script>

<template>
  <TokenGallery />
</template>
```

> `_dev/` 접두사는 "개발용 검증 페이지" 표식. step 3 에서 라우터 도입 후에는 `/_dev/tokens` 같은 경로로 라우팅하거나 step 4 이후 제거 검토.

### 5. ESLint·TypeScript 정합

- Tailwind v4 의 새 유틸리티(`bg-primary`·`rounded-pill` 등) 는 별도 타입 선언이 필요 없다 (Vue 의 클래스 prop 은 string).
- ESLint 가 새 import 경로(`./assets/styles/tokens.css`) 에 대해 오류를 내면, `eslint.config.js` 의 `ignores` 에 `**/*.css` 가 포함되었는지 확인 (기본적으로 포함).

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 의존성 — Tailwind v4 설치 확인
pnpm install
grep -E '"tailwindcss"|"@tailwindcss/vite"' package.json | grep -q '"\^4'

# 2) 타입 체크
pnpm type-check

# 3) 린트
pnpm lint

# 4) 빌드
pnpm build
test -f dist/index.html
# CSS 번들에 토큰 변수가 포함되었는지 — --color-primary 변수 존재 확인
grep -q "color-primary" dist/assets/*.css

# 5) dev 서버 부팅 + 갤러리 확인
pnpm dev &
DEV_PID=$!
sleep 5
curl -fsS http://localhost:5173 -o /tmp/index.html
test -s /tmp/index.html
# HTML 에 #app 진입점 존재
grep -q 'id="app"' /tmp/index.html
kill $DEV_PID
```

수동 검증 (개발자 눈):
- 브라우저에서 `http://localhost:5173` 열면 토큰 미니 갤러리가 보여야 한다.
- 색상 칩의 `--color-primary` 가 `#0066cc` 로 표시, hover 칩이 `#0057b3` 로 표시.
- radius 샘플이 `sm 6px / md 8px / lg 12px / pill 9999px / full 50%` 시각적으로 구분.
- 본문이 14px 로 표시 (그라데이션·blur·gradient orb 등 금지 패턴 흔적 없음).

## 검증 절차

1. 위 AC 커맨드를 실행한다. 모든 단계가 통과해야 한다.
2. 아키텍처 체크리스트:
   - Tailwind CSS v4 (`^4`) 와 `@tailwindcss/vite` (`^4`) 가 `devDependencies` 인가?
   - PostCSS 설정 파일(`postcss.config.js`/`postcss.config.cjs`) 을 추가하지 않았는가? (v4 는 Vite 플러그인으로 충분)
   - `src/assets/styles/tokens.css` 의 `@theme` 블록에 `UI_GUIDE.md §3·§4·§7` 의 모든 핵심 토큰이 들어있는가? (액센트·서피스·텍스트·보더·시맨틱·radius·폰트)
   - 토큰 이름이 시맨틱 기반(`--color-primary`·`--color-surface-muted`)인가? hex 값을 변수명에 박지 않았는가? (예: `--color-0066cc` 같은 금지)
   - `_dev/TokenGallery.vue` 가 라이트 토큰만 사용하며 그라데이션·blur·gradient orb 가 일체 없는가?
   - 본문 폰트가 14px / weight 400 / line-height 1.5 로 적용되는가? (UI_GUIDE §4-2)
3. 결과에 따라 `phases/1-frontend-foundation/index.json` 의 step 1 을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "Tailwind CSS v4 + @tailwindcss/vite 통합. assets/styles/tokens.css 에 @theme 토큰 매핑(액센트·서피스·텍스트·보더·시맨틱·radius·폰트). base.css 의 reset+타이포 위계. _dev/TokenGallery.vue 미니 갤러리 + App.vue 렌더링. pnpm build 후 dist CSS 에 --color-primary 변수 포함 확인."`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "<구체적 사유>"` 후 즉시 중단

## 금지사항

- Tailwind v3 의 `tailwind.config.js` 형식을 도입하지 마라. 이유: v4 는 `@theme` 블록과 Vite 플러그인 기반. config 파일을 만들면 v3 의 정신 모델을 끌고 오게 된다.
- `postcss.config.cjs` 또는 `postcss.config.js` 를 추가하지 마라. 이유: v4 는 Vite 플러그인만으로 충분.
- 그라데이션 배경·gradient orb·blur(장식 목적)·glass morphism·보라/인디고 액센트를 추가하지 마라. 이유: UI_GUIDE §2 금지 패턴.
- `--color-primary` 외의 두 번째 액센트 컬러 토큰을 추가하지 마라. 이유: UI_GUIDE §1 원칙 — 단일 액센트.
- 본문에 `font-weight: 500` 를 적용하지 마라. 이유: UI_GUIDE §4-2 — 500 은 버튼 라벨 전용으로 비워둔다.
- 본문 폰트 사이즈를 16px·15px 로 설정하지 마라. 이유: UI_GUIDE §4-2 — 본문 14px 강제.
- Pretendard 폰트를 이 step 에서 npm 또는 CDN 으로 import 하지 마라. 이유: step 2 의 책임. 토큰 변수에 이름만 박아두고 폴백(Inter, 시스템) 으로 우선 동작.
- shadcn/vue 컴포넌트(`Button`·`Input` 등) 를 이 step 에서 추가하지 마라. 이유: step 2 의 책임.
- Vue Router·Pinia·VueUse 의존성을 이 step 에서 설치하지 마라. 이유: step 3·4 의 책임.
- `App.vue` 에서 `<style scoped>` 로 색상·배경을 하드코딩하지 마라. 이유: 토큰을 통해서만 색을 표현 (Tailwind 유틸리티 또는 CSS 변수 직접 참조).
- 갤러리에 실제 운영 데이터(이름·이메일·서버명) 를 넣지 마라. 이유: ADR-011 — 가상 샘플만.
