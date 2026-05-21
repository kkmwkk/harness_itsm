# Step 5: ui-dashboard

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "Server Components 기본, 인터랙션만 'use client'" 규칙
- `/docs/ARCHITECTURE.md` — `(dashboard)` 라우트 그룹, page 구조
- `/docs/UI_GUIDE.md` — **모든 색상·radius·타이포·여백 토큰**. 이 step 에서 가장 중요한 문서.
- `/docs/PRD.md` — 사용자 흐름
- `/src/types/summary.ts`, `/src/lib/db.ts` — Step 1
- `/src/app/api/summarize/route.ts` — Step 4

Step 0~4 에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

웹 대시보드 UI 를 작성한다. Server Component 기본, 입력 폼·상태가 있는 부분만 Client Component.

### 1) 의존성 추가

```
npm i react-markdown remark-gfm
```

### 2) 라우트 구조

```
src/app/
├── layout.tsx                       # 글로벌 layout (Step 0 에 이미 존재). 폰트·base 스타일만.
└── (dashboard)/
    ├── layout.tsx                   # 대시보드 layout (글로벌 nav, max-width container)
    ├── page.tsx                     # 메인: SubmitForm + RecentSummaries
    └── [id]/
        └── page.tsx                 # 상세: 메타 + 본문 마크다운
```

Step 0 에서 만든 `src/app/page.tsx` 는 `(dashboard)` 그룹의 `page.tsx` 와 같은 경로(`/`)를 차지하지 않는다 — `(dashboard)` 는 URL 에 나타나지 않는 라우트 그룹이므로 `src/app/page.tsx` 를 삭제하고 `(dashboard)/page.tsx` 가 `/` 를 담당하게 하라.

### 3) Server Components

#### `src/app/(dashboard)/page.tsx`

- Server Component. `getDb()` + `listSummaries(db, { limit: 30 })` 직접 호출.
- 상단: `<SubmitForm />` (Client Component, 아래 정의).
- 그 아래: 최근 요약 카드 그리드 (`<SummaryCard summary={...} />` × N).
- 카드 클릭 → `/{id}` 로 라우팅.

#### `src/app/(dashboard)/[id]/page.tsx`

- Server Component. `getSummary(db, id)` 호출. null → `notFound()`.
- 파일 시스템에서 `mdPath` 를 읽어 `gray-matter` 로 분리. 본문만 `<MarkdownRenderer body={...} />` 에 전달.
- 상단 영역에는 `colors.surface-tile-1` 다크 풀블리드 타일로 메타(title, channel, duration, oneLiner) 노출. UI_GUIDE 의 `product-tile-dark` 컴포넌트 패턴 적용.
- 본문은 `colors.canvas` 위 `max-w-[980px]` 좌측 정렬, 17px body.

### 4) Client Components

#### `src/components/SubmitForm.tsx` (`'use client'`)

- 인풋: 유튜브 URL. UI_GUIDE 의 `search-input` 스타일 (pill, 17px, 44px height).
- 제출 버튼: UI_GUIDE 의 `button-primary` (full pill, Action Blue).
- 제출 → `POST /api/summarize` 호출. 진행 중에는 버튼 disabled + 진행 인디케이터 ("요약 중 [{Ns}]" 식의 카운터).
- 성공 → `router.refresh()` + 응답 `Summary.id` 로 `/{id}` 이동.
- 실패 → 인풋 하단에 인라인 에러 메시지 (시맨틱 에러 색 #ef4444).

#### `src/components/SummaryCard.tsx` (Server Component 가능)

UI_GUIDE 의 `store-utility-card` 패턴:
- `bg-canvas border border-hairline rounded-[18px] p-6`
- 내부:
  - 한 줄 `oneLiner` (17px / 600)
  - 채널명 + duration "Xm Ys" 표기 (14px, ink-muted-48)
  - `createdAt` 상대 시간 ("3분 전")
- 카드 전체가 `<Link href={`/${id}`}>` 로 감싸짐. hover/active 효과는 UI_GUIDE 의 `transform: scale(0.95)` active 만.

#### `src/components/MarkdownRenderer.tsx` (`'use client'` 가능)

- `react-markdown` + `remark-gfm`. 클래스명만 입혀 UI_GUIDE 타이포 적용. 색·여백을 Tailwind 클래스로.
- 코드 블록·이미지·링크 등은 기본 렌더 그대로. 인라인 링크는 `colors.primary`.

### 5) 레이아웃 / 토큰 적용

- `(dashboard)/layout.tsx`: 글로벌 nav (높이 44px, `colors.surface-black`) + max-width 컨테이너. 좌측 상단 "TubeNote" 워드마크만, 우측은 빈 공간.
- 페이지 캔버스: `colors.canvas`, 섹션 패딩 48~80px.
- 카드 그리드: 데스크탑 3열 / 태블릿 2열 / 모바일 1열 (Tailwind `grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6`).
- 풀블리드 다크 타일 (상세 페이지 헤더): radius 0, padding 80px 수직.

### 6) 테스트: `src/components/*.test.tsx`

- `SubmitForm`: 빈 인풋 제출 시 버튼이 활성화되지 않거나 즉시 에러. (mock fetch)
- `SubmitForm`: fetch 성공 시 `router.push` 가 호출됨. (mock `next/navigation`)
- `SummaryCard`: 주어진 Summary 로 한 줄 요약과 채널명을 렌더.
- `MarkdownRenderer`: `## Heading` 입력 → `<h2>` 렌더.

페이지 컴포넌트 자체는 빌드 통과로 갈음 (Server Component + DB 가 결합되어 단위 테스트가 복잡함). 빌드 + lint 가 AC.

## Acceptance Criteria

```bash
npm test
npm run build
npm run lint
```

빌드는 정적 분석·타입 체크 포함. 빌드 시 페이지가 prerender 되지 않게 하기 위해 `(dashboard)/page.tsx` 와 `[id]/page.tsx` 에 다음 한 줄을 추가하라:

```ts
export const dynamic = 'force-dynamic';
```

(DB 가 빌드 타임에 비어있고, 매 요청마다 최신 데이터를 보여줘야 하기 때문.)

## 검증 절차

1. AC 통과.
2. 아키텍처 체크리스트:
   - Server Component 가 DB 를 직접 쿼리하는가? (CLAUDE.md "데이터 페칭 라이브러리 도입 금지" 와 일치)
   - Client Component 가 외부 API (Gemini 등) 를 직접 호출하지 않는가? (CRITICAL — 반드시 `/api/*` 경유)
   - UI_GUIDE 토큰을 인라인 hex 가 아닌 Tailwind 토큰으로 사용하는가?
   - 단일 액센트 `colors.primary` 외 다른 브랜드 컬러가 없는가?
3. `/phases/0-mvp/index.json` step 5 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "(dashboard) 라우트 그룹 + SubmitForm/SummaryCard/MarkdownRenderer 작성. UI_GUIDE 토큰 적용, 컴포넌트 단위 테스트 통과."`
   - 3회 실패 → `"status": "error"`

## 금지사항

- 외부 데이터 페칭 라이브러리 (SWR, TanStack Query 등) 도입 금지. 이유: CLAUDE.md 에 명시.
- 전역 상태 관리 라이브러리 (Zustand, Redux, Jotai 등) 도입 금지. 이유: 같음.
- UI 컴포넌트 라이브러리 (shadcn, MUI, Mantine 등) 도입 금지. 이유: UI_GUIDE 의 단일 디자인 시스템 일관성. 직접 작성.
- backdrop-filter 글래스 효과 / gradient 배경 / blur orb / 보라 인디고 컬러 / 모든 카드 같은 radius 도입 금지. 이유: UI_GUIDE "AI 슬롭 안티패턴".
- 본문 텍스트 16px 사용 금지. 17px / weight 400 / line-height 1.47. 이유: UI_GUIDE 타이포 규칙.
- 카드·버튼·텍스트에 box-shadow 추가 금지. 영상 썸네일에만 single shadow. 이유: UI_GUIDE elevation 규칙.
- 이 step 에서 노션 코드 작성 금지. 이유: scope 위반.
- 기존 테스트를 깨뜨리지 마라.
