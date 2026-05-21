# Step 2: gemini-summarizer

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — `lib/` (외부 API 클라이언트) 와 `services/` (오케스트레이션) 분리 규칙, `GEMINI_API_KEY` 환경 변수
- `/docs/ARCHITECTURE.md` — `src/lib/gemini.ts` 와 `src/services/summarizer.ts` 의 역할 분리
- `/docs/ADR.md` — ADR-002 (Gemini 단일 사용, 유튜브 URL 직접 입력)
- `/src/types/summary.ts` — Step 1 의 `Summary` 타입 (참고용. 이 step 은 DB 와 무관)
- `/src/lib/db.ts` — Step 1 산출 (참고용, 호출하지 않음)

Step 0~1 에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

Gemini API 를 호출해 유튜브 URL → 구조화된 마크다운 요약을 만드는 레이어를 구현한다.

### 1) 의존성 추가

```
npm i @google/genai gray-matter
```

> `@google/genai` 는 Google Gen AI 공식 TypeScript SDK. `@google/generative-ai` (구버전) 가 아니다 — 새 SDK 를 쓴다.

### 2) Gemini 클라이언트: `src/lib/gemini.ts`

다음 인터페이스를 노출한다. 구현은 너 재량.

```ts
export interface GeminiSummaryRaw {
  // Gemini 응답에서 추출한 raw markdown 문자열.
  // 형식: YAML frontmatter (--- 로 감싼 메타) + 본문 markdown.
  // 예:
  //   ---
  //   title: ...
  //   channel: ...
  //   url: ...
  //   duration: 1234
  //   one_liner: ...
  //   ---
  //
  //   ## 핵심 포인트
  //   ...
  markdown: string;
}

// 유튜브 URL 을 Gemini 에 직접 입력해 요약 마크다운을 받아온다.
// 모델은 환경 변수 GEMINI_MODEL 우선, 없으면 'gemini-2.5-flash'.
// API 키는 process.env.GEMINI_API_KEY. 미설정 시 명시적 에러를 던진다 (MissingApiKeyError).
export async function summarizeYoutubeUrl(url: string): Promise<GeminiSummaryRaw>;

export class MissingApiKeyError extends Error {}
export class GeminiCallError extends Error {}
```

**프롬프트 핵심 규칙** (구현 시 사용):

```
당신은 영상 콘텐츠를 한국어 마크다운 노트로 정리하는 도구입니다.
아래 영상 링크의 콘텐츠를 분석해 마크다운 한 편으로 정리하세요.

반드시 다음 형식을 지키세요:

1. 문서 맨 위는 YAML frontmatter 입니다. --- 두 줄로 감싸고 아래 키를 정확히 포함하세요:
   - title:      영상 제목 (string)
   - channel:    채널명 (string)
   - url:        원본 URL (string) — 입력으로 받은 값 그대로
   - duration:   영상 길이 (초 단위 정수). 모르면 0
   - one_liner:  영상의 한 줄 요약 (1문장, 80자 이내)

2. frontmatter 이후 본문은 영상의 성격(강의/토크/리뷰/브이로그/튜토리얼 등)에 맞춰
   적절한 섹션 구조를 직접 결정해 작성하세요. 섹션 구조는 영상마다 달라도 됩니다.

3. 본문은 한국어로 작성합니다. 단, 인명·기술 용어·고유명사는 원문 그대로 둡니다.

4. 마크다운 외 다른 텍스트 (서두 인사, 코드 펜스 ```markdown 같은 래퍼) 를 출력하지 마세요.

입력 URL: {url}
```

> 위 프롬프트 문구는 `src/lib/gemini.ts` 안에 상수 `SUMMARY_PROMPT` 로 분리해 export 한다 (테스트와 services 에서 참조 가능하도록).

### 3) 서비스 레이어: `src/services/summarizer.ts`

```ts
import type { NewSummary } from '@/types/summary';

export interface SummarizerOutput {
  // gemini 가 반환한 frontmatter (raw object) — DB 저장에 사용
  meta: {
    title: string;
    channel: string;
    url: string;
    duration: number;
    oneLiner: string;
  };
  // frontmatter 를 제외한 본문 마크다운만
  body: string;
}

// URL 검증 → gemini 호출 → frontmatter 파싱 → 결과 반환.
// frontmatter 의 필수 키가 빠지면 InvalidSummaryError.
export async function summarize(url: string): Promise<SummarizerOutput>;

export function isValidYoutubeUrl(url: string): boolean;

export class InvalidUrlError extends Error {}
export class InvalidSummaryError extends Error {}
```

**URL 검증 규칙** — 다음 패턴만 허용:
- `https?://(www\.)?youtube\.com/watch\?v=[A-Za-z0-9_-]{11}([&?].*)?`
- `https?://(www\.)?youtube\.com/shorts/[A-Za-z0-9_-]{11}([?].*)?`
- `https?://youtu\.be/[A-Za-z0-9_-]{11}([?].*)?`

다른 도메인 / 다른 경로는 모두 거부. 유효 시 그대로 반환, 무효 시 `InvalidUrlError` 던짐.

**frontmatter 파싱**:
- `gray-matter` 사용. raw markdown → `{ data, content }`.
- `data` 에 `title`, `channel`, `url`, `duration`, `one_liner` 가 모두 존재하고 타입이 맞아야 한다. 누락 / 타입 불일치 시 `InvalidSummaryError`.
- `one_liner` → `oneLiner` 로 키 변환.

### 4) 테스트

#### `src/services/summarizer.test.ts`

Gemini 호출은 `vi.mock('@/lib/gemini', ...)` 로 모킹. 실제 API 호출 금지.

- `isValidYoutubeUrl`: `watch?v=...`, `youtu.be/...`, `shorts/...` 각각 11자 id 매칭 통과 케이스 + 거부 케이스 (도메인 다름, id 길이 다름, 임의 경로).
- `summarize`: invalid URL 입력 시 `InvalidUrlError`.
- `summarize`: gemini 가 정상 markdown 반환 시 `{ meta, body }` 가 올바르게 분리됨.
- `summarize`: gemini 가 frontmatter 누락된 markdown 반환 시 `InvalidSummaryError`.
- `summarize`: gemini 가 `duration` 을 string 으로 반환 시 `InvalidSummaryError` (타입 불일치).

#### `src/lib/gemini.test.ts`

- `summarizeYoutubeUrl`: `GEMINI_API_KEY` 미설정 시 `MissingApiKeyError`.
- `SUMMARY_PROMPT` 가 export 되어 있다 (문자열, 비어있지 않음).

> Gemini SDK 자체 호출 경로는 모킹이 까다로워 단위 테스트에서 검증하지 않는다. SDK 호출은 services 테스트에서 `gemini` 모듈을 통째로 mock 함으로써 우회한다.

## Acceptance Criteria

```bash
npm test -- src/services/summarizer.test.ts src/lib/gemini.test.ts
npm run build
npm run lint
```

## 검증 절차

1. 위 AC 통과.
2. 아키텍처 체크리스트:
   - `lib/gemini.ts` 와 `services/summarizer.ts` 가 ARCHITECTURE.md 의 분리 원칙대로 나뉘었는가?
   - 외부 API 호출이 `src/lib/` 안에서만 일어나는가?
   - `GEMINI_API_KEY` 가 코드에 하드코딩되지 않았는가?
3. `/phases/0-mvp/index.json` step 2 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "src/lib/gemini.ts (Gemini SDK 래퍼) + src/services/summarizer.ts (URL 검증·frontmatter 파싱) 작성, 모킹 기반 테스트 통과."`
   - 3회 실패 → `"status": "error"`

> 참고: 이 step 의 테스트는 전부 모킹 기반이므로 `GEMINI_API_KEY` 가 없어도 통과해야 한다. 키 부재로 blocked 처리하지 마라. 실제 Gemini 통합 동작은 step 4 (api-routes) 이후 사용자가 직접 검증한다.

## 금지사항

- `@google/generative-ai` (구 SDK) 사용 금지. 이유: 새 `@google/genai` 가 공식 후속이며 ADR-002 의 "Gemini 활용 최대화" 와 일치한다.
- Gemini 실제 API 호출이 테스트에서 발생하면 안 된다. 이유: 비용 + 결정론. 반드시 mock.
- 영상 콘텐츠 추출을 위해 `yt-dlp`, `youtube-transcript-api`, Whisper 등 별도 파이프라인 도입 금지. 이유: ADR-002 — Gemini 단일 사용.
- LLM 출력의 "올바름" (요약 품질) 을 테스트하지 마라. 이유: 결정론 불가. CLAUDE.md TDD 적용 범위는 결정론적 부분만.
- 이 step 에서 DB insert / 파일 저장 / API route 작성 금지. 이유: scope 위반. 후속 step.
- 기존 테스트를 깨뜨리지 마라.
