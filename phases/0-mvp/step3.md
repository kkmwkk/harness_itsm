# Step 3: markdown-exporter

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — `output/` 폴더 위치, `lib/` 와 `services/` 분리
- `/docs/ARCHITECTURE.md` — `src/lib/markdown.ts` 와 `src/services/exporter.ts` 의 역할
- `/docs/ADR.md` — ADR-003 (로컬 파일시스템 + SQLite)
- `/src/types/summary.ts` — Step 1 의 `Summary`, `NewSummary` 타입
- `/src/lib/db.ts` — Step 1 산출
- `/src/services/summarizer.ts` — Step 2 산출, `SummarizerOutput` 타입

Step 0~2 에서 만들어진 코드를 꼼꼼히 읽고, 설계 의도를 이해한 뒤 작업하라.

## 작업

요약 결과(`SummarizerOutput`)를 받아 **DB insert + `output/{id}.md` 파일 저장**을 하나의 동작으로 묶는 exporter 를 만든다. 한쪽 실패 시 보상(rollback).

### 1) 의존성

이미 깔린 것 사용. `gray-matter` 는 Step 2 에서 추가됨.

### 2) `src/lib/markdown.ts`

```ts
import type { Summary } from '@/types/summary';

// frontmatter object + body string 을 합쳐 '---\n...\n---\n\nbody' 형태로 직렬화.
// gray-matter 의 stringify 사용.
export function buildMarkdownDocument(
  meta: Record<string, unknown>,
  body: string
): string;

// output/{id}.md 에 동기 저장. 디렉토리가 없으면 생성.
// 반환: 저장된 파일의 절대 경로.
export function writeSummaryFile(id: string, document: string, outputDir?: string): string;

// output/{id}.md 삭제. 파일이 없어도 에러나지 않음.
export function deleteSummaryFile(id: string, outputDir?: string): void;

// 기본 output 디렉토리: process.env.OUTPUT_DIR ?? path.join(process.cwd(), 'output')
```

### 3) `src/services/exporter.ts`

```ts
import type { Summary } from '@/types/summary';
import type { SummarizerOutput } from '@/services/summarizer';
import type Database from 'better-sqlite3';

// 1) markdown 직렬화
// 2) DB insert (id 생성)
// 3) 파일 저장 (output/{id}.md)
// 4) DB row 의 md_path 업데이트 (id 가 정해진 뒤에야 파일 경로가 확정되므로)
//
// 실패 처리 (보상):
// - 파일 저장 실패 → DB row 삭제 후 에러 re-throw
// - DB 업데이트 실패 → 파일 삭제 + DB row 삭제 후 에러 re-throw
//
// 반환: 최종 Summary (mdPath 포함).
export async function persistSummary(
  db: Database.Database,
  output: SummarizerOutput
): Promise<Summary>;
```

> `db.ts` 의 `insertSummary` 가 `mdPath` 를 필수로 받으므로 약간의 트릭이 필요하다. 두 가지 방식 중 하나를 선택하라:
> - (A) `insertSummary` 를 호출하기 전에 미리 id 를 만들어 path 를 결정 후 한 번에 insert. → 이 경우 `db.ts` 에 헬퍼 `generateSummaryId(): string` 을 추가하거나, `insertSummary` 가 받는 `NewSummary` 인터페이스에 `mdPath` 가 이미 있으므로 services 레이어에서 `crypto.randomUUID()` 로 id 를 먼저 만들고 path 를 합성한 뒤 insert. **이 방식 추천**.
> - (B) 임시 빈 mdPath 로 insert 후 update. → schema 가 `NOT NULL` 이므로 빈 문자열을 쓰면 의미가 모호해진다. 비추.
>
> 방식 A 를 택하면 `db.ts` 의 `insertSummary` 가 외부에서 받은 id 도 허용해야 한다. 그래야 services 가 id 를 먼저 만들 수 있다. 다음과 같이 시그니처를 확장하라:
>
> ```ts
> // 기존: insertSummary(db, input: NewSummary): Summary
> // 변경: insertSummary(db, input: NewSummary, opts?: { id?: string; createdAt?: string }): Summary
> ```
>
> 기존 step 1 테스트와의 호환성을 유지하라 (선택 인자만 추가).

### 4) 테스트: `src/services/exporter.test.ts`

- 정상 흐름: `persistSummary` 호출 후
  - 반환값의 `mdPath` 가 `output/{id}.md` 패턴이며 파일이 실제로 존재한다.
  - DB 에 row 가 1개 추가됐다.
  - 파일 내용에 frontmatter (`title`, `channel`, `url`, `duration`, `one_liner`) 가 포함된다.
- 보상 동작: 파일 저장 단계에서 일부러 실패시켰을 때 (예: outputDir 을 `/proc/forbidden` 같은 쓰기 불가 경로로 주입)
  - DB row 가 남아있지 않다.
  - 에러가 호출자에게 던져진다.

> 테스트에서 `outputDir` 을 임시 디렉토리(`os.tmpdir()`)로 주입하려면 `writeSummaryFile` / `deleteSummaryFile` 가 `outputDir` 인자를 받도록 만든 것이다. `persistSummary` 도 `opts?: { outputDir?: string }` 를 받게 만들어 테스트 격리 가능하게 하라.

### 5) `src/lib/markdown.test.ts`

- `buildMarkdownDocument({title:'t', channel:'c', url:'u', duration:60, one_liner:'l'}, '## h\n\nbody')` 결과가 `---\n...\n---\n\n## h\n\nbody` 패턴을 만족한다.
- `writeSummaryFile` → 같은 id 두 번 호출 시 덮어쓴다.
- `deleteSummaryFile` → 없는 파일에 대해 에러 없음.

## Acceptance Criteria

```bash
npm test -- src/lib/markdown.test.ts src/services/exporter.test.ts
npm run build
npm run lint
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크리스트:
   - `output/` 절대 경로가 코드에 하드코딩되지 않고 환경 변수 또는 인자로 주입 가능한가?
   - 파일 저장과 DB insert 가 보상 패턴으로 묶였는가?
   - `lib/` 와 `services/` 의 책임이 ARCHITECTURE.md 와 일치하는가?
3. `/phases/0-mvp/index.json` step 3 업데이트:
   - 성공 → `"status": "completed"`, `"summary": "src/lib/markdown.ts + src/services/exporter.ts 작성. DB+파일 저장 보상 패턴, 임시 디렉토리 기반 테스트 통과."`
   - 3회 실패 → `"status": "error"`

## 금지사항

- 외부 트랜잭션 라이브러리 도입 금지. 이유: try/finally + 명시적 보상으로 충분.
- 파일 시스템 비동기 API (`fs/promises`) 와 동기 API 를 한 함수 안에서 섞지 마라. 이유: 가독성과 에러 흐름 일관성. `writeSummaryFile` 는 동기로 통일.
- `output/` 디렉토리 외 다른 위치에 .md 를 쓰지 마라. 이유: PRD 와 ARCHITECTURE 의 약속.
- 이 step 에서 API route / UI / 노션 코드 작성 금지. 이유: scope 위반.
- 기존 테스트를 깨뜨리지 마라.
