# 아키텍처

## 디렉토리 구조
```
src/
├── app/
│   ├── api/
│   │   ├── summarize/        # POST: 유튜브 URL → 요약 생성
│   │   ├── summaries/        # GET: 과거 요약 목록 / [id] 상세
│   │   └── notion/           # POST: 요약 → 노션 페이지 동기화
│   ├── (dashboard)/
│   │   ├── page.tsx          # URL 입력 + 최근 요약 리스트
│   │   └── [id]/page.tsx     # 개별 요약 상세/원문 MD
│   └── layout.tsx
├── components/               # UI 컴포넌트 (입력 폼, 카드, 진행 표시 등)
├── lib/
│   ├── db.ts                 # SQLite 핸들 (better-sqlite3)
│   ├── gemini.ts             # Gemini API 클라이언트 (유튜브 URL 직접 입력)
│   ├── notion.ts             # Notion API 클라이언트
│   └── markdown.ts           # MD 생성/저장 유틸
├── services/
│   ├── summarizer.ts         # URL → Gemini → 구조화된 요약 오케스트레이션
│   └── exporter.ts           # MD 파일 + 노션 페이지 출력
└── types/                    # TypeScript 타입 정의

output/                       # 생성된 .md 파일 (gitignored)
data/                         # SQLite DB 파일 (gitignored)
```

## 패턴
- **Server Components 기본** — 목록·상세 페이지는 서버에서 SQLite 직접 쿼리해 렌더. 입력 폼·진행 표시·낙관적 업데이트가 필요한 부분만 Client Component.
- **외부 API는 라우트 핸들러에서만** — Gemini·Notion 키는 서버 사이드 환경 변수. 클라이언트는 절대 외부 API를 직접 호출하지 않는다.
- **서비스 레이어 분리** — 외부 API 클라이언트(`lib/`)와 비즈니스 오케스트레이션(`services/`)을 분리해 LLM/저장소를 갈아끼울 여지 확보.

## 데이터 흐름
```
[사용자]
  │  유튜브 URL 입력
  ▼
[Client Component: 입력 폼]
  │  POST /api/summarize { url }
  ▼
[API Route: /api/summarize]
  │  services/summarizer.ts 호출
  ▼
[Gemini API]  ── 유튜브 URL을 직접 입력으로 받아 영상 분석 + 구조화된 요약 반환
  │
  ▼
[SQLite]     ── 메타 저장 (id, url, title, created_at, md_path)
[output/*.md] ── 마크다운 파일 저장
  │
  ▼ (옵션, 사용자 트리거)
[Notion API] ── 페이지 생성, 본문에 MD 변환 블록 삽입
  │
  ▼
[Client]     ── 결과 화면으로 라우팅 + 다운로드 링크
```

## 상태 관리
- **서버 상태**: Server Components가 SQLite를 직접 쿼리하여 렌더링. 데이터 페칭 라이브러리(SWR/React Query) 없이 Next.js의 캐싱과 `revalidatePath`로 처리.
- **클라이언트 상태**: 입력 값, 로딩, 에러 메시지 정도만 `useState`. 전역 상태 관리 라이브러리는 도입하지 않는다.
- **장기 실행 작업**: Gemini 요약은 응답이 길어질 수 있으므로 API Route를 스트리밍(또는 단순 동기 응답 + 진행 인디케이터)으로 처리. v1 은 동기 응답으로 단순화.
