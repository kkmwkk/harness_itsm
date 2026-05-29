# QA 가이드 — Playwright baseline 갱신 절차

> Playwright 시각 회귀(ADR-019)의 baseline 스크린샷을 **의도된 UI 변경** 시 어떻게 갱신하는지
> 정리한 운영자 가이드. 의도하지 않은 시각 변경은 CI 에서 자동 차단되므로, baseline 갱신은
> 반드시 사람이 검토 후 수동으로만 수행한다.

## 1. baseline 갱신 절차

```
1) 로컬에서 변경된 화면을 직접 확인한다 (의도된 변경인지 검토).
2) backend + frontend 를 실제 기동한다.
     # 터미널 A — DB
     docker-compose up -d
     # 터미널 B — backend
     cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
     # 터미널 C — frontend
     cd frontend && pnpm dev      # 또는 pnpm build && pnpm preview
3) frontend 에서 baseline 을 갱신한다.
     cd frontend && pnpm e2e:update
4) git status 에 e2e/*-snapshots/ 의 새 PNG 가 보인다 — 변경 내용을 눈으로 검토한다.
5) PR 본문에 baseline 갱신 사유를 명시한다 (어떤 화면이 왜 바뀌었는지).
6) 리뷰어가 diff 를 검토한 뒤 머지한다.
```

## 2. 관련 npm 스크립트 (`frontend/package.json`)

| 스크립트 | 용도 |
|----------|------|
| `pnpm e2e` | 시각 회귀 실행 (baseline 과 diff) |
| `pnpm e2e:headed` | 브라우저 표시하며 실행 (디버깅) |
| `pnpm e2e:update` | baseline 스크린샷 갱신 (의도된 변경 시에만) |
| `pnpm e2e:report` | 마지막 실행 HTML 리포트 열기 |

## 3. 주의

- CI(`.github/workflows/playwright.yml`)에서는 `--update-snapshots` 를 절대 실행하지 않는다.
  baseline 갱신은 로컬에서 사람이 의도적으로만 수행한다.
- diff 임계치는 `playwright.config.ts` 의 `maxDiffPixelRatio: 0.02` (2%). 임계치 변경도 PR 검토 대상.
