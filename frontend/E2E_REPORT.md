# E2E 검증 보고서 — Step 4: api-client-and-meta-fetch

> Phase 1 (frontend-foundation) / Step 4. Frontend(VueUse `useFetch`) ↔ Backend(`/api/meta/active/{groupId}`) 동시 부팅 연동 검증.
> 모든 예시 메타는 가상 샘플(`itg-published`, `itg-no-publish`, `itg-sample-front`)이며 실 운영 데이터를 포함하지 않는다 (ADR-011).

## 1. 환경

| 항목 | 값 |
|------|------|
| Node.js | v24.16.0 (공용 운영 버전) |
| pnpm | 11.4.0 |
| Vite | 6.4.2 |
| VueUse | @vueuse/core 14.3.0 (`createFetch`) |
| Frontend Dev | http://localhost:5173 |
| Backend | Spring Boot 3.5 / Gradle toolchain JDK 21 (bootRun), http://localhost:8080 |
| DB | PostgreSQL 16.14 (Docker `itg-postgres`, localhost:5432, itgdb) |
| 브라우저 | SPA 라우트 HTTP 응답 + curl 기반 백엔드 직접 호출로 검증 |

> 참고: 호스트 `java -version` 은 17 이지만, 백엔드는 `settings.gradle` 의 foojay-resolver + `build.gradle` 의 toolchain(`languageVersion 21`)으로 JDK 21 을 자동 프로비저닝하여 부팅했다.

## 2. 시나리오 결과

| # | 시나리오 | 기대 | 결과 |
|---|----------|------|------|
| 1 | docker-compose up (postgres) | 컨테이너 Running | PASS |
| 2 | 백엔드 부팅 → `/actuator/health` | `{"status":"UP"}` | PASS |
| 3 | 시드: DRAFT 2건(`itg-sample-front`, `itg-no-publish`) + PUBLISHED 1건(`itg-published`) | INSERT 3 | PASS |
| 4 | `GET /api/meta/active/itg-published` | 200 + `id=itg-published-v1-1` + `metaStatus=PUBLISHED` | PASS |
| 5 | `GET /api/meta/active/itg-no-publish` (DRAFT only) | 404 + `errorCode=META_NOT_PUBLISHED` | PASS |
| 6 | `GET /api/meta/active/itg-sample-front` (DRAFT only) | 404 | PASS |
| 7 | CORS preflight (`OPTIONS`, `Origin: http://localhost:5173`) | `Access-Control-Allow-Origin: http://localhost:5173` | PASS |
| 8 | `GET /system/meta?groupId=itg-published` (SPA) | 200 | PASS |
| 9 | `GET /system/meta?groupId=itg-no-publish` (SPA) | 200 | PASS |
| 10 | `GET /system/meta` (query 없음, SPA) | 200 | PASS |
| 11 | 정적 검증 `pnpm type-check` / `pnpm lint` / `pnpm build` | 0 error | PASS |
| 12 | 시드 메타 정리 (DELETE) | 잔여 0건 | PASS |

### 백엔드 응답 원문 (검증 근거)

```
GET /api/meta/active/itg-published
{"success":true,"data":{"id":"itg-published-v1-1","title":"Published 샘플",
 "systemType":"ITSM","packageType":"PACKAGE","groupId":"itg-published",
 "majorVersion":1,"minorVersion":1,"metaStatus":"PUBLISHED",
 "metaJson":{"api":"/api/published-samples"},"active":true, ... },
 "message":null,"errorCode":null}

GET /api/meta/active/itg-no-publish   → HTTP 404
{"success":false,"data":null,"message":"배포된 메타가 없습니다: itg-no-publish",
 "errorCode":"META_NOT_PUBLISHED"}
```

응답 형태가 `types/meta.ts` 의 `ApiEnvelope<PageMeta>` (`success`/`data`/`message`/`errorCode`) 및 `PageMeta` 필드와 1:1 일치함을 확인.

## 3. 핵심 검증 사실

- **DRAFT 노출 차단 (클라이언트 사이드 안전망):** 백엔드 버전 라우팅이 DRAFT 를 404 로 막고, `usePageMeta` 는 `statusCode===404 && data.errorCode==='META_NOT_PUBLISHED'` 일 때 `notPublished=true` 를 반환 → `/system/meta?groupId=itg-no-publish` 화면에 "배포된 버전이 없습니다 (META_NOT_PUBLISHED)" 경고만 노출하고 메타 카드는 렌더링하지 않는다.
- **`ApiEnvelope` 정규화:** `useApiFetch` 의 `onFetchError` 가 응답 body 의 `errorCode`/`message` 를 `ctx.error` 로 정규화한다. 200 이면 `data.data` 에서 `PageMeta` 를 추출.
- **`beforeFetch`(JWT 헤더 골격):** `readToken()` 이 `null` 을 반환하면 `Authorization` 헤더를 붙이지 않는다(현재 phase). 토큰 연결은 다음 phase.
- **baseURL:** `import.meta.env.VITE_API_BASE_URL` (기본 `http://localhost:8080`) 로 `createFetch` 구성.
- **CORS:** 백엔드 `SecurityConfig` 가 `http://localhost:5173` 만 허용 → 프론트가 200 응답 수신 가능.
- **타입 안전성:** `metaJson` 은 `Record<string, unknown>` (no `any`). `pnpm type-check`·`pnpm lint`(`--max-warnings=0`) 0건.

## 4. 한계 (다음 phase 범위)

- **JWT 자동 주입:** `readToken()` 은 현재 `null` 고정(골격). 실제 토큰 조회/`useAuthStore` 연결은 phase-2-auth.
- **메타 본문 구조화 타입:** `PageMetaBody = Record<string, unknown>`. `GridMeta`/`FormMeta` 등 세부 타입은 다음 phase 의 `DynamicPage`/`DynamicForm`/`DynamicGrid` 책임.
- **DynamicPage 미구현:** 본 step 은 `/system/meta` 단일 페이지의 fetch + 표시(카드 + JSON pre)만 담당. 동적 렌더링 컴포넌트는 다음 phase.
- **다크 모드 토글 / 메타 변경(POST·PATCH) UI 없음:** 본 step 은 조회 전용.

## 5. 산출물

- `frontend/.env.example` (`VITE_API_BASE_URL`)
- `frontend/src/env.d.ts` (`ImportMetaEnv` 타입 선언)
- `frontend/src/types/meta.ts` (`SystemType`/`PackageType`/`MetaStatus`/`PageMeta`/`PageMetaVersion`/`ApiEnvelope<T>`)
- `frontend/src/lib/api.ts` (`useApiFetch` — `createFetch`)
- `frontend/src/composables/usePageMeta.ts` (`usePageMeta(groupId)`)
- `frontend/src/pages/system/MetaPage.vue` (조회 폼 + 메타 카드 + JSON 미리보기)
- `frontend/E2E_REPORT.md` (본 파일)
