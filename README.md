# TubeNote

유튜브 링크 하나를 던지면 영상 콘텐츠를 분석·요약해 깔끔한 마크다운(.md)으로 정리해주는 1인용 웹 대시보드. 선택적으로 노션 워크스페이스에 자동 동기화도 지원.

## 핵심 기능

1. **유튜브 링크 → 영상 분석** — Gemini 2.5 Flash 가 유튜브 URL 을 직접 입력으로 받아 처리. 별도 자막 추출/STT 파이프라인 없음.
2. **자동 마크다운 요약** — 영상 성격(강의/토크/리뷰/브이로그 등)에 맞춰 모델이 섹션 구조를 직접 결정. 필수 메타(제목·채널·길이·한 줄 요약·URL)는 YAML frontmatter 로 강제.
3. **로컬 보관** — 마크다운은 `output/{id}.md`, 메타데이터는 `data/tubenote.db` (SQLite).
4. **노션 자동 정리 (선택)** — "노션으로 보내기" 버튼 한 번에 본인 노션 DB 페이지로 동기화. 같은 영상 두 번 보내도 페이지 1개 유지 (멱등).

## 기술 스택

- **Next.js 15** (App Router) — 풀스택. 별도 백엔드 없음
- **TypeScript** strict mode
- **Tailwind CSS v4** — `docs/UI_GUIDE.md` 의 디자인 토큰을 CSS-first config 로 매핑
- **SQLite** (`better-sqlite3`) — 1인 단일 머신 가정
- **@google/genai** — Gemini 공식 SDK
- **@notionhq/client** + **@tryfabric/martian** — 노션 API + 마크다운→블록 변환

자세한 결정 배경은 `docs/ADR.md` 참고.

## 사전 요구사항

- **Node.js ≥ 20** (v18 은 동작 안 함 — Next.js 15 요구사항)
- **npm**
- **Gemini API key** (필수, 무료) — https://aistudio.google.com/apikey
- **Notion Internal Integration secret** + **Database ID** (선택)

## 설치

```bash
git clone git@github.com:kkmwkk/tubeNote.git
cd tubeNote
npm install
```

## 환경 변수

프로젝트 루트에 `.env` 파일을 만들고 다음 키들을 채운다.

```env
# 필수
GEMINI_API_KEY=AIza...

# 선택 (미설정 시 노션 동기화 기능만 비활성)
NOTION_TOKEN=ntn_...
NOTION_DATABASE_ID=                  # 노션 DB 페이지 URL 의 32자 hex 부분
```

### Notion 셋업 (노션 동기화를 쓸 때만)

1. https://www.notion.so/profile/integrations → `New integration` (Internal) → secret 복사
2. 노션 워크스페이스에 요약을 쌓을 페이지를 만들고 본문에 Full-page database 추가
3. DB Properties 5 개를 다음 이름·타입 정확히 그대로 만든다:

   | Property | Type |
   |---|---|
   | `Title` | Title (기본) |
   | `URL` | URL |
   | `OneLiner` | Text |
   | `Channel` | Text |
   | `TubenoteId` | Text (중복 방지 키) |

4. DB 페이지 우상단 `···` → `Connections` → 1번에서 만든 Integration connect
5. DB URL 의 `?` 앞 32자 hex 부분이 `NOTION_DATABASE_ID`

## 실행

```bash
npm run dev          # 개발 서버 (localhost:3000)
npm run build        # 프로덕션 빌드
npm test             # Vitest
npm run lint         # ESLint
```

브라우저로 `http://localhost:3000` 열기.

## 사용 방법

1. 메인 페이지의 입력란에 유튜브 URL 붙여넣기 (`youtube.com/watch?v=...`, `youtu.be/...`, `youtube.com/shorts/...` 모두 지원)
2. **`요약하기`** 클릭 → Gemini 가 영상 분석 중 (보통 30초~1분, 영상 길이에 따라 변동)
3. 끝나면 상세 페이지로 자동 라우팅. 본문이 마크다운으로 렌더되어 보임
4. (선택) 헤더의 **`노션으로 보내기`** 버튼 → 본인 노션 DB 에 페이지 생성. 두 번 눌러도 안전 (멱등)

## 디렉토리 구조

```
src/
├── app/
│   ├── (dashboard)/        # 메인 / 상세 페이지 (Server Components)
│   └── api/                # POST /summarize, GET /summaries[/:id], POST /notion
├── components/             # SubmitForm, SummaryCard, MarkdownRenderer, SyncToNotionButton
├── lib/                    # db, gemini, notion, markdown, http (외부 API 클라이언트 + 유틸)
├── services/               # summarizer, exporter, notion (비즈니스 오케스트레이션)
└── types/                  # Summary 타입

output/                     # 생성된 .md (gitignored)
data/                       # tubenote.db (gitignored)
docs/                       # PRD / ARCHITECTURE / ADR / UI_GUIDE
phases/                     # Harness 프레임워크의 phase 정의 (개발 자동화 흔적)
```

## 문서

- `docs/PRD.md` — 제품 정의·MVP 스코프
- `docs/ARCHITECTURE.md` — 데이터 흐름·디렉토리 구조
- `docs/ADR.md` — 기술 선택 이유와 트레이드오프
- `docs/UI_GUIDE.md` — Apple 디자인 시스템 기반 UI 토큰

## 트러블슈팅

- **`Unsupported MIME type: text/html`** → Gemini SDK 호출 시 `mimeType: 'video/*'` 가 빠진 경우. `src/lib/gemini.ts` 확인.
- **`Invalid argument`** → 유튜브 URL 에 `&t=5s` 같은 추적/타임스탬프 파라미터가 포함된 경우. `src/services/summarizer.ts` 의 `normalizeYoutubeUrl` 이 정규화.
- **노션 동기화가 401/404** → DB 페이지에 Integration 이 connect 안 된 경우. 위 "Notion 셋업" 4번 다시 확인.
- **Node 버전 에러** → `nvm use 20` 후 다시 시도.
