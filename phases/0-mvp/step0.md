# Step 0: project-setup

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — 프로젝트 전반 규칙, 기술 스택, 환경 변수
- `/docs/PRD.md` — TubeNote가 무엇인지
- `/docs/ARCHITECTURE.md` — 디렉토리 구조 및 패턴
- `/docs/ADR.md` — 기술 선택 이유
- `/docs/UI_GUIDE.md` — Tailwind 토큰으로 매핑할 디자인 시스템

## 작업

TubeNote 프로젝트의 초기 셋업을 수행한다. 이 step의 산출물은 "빈 페이지가 빌드되고 빈 테스트가 통과하는 상태"다.

### 1) 패키지 매니저 / 프로젝트 부트스트랩

- 패키지 매니저는 `npm` 사용. `pnpm`·`yarn`·`bun` 도입 금지.
- `package.json` 을 직접 작성하거나 `create-next-app` 으로 부트스트랩 후 불필요한 코드를 제거하라.
- Node 20 LTS 이상을 가정한다. `"engines": { "node": ">=20" }` 명시.

### 2) 의존성

런타임:
- `next@^15`
- `react@^19`, `react-dom@^19`
- `tailwindcss@^4`, `@tailwindcss/postcss@^4`, `postcss`

개발:
- `typescript@^5`
- `@types/node`, `@types/react`, `@types/react-dom`
- `vitest@^2`, `@vitejs/plugin-react`, `@testing-library/react`, `@testing-library/jest-dom`, `jsdom`
- `eslint`, `eslint-config-next`

> 후속 step 에서 추가 의존성을 깔 것이다 (`better-sqlite3`, `@google/genai`, `@notionhq/client`, `gray-matter`, `@tryfabric/martian`, `react-markdown`). 이 step에서는 위 목록만 설치한다.

### 3) TypeScript 설정 (`tsconfig.json`)

- `"strict": true` (필수 — CLAUDE.md CRITICAL)
- `"target": "ES2022"`, `"module": "esnext"`, `"moduleResolution": "bundler"`
- `"jsx": "preserve"`, `"baseUrl": "."`, `"paths": { "@/*": ["./src/*"] }`
- `"noUncheckedIndexedAccess": true` 권장.

### 4) 디렉토리 구조 생성

ARCHITECTURE.md 의 구조를 정확히 따른다:

```
src/
├── app/
│   ├── layout.tsx           # root layout
│   ├── page.tsx             # 임시 빈 페이지 ("TubeNote" 텍스트만)
│   └── globals.css          # Tailwind 진입점
├── components/              # (빈 폴더, .gitkeep)
├── lib/                     # (빈 폴더, .gitkeep)
├── services/                # (빈 폴더, .gitkeep)
└── types/                   # (빈 폴더, .gitkeep)
```

추가:
- `output/.gitkeep` (이 디렉토리는 `.gitignore` 에 이미 들어있다 — 빈 디렉토리만 유지)
- `data/.gitkeep`

### 5) Tailwind v4 + UI_GUIDE 토큰 매핑

- Tailwind v4 는 CSS-first config. `src/app/globals.css` 에서 `@import "tailwindcss"` + `@theme` 블록으로 토큰을 정의한다.
- `docs/UI_GUIDE.md` 의 다음 토큰을 그대로 매핑하라:
  - 색상: `colors.primary` (#0066cc), `colors.primary-focus` (#0071e3), `colors.primary-on-dark` (#2997ff), `colors.canvas` (#ffffff), `colors.canvas-parchment` (#f5f5f7), `colors.surface-pearl` (#fafafc), `colors.surface-tile-1` (#272729), `colors.surface-black` (#000000), `colors.ink` (#1d1d1f), `colors.body-on-dark` (#ffffff), `colors.body-muted` (#cccccc), `colors.ink-muted-80` (#333333), `colors.ink-muted-48` (#7a7a7a), `colors.hairline` (#e0e0e0)
  - radius: `sm` 8px, `md` 11px, `lg` 18px, `pill` 9999px
  - 폰트: `font-display: "SF Pro Display", system-ui, -apple-system, sans-serif`, `font-body: "SF Pro Text", system-ui, -apple-system, sans-serif`
- `postcss.config.mjs` 에 `@tailwindcss/postcss` 등록.

### 6) Vitest 셋업

- `vitest.config.ts` 작성. React 컴포넌트 테스트를 위한 `jsdom` 환경 + `@testing-library/jest-dom` 매처 셋업.
- `tsconfig.json` 의 `paths` 가 vitest 에서도 동작하도록 `vite-tsconfig-paths` 를 도입하거나 `vitest.config.ts` 의 `resolve.alias` 로 `@/` 를 매핑.
- 스모크 테스트 1개: `src/app/page.test.tsx` — 빈 페이지가 "TubeNote" 텍스트를 렌더하는지 확인.

### 7) package.json scripts

CLAUDE.md 와 정확히 동일하게:
```
"scripts": {
  "dev": "next dev",
  "build": "next build",
  "lint": "next lint",
  "test": "vitest run"
}
```

### 8) ESLint

- `next/core-web-vitals`, `next/typescript` 베이스. 추가 규칙 도입 금지.
- `.eslintrc.*` 작성.

## Acceptance Criteria

```bash
npm install
npm run build
npm test
npm run lint
```

전부 0 으로 종료해야 한다.

## 검증 절차

1. 위 AC 커맨드 4개를 순차 실행해 통과를 확인한다.
2. 아키텍처 체크리스트:
   - `src/{app,components,lib,services,types}` 디렉토리가 ARCHITECTURE.md 와 일치하는가?
   - `output/`, `data/` 디렉토리가 존재하고 `.gitignore` 에 포함되어 있는가?
   - `tsconfig.json` 의 `strict` 가 `true` 인가?
   - Tailwind v4 토큰이 UI_GUIDE.md 와 일치하는가?
3. 결과에 따라 `/phases/0-mvp/index.json` 의 step 0 을 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "Next.js 15 + TS strict + Tailwind v4 + Vitest 셋업 완료. 빈 페이지 렌더 테스트 통과."`
   - 3회 실패 → `"status": "error"`, 구체적 `error_message`
   - 외부 입력 필요 → `"status": "blocked"`, `blocked_reason`

## 금지사항

- `pnpm`, `yarn`, `bun` 도입 금지. 이유: CLAUDE.md 의 명령어가 `npm` 기준이며 1인 도구의 의존성을 늘릴 이유가 없다.
- Tailwind v3 사용 금지. 이유: Next.js 15 + React 19 + v4 가 현재 표준이며 ADR-002 의 "MVP 속도 최우선" 철학과 일치.
- `src/` 외부에 코드 파일 생성 금지. 이유: ARCHITECTURE.md 의 디렉토리 구조 일관성.
- 이 step 에서 비즈니스 로직(요약·DB·Gemini) 구현 금지. 이유: scope 위반. 후속 step의 작업이다.
- 기존 테스트를 깨뜨리지 마라.
