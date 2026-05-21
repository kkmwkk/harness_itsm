# Step 6: notion-sync

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — `NOTION_TOKEN`, `NOTION_DATABASE_ID` 환경 변수
- `/docs/ARCHITECTURE.md` — `src/app/api/notion/`, `src/lib/notion.ts`, `src/services/notion.ts` 분리
- `/docs/PRD.md` — 노션 동기화는 **선택** 기능. 토큰 없으면 동작하지 않아도 됨.
- `/src/types/summary.ts`, `/src/lib/db.ts` — Step 1
- `/src/app/api/summarize/route.ts` — Step 4 의 라우트 패턴
- `/src/app/(dashboard)/[id]/page.tsx` — Step 5 의 상세 페이지 (여기에 "노션으로" 버튼 추가)

Step 0~5 에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

요약을 노션 페이지로 동기화하는 기능을 추가한다. 환경 변수 (`NOTION_TOKEN`, `NOTION_DATABASE_ID`) 가 설정되지 않은 환경에서는 **이 step 의 코드 자체는 통과해야 하지만, 통합 검증이 불가능하면 status 를 `blocked` 로 마크하고 즉시 중단**한다.

### 1) blocked 우선 점검

이 step 의 어떤 코드도 작성하기 전에:

1. `process.env.NOTION_TOKEN` 과 `process.env.NOTION_DATABASE_ID` 의 존재를 확인한다.
2. 둘 중 하나라도 없으면 **즉시 다음 동작 후 중단**:
   - 코드는 **작성하지 말고** (작성하면 검증 불가).
   - `/phases/0-mvp/index.json` 의 step 6 을 `"status": "blocked"` 로 마크.
   - `blocked_reason` 에 다음 메시지 기록:
     ```
     NOTION_TOKEN 또는 NOTION_DATABASE_ID 가 .env 에 없음. 노션 동기화 동작 검증 불가.
     해결 방법:
       1. https://www.notion.so/profile/integrations 에서 Internal Integration 생성, secret 복사
       2. 노션에서 요약을 정리할 database 페이지를 만들고 통합을 connect, database id 복사
          (URL 의 32자 hex 부분)
       3. 프로젝트 루트의 .env 에 다음 추가:
            NOTION_TOKEN=secret_xxx
            NOTION_DATABASE_ID=xxxxxxxxxxxxxxxx
       4. status 를 pending 으로 되돌리고 재실행:
            python3 scripts/execute.py 0-mvp
     ```
   - 그 외 어떤 파일도 생성/수정하지 마라.

3. 둘 다 있으면 아래 작업을 정상적으로 진행한다.

### 2) 의존성 추가

```
npm i @notionhq/client @tryfabric/martian
```

### 3) `src/lib/notion.ts`

```ts
import { Client } from '@notionhq/client';

// 싱글톤. NOTION_TOKEN 미설정 시 MissingNotionTokenError.
export function getNotionClient(): Client;

export class MissingNotionTokenError extends Error {}
```

### 4) `src/services/notion.ts`

```ts
import type { Summary } from '@/types/summary';

export interface NotionSyncResult {
  pageId: string;       // 노션 페이지 id
  pageUrl: string;      // 노션 페이지 URL
}

// 1) DB 에서 summary 조회. 없으면 NotFoundError.
// 2) mdPath 의 본문을 읽음. frontmatter 제거.
// 3) @tryfabric/martian 로 markdown → notion blocks 변환.
// 4) NOTION_DATABASE_ID 안에 새 페이지 생성.
//    properties:
//      Title:    summary.title (DB 의 'Title' property)
//      URL:      summary.url
//      OneLiner: summary.oneLiner (rich_text)
//      Channel:  summary.channel (rich_text)
//      TubenoteId: summary.id (rich_text — 중복 방지/idempotent key)
// 5) 같은 TubenoteId 가 이미 있으면 새로 만들지 않고 기존 페이지 반환 (멱등).
//
// 반환: NotionSyncResult.
export async function syncSummaryToNotion(summaryId: string): Promise<NotionSyncResult>;

export class NotFoundError extends Error {}
export class NotionSyncError extends Error {}
```

> Notion DB 스키마 요구사항을 README 또는 `docs/NOTION_SETUP.md` 같은 별도 문서로 남기지 마라. 이 step.md 의 위 properties 가 곧 사용자 가이드다.

### 5) API 라우트: `src/app/api/notion/route.ts`

`POST /api/notion`

요청:
```ts
type RequestBody = { summaryId: string };
```

응답 (200): `NotionSyncResult`.

에러:
- 400 — `summaryId` 누락
- 404 — `NotFoundError`
- 500 — `NotionSyncError` 또는 그 외 → `{ error: 'NOTION_FAILED', message }`
- 503 — `MissingNotionTokenError` → `{ error: 'NOTION_NOT_CONFIGURED' }` (운영 중 토큰이 사라진 경우 대비)

### 6) UI 통합

`src/app/(dashboard)/[id]/page.tsx` 의 메타 영역(`product-tile-dark`) 우측에 "노션으로 보내기" 버튼을 추가하라.

- 버튼은 Client Component (`src/components/SyncToNotionButton.tsx`, `'use client'`).
- UI_GUIDE `button-primary` (Action Blue pill) 또는 `button-secondary-pill` (ghost) 중 하나 — 페이지가 다크 타일 위라 `button-secondary-pill` 권장.
- 클릭 → `POST /api/notion` 호출.
- 응답 200 → 버튼이 "노션에서 열기" 로 바뀌고 `pageUrl` 로 외부 링크.
- 503 → "노션 미연결" 인라인 메시지.
- 그 외 에러 → 인라인 에러 메시지.

### 7) 테스트

#### `src/services/notion.test.ts`

- `@/lib/notion` 의 `getNotionClient` 를 모킹해 `pages.create`, `databases.query` 등의 호출을 spy.
- DB 와 파일시스템은 in-memory + tmp dir 로 격리.
- 케이스:
  - 정상: 새 summary → `pages.create` 가 정확한 properties 로 호출되고 `pageId, pageUrl` 반환.
  - 멱등: 같은 `summaryId` 두 번 → 두 번째는 `pages.create` 가 호출되지 않고 기존 pageId 반환.
  - 없는 summary → `NotFoundError`.
- 실제 Notion API 호출 금지.

#### `src/app/api/notion/route.test.ts`

- body 누락 → 400.
- service 가 `NotFoundError` → 404.
- service 가 `NotionSyncError` → 500.
- service 가 `MissingNotionTokenError` → 503.
- 정상 → 200 + `NotionSyncResult`.

#### `src/components/SyncToNotionButton.test.tsx`

- 클릭 시 `fetch('/api/notion', ...)` 호출. (mock fetch)
- 200 응답 → 버튼 라벨이 변경되고 링크 속성이 `pageUrl` 로 설정.

## Acceptance Criteria

**전제: NOTION_TOKEN, NOTION_DATABASE_ID 가 .env 에 설정되어 있음.**

```bash
npm test
npm run build
npm run lint
```

**추가 수동 검증** (가능하다면):
- 실제 노션 DB 에 새 페이지가 생성되는지 한 번 호출해보기. (선택)

## 검증 절차

1. **먼저 환경 변수 확인** — 미설정 시 위 "blocked 우선 점검" 절차대로 즉시 중단.
2. AC 통과.
3. 아키텍처 체크리스트:
   - `lib/notion.ts` (SDK 래퍼) 와 `services/notion.ts` (오케스트레이션) 가 분리됐는가?
   - API 라우트가 비즈니스 로직을 인라인하지 않고 service 를 호출만 하는가?
   - 같은 summary 를 두 번 동기화해도 노션 페이지가 1개만 생성되는가? (멱등)
   - UI_GUIDE 의 button 토큰을 사용했는가?
4. `/phases/0-mvp/index.json` step 6 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "src/lib/notion.ts + src/services/notion.ts + POST /api/notion + SyncToNotionButton 작성. 멱등 동기화 테스트 통과."`
   - 3회 실패 → `"status": "error"`
   - 토큰 없음 → `"status": "blocked"` (위 1번)

## 금지사항

- markdown → notion blocks 변환을 직접 구현하지 마라. 이유: `@tryfabric/martian` 가 기능이 광범위하고 안정적. 직접 구현은 ADR-002 의 "MVP 속도 최우선" 위반.
- 환경 변수 미설정 시 코드를 작성하지 마라. 이유: 검증 불가능한 코드를 생성하면 다음 실행에서 통과 여부를 알 수 없다. blocked 메커니즘이 정확히 이런 경우를 위한 것이다.
- `await fetch('https://api.notion.com/...')` 직접 호출 금지. 반드시 `@notionhq/client` 경유. 이유: 인증 헤더 / 버전 헤더 관리를 SDK 에 맡긴다.
- 노션 페이지 본문에 동일한 frontmatter 를 텍스트로 박지 마라. 메타는 DB property 로, 본문은 마크다운 변환 결과만. 이유: 노션 DB 필터링 가능성 확보.
- 기존 테스트를 깨뜨리지 마라.
