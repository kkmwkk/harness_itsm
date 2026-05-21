# Step 4: api-routes

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "외부 API 키와 호출은 서버 사이드에서만" CRITICAL 규칙
- `/docs/ARCHITECTURE.md` — `src/app/api/` 라우트 구조 (summarize / summaries / notion)
- `/docs/PRD.md` — 핵심 기능 흐름
- `/src/types/summary.ts` — `Summary`
- `/src/lib/db.ts` — Step 1
- `/src/services/summarizer.ts` — Step 2
- `/src/services/exporter.ts` — Step 3

Step 0~3 에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

요약 생성·조회 API 라우트 핸들러를 작성한다. 라우트는 services 를 호출만 하고 비즈니스 로직을 인라인하지 않는다 (CLAUDE.md CRITICAL).

### 1) 디렉토리 구조

```
src/app/api/
├── summarize/
│   └── route.ts          # POST: { url } → Summary
└── summaries/
    ├── route.ts          # GET: Summary[]
    └── [id]/
        └── route.ts      # GET: Summary + bodyMarkdown
```

> `notion/` 라우트는 Step 6 에서 추가한다.

### 2) `POST /api/summarize`

요청:
```ts
type RequestBody = { url: string };
```

응답 (성공 200):
```ts
type Response = Summary;
```

에러 응답:
- 400 — body 누락 / `url` 누락 / 유효하지 않은 유튜브 URL → `{ error: 'INVALID_URL', message: '...' }`
- 502 — Gemini 호출 실패 / 응답 파싱 실패 → `{ error: 'GEMINI_FAILED', message: '...' }`
- 500 — 그 외 → `{ error: 'INTERNAL', message: '...' }`

흐름:
1. `request.json()` 으로 body 파싱. 실패 시 400.
2. `summarize(url)` 호출. `InvalidUrlError` → 400, 그 외 Gemini 에러 → 502.
3. `getDb()` + `persistSummary(db, output)` 호출. 실패 시 500.
4. `Summary` 를 JSON 으로 반환.

### 3) `GET /api/summaries`

쿼리 파라미터:
- `limit` (정수, 기본 50, 최대 200)
- `offset` (정수, 기본 0)

응답: `Summary[]` 그대로.

`limit` 가 음수·문자열·200 초과 시 400.

### 4) `GET /api/summaries/[id]`

응답:
```ts
type Response = Summary & { bodyMarkdown: string };
```

- `getSummary(db, id)` 가 null 이면 404.
- `bodyMarkdown` 은 `mdPath` 에서 파일을 읽어 frontmatter 를 제거한 본문만 (gray-matter 사용).
- 파일이 사라졌으면 500 (`{ error: 'MD_MISSING' }`).

### 5) 입력 검증 헬퍼

URL 검증은 Step 2 의 `isValidYoutubeUrl` 을 재사용. limit / offset 파싱은 `src/lib/http.ts` 에 분리해도 좋고 라우트 내부 인라인이어도 좋다.

### 6) 테스트: `src/app/api/**/route.test.ts`

각 route 의 `POST` / `GET` 함수를 직접 import 해 호출하는 단위 테스트. `Request` / `Response` 는 Next.js 가 제공하는 globalThis 객체를 그대로 사용.

services 는 모두 `vi.mock` 으로 모킹:
- `@/services/summarizer` → `summarize` 가 fixed `SummarizerOutput` 반환
- `@/services/exporter` → `persistSummary` 가 fixed `Summary` 반환
- `@/lib/db` → in-memory DB 주입

케이스:
- `POST /api/summarize` 200: 정상 URL → `Summary` 반환.
- `POST /api/summarize` 400: body 누락, url 누락, `InvalidUrlError`.
- `POST /api/summarize` 502: summarize 가 Gemini 에러 throw → 502.
- `GET /api/summaries` 200: 빈 배열.
- `GET /api/summaries?limit=999` 400.
- `GET /api/summaries/[id]` 404: 없는 id.
- `GET /api/summaries/[id]` 200: 존재하는 id + `bodyMarkdown` 분리.

## Acceptance Criteria

```bash
npm test
npm run build
npm run lint
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크리스트:
   - 모든 외부 API 호출이 라우트 핸들러 → services 경로를 거치는가? (CLAUDE.md CRITICAL)
   - 클라이언트에서 직접 Gemini 를 부르는 코드가 없는가? (이 step 에서는 클라이언트 코드가 없어야 함)
   - 에러 응답이 일관된 `{ error, message }` 구조인가?
3. `/phases/0-mvp/index.json` step 4 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "src/app/api/summarize, summaries (GET list / GET by id) 라우트 작성. services 모킹 기반 라우트 핸들러 테스트 통과."`
   - 3회 실패 → `"status": "error"`

## 금지사항

- 라우트 핸들러 안에 비즈니스 로직 인라인 금지. 이유: CLAUDE.md CRITICAL — services 분리 의도 보존.
- 외부 HTTP 클라이언트 (axios, ky, got) 도입 금지. 이유: Next.js fetch / SDK 로 충분.
- API 응답에 `Summary` 외 임의 필드 추가 금지 (`bodyMarkdown` 은 detail 만 허용). 이유: 응답 스키마 일관성.
- 이 step 에서 UI / 노션 코드 작성 금지. 이유: scope 위반.
- 기존 테스트를 깨뜨리지 마라.
