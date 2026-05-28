# Polestar10 ITG v2 (POLESTAR-ITSM)

JSON 메타 한 건만 추가하면 폼·그리드·목록·상세 화면이 전부 자동 생성되는 **No-code 플랫폼**.

## 기술 스택

- Frontend: Vue 3.5+ · Vite 6 · TypeScript 5 · shadcn/vue · Tailwind CSS v4
- Backend: Spring Boot 3 · **Java 21** · JPA + QueryDSL · Spring Security + JWT
- DB: PostgreSQL 16 (Docker Desktop)
- 빌드: Gradle 8 · JUnit 5 + Mockito · vue-tsc

## 로컬 실행 (M1 — 백엔드 스켈레톤 단계)

```bash
cd backend
./gradlew build            # 컴파일 + 테스트
./gradlew bootJar          # 실행 가능한 jar 생성
```

> Docker 인프라 · 프론트엔드 · 메타 도메인은 후속 step 에서 추가된다.

## 더 보기

- 절대 규칙 / 개발 프로세스 → [`CLAUDE.md`](./CLAUDE.md)
- 제품 요구사항 → [`docs/PRD.md`](./docs/PRD.md)
- 아키텍처 → [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md)
- 의사결정 기록 → [`docs/ADR.md`](./docs/ADR.md)
- UI 토큰·컴포넌트 규칙 → [`docs/UI_GUIDE.md`](./docs/UI_GUIDE.md)
