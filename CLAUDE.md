# 프로젝트: TubeNote

유튜브 링크 하나를 받아 영상 콘텐츠를 분석·요약하여 마크다운(.md)으로 출력하고, 선택적으로 본인 노션 워크스페이스에 자동 정리하는 1인용 웹 대시보드. 자세한 배경은 `docs/PRD.md`, 구조는 `docs/ARCHITECTURE.md`, 결정 이유는 `docs/ADR.md`, UI 토큰은 `docs/UI_GUIDE.md` 참조.

## 기술 스택
- Next.js 15 (App Router) — 풀스택 (UI + API 라우트 한 레포)
- TypeScript strict mode
- Tailwind CSS — `docs/UI_GUIDE.md` 의 토큰(색상·radius·타이포)을 `tailwind.config` 로 매핑
- SQLite (`better-sqlite3`) — 메타데이터
- Google Gemini API — 유튜브 URL 직접 입력, 영상 분석 + 요약 통합
- Notion API — (선택) 요약 동기화

## 아키텍처 규칙
- CRITICAL: Gemini·Notion 등 외부 API 키와 호출은 서버 사이드(`app/api/*` 라우트 핸들러 또는 `services/*`)에서만 한다. 클라이언트 컴포넌트에서 외부 API를 직접 호출하지 않는다.
- CRITICAL: 외부 API 클라이언트는 `src/lib/` 에, 비즈니스 오케스트레이션은 `src/services/` 에 분리한다. 라우트 핸들러는 services를 호출만 하고 로직을 인라인하지 않는다 — Gemini 교체 가능성을 보존하기 위한 ADR-002 결정.
- 컴포넌트는 `src/components/`, 타입은 `src/types/` 에 분리한다.
- Server Components 기본. 입력 폼·진행 인디케이터처럼 인터랙션이 필요한 곳만 Client Component 로 표시(`'use client'`).
- 데이터 접근: Server Components 에서 SQLite 를 직접 쿼리한다. SWR/React Query 같은 데이터 페칭 라이브러리·전역 상태 관리 라이브러리를 도입하지 않는다.
- 생성된 `.md` 는 `output/`, SQLite DB 는 `data/` 에 둔다. 둘 다 `.gitignore`.

## 디자인 규칙 (요약, 자세한 건 `docs/UI_GUIDE.md`)
- 인터랙티브 요소는 단일 액센트 `colors.primary` (#0066cc) 만 사용. 두 번째 브랜드 컬러 도입 금지.
- 본문은 17px / weight 400 / line-height 1.47 / letter-spacing -0.374px. 16px 본문 금지, weight 500 금지.
- 그림자는 영상 썸네일에만(`rgba(0,0,0,0.22) 3px 5px 30px`). 카드·버튼·텍스트에 그림자 추가 금지.
- 그라데이션 배경, glass morphism(장식 목적의 blur), 보라/인디고 액센트, gradient orb 금지.

## 개발 프로세스
- CRITICAL: 새 기능 구현 시 테스트를 먼저 작성하고, 테스트가 통과하는 구현을 작성할 것 (TDD).
- 커밋 메시지는 conventional commits 형식 (feat:, fix:, docs:, refactor:, test:, chore:).
- 1인 사용 프로젝트지만 의사결정 흐름은 `docs/ADR.md` 에 추가로 기록 (ADR-004, ADR-005 …) — 미래의 본인이 맥락을 잃지 않도록.

## 명령어
```
npm run dev      # 개발 서버
npm run build    # 프로덕션 빌드
npm run lint     # ESLint
npm run test     # 테스트 (Vitest)
```

## 환경 변수
- `GEMINI_API_KEY` — Google Gemini API 키
- `NOTION_TOKEN` — Notion Integration 시크릿 (선택 기능)
- `NOTION_DATABASE_ID` — 요약을 정리할 노션 DB ID (선택 기능)
