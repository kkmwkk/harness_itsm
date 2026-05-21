# Step 1: db-schema

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — `src/lib/` 와 `src/types/` 분리 규칙
- `/docs/ARCHITECTURE.md` — `src/lib/db.ts` 위치와 역할
- `/docs/ADR.md` — ADR-003 (SQLite + 로컬 파일, ORM 없음)
- `/src/lib/` 의 현재 상태 (Step 0 산출물 — 빈 디렉토리)
- `/src/types/` 의 현재 상태 (Step 0 산출물 — 빈 디렉토리)

Step 0 에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

SQLite 핸들과 `summaries` 테이블 스키마를 정의한다. TDD: 테스트를 먼저 작성하고 통과하는 구현을 작성한다.

### 1) 의존성 추가

```
npm i better-sqlite3
npm i -D @types/better-sqlite3
```

ORM(Prisma, Drizzle 등) 금지 — ADR-003 의 "외부 의존성 최소화" 철학.

### 2) 타입 정의: `src/types/summary.ts`

```ts
export interface Summary {
  id: string;            // UUID v4
  url: string;           // 원본 유튜브 URL
  title: string;         // 영상 제목
  channel: string;       // 채널명
  duration: number;      // 초 단위. 모르면 0
  oneLiner: string;      // 한 줄 요약 (Gemini 반환의 one_liner)
  mdPath: string;        // output/{id}.md 의 절대 또는 상대 경로
  createdAt: string;     // ISO 8601 (UTC)
}

export interface NewSummary extends Omit<Summary, 'id' | 'createdAt'> {}
```

### 3) DB 모듈: `src/lib/db.ts`

다음 인터페이스를 제공한다. 구현체는 너 재량.

```ts
import type { Summary, NewSummary } from '@/types/summary';

// 싱글톤 핸들 반환. 첫 호출 시 data/tubenote.db 를 열고 migrate 를 자동 실행한다.
// path 옵션은 테스트에서 ':memory:' 를 주입하기 위함.
export function getDb(path?: string): Database;

// CREATE TABLE IF NOT EXISTS summaries (...). 멱등.
export function migrate(db: Database): void;

// INSERT. id, createdAt 은 함수 내부에서 생성 (uuid v4, new Date().toISOString()).
// 반환: 생성된 Summary 전체.
export function insertSummary(db: Database, input: NewSummary): Summary;

// SELECT * WHERE id = ?. 없으면 null.
export function getSummary(db: Database, id: string): Summary | null;

// SELECT *. 최신순 (createdAt DESC). limit 기본 50.
export function listSummaries(db: Database, opts?: { limit?: number; offset?: number }): Summary[];

// DELETE WHERE id = ?. 영향 받은 행 수 반환 (0 또는 1).
export function deleteSummary(db: Database, id: string): number;
```

`Database` 타입은 `better-sqlite3` 의 `Database` 를 import 해 그대로 노출한다.

스키마:
```sql
CREATE TABLE IF NOT EXISTS summaries (
  id          TEXT PRIMARY KEY,
  url         TEXT NOT NULL,
  title       TEXT NOT NULL,
  channel     TEXT NOT NULL,
  duration    INTEGER NOT NULL DEFAULT 0,
  one_liner   TEXT NOT NULL,
  md_path     TEXT NOT NULL,
  created_at  TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_summaries_created_at ON summaries (created_at DESC);
```

> snake_case ↔ camelCase 매핑은 함수 내부에서 처리. 외부 타입은 camelCase 만 노출.

UUID 는 Node 내장 `crypto.randomUUID()` 사용. `uuid` 패키지 도입 금지.

### 4) 테스트: `src/lib/db.test.ts`

TDD — 아래 케이스를 먼저 작성하고 구현이 통과하게 한다. 모든 테스트는 `:memory:` DB 로 격리한다.

- `migrate` 가 두 번 호출되어도 에러나지 않는다 (멱등).
- `insertSummary` 가 입력 + 자동 생성된 `id`, `createdAt` 을 포함한 `Summary` 를 반환한다.
- `insertSummary` 직후 `getSummary(id)` 가 같은 객체를 반환한다.
- `getSummary` 가 없는 id 에 대해 `null` 을 반환한다.
- `listSummaries` 가 `createdAt DESC` 순으로 정렬해 반환한다 (작은 sleep 또는 직접 timestamp 주입 트릭).
- `listSummaries({ limit: 2 })` 가 정확히 2 개만 반환한다.
- `deleteSummary` 가 1 또는 0 을 반환하고 이후 `getSummary` 가 `null`.
- `id` 는 UUID v4 패턴을 만족한다 (`/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/`).

## Acceptance Criteria

```bash
npm test -- src/lib/db.test.ts
npm run build
npm run lint
```

## 검증 절차

1. 위 AC 커맨드 통과.
2. 아키텍처 체크리스트:
   - `src/lib/db.ts` 위치가 ARCHITECTURE.md 와 일치하는가?
   - `src/types/summary.ts` 가 camelCase 인가?
   - ORM 이 도입되지 않았는가? (ADR-003)
   - `data/tubenote.db` 경로가 기본값으로 사용되는가?
3. `/phases/0-mvp/index.json` 의 step 1 을 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "src/lib/db.ts (better-sqlite3) + src/types/summary.ts 작성, summaries 테이블 + CRUD + TDD 8 케이스 통과."`
   - 3회 실패 → `"status": "error"`
   - 외부 입력 필요 → `"status": "blocked"`

## 금지사항

- ORM (Prisma, Drizzle, Kysely 등) 도입 금지. 이유: ADR-003 — 1인 도구에서 ORM 의 복잡도는 가치 대비 과하다.
- `uuid` 패키지 도입 금지. 이유: Node 내장 `crypto.randomUUID()` 로 충분. 의존성 최소화.
- DB 마이그레이션 도구 (umzug, knex migrate) 도입 금지. 이유: `CREATE TABLE IF NOT EXISTS` 한 줄로 충분.
- `Summary` 의 필드를 snake_case 로 외부에 노출 금지. 이유: 도메인 타입은 camelCase 일관성.
- 이 step 에서 Gemini·Notion·UI 코드 작성 금지. 이유: scope 위반.
- 기존 테스트를 깨뜨리지 마라.
