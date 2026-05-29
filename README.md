# Polestar10 ITG v2 (POLESTAR-ITSM)

JSON 메타 한 건만 추가하면 폼·그리드·목록·상세 화면이 전부 자동 생성되는 **No-code 플랫폼**.

## 기술 스택

- Frontend: Vue 3.5+ · Vite 6 · TypeScript 5 · shadcn/vue · Tailwind CSS v4
- Backend: Spring Boot 3 · **Java 21** · JPA + QueryDSL · Spring Security + JWT
- DB: PostgreSQL 16 (Docker Desktop)
- 빌드: Gradle 8 · JUnit 5 + Mockito · vue-tsc

## 현재 상태

- **M1 (백엔드 메타 모델) 완료** — `page_meta` 스키마·MetaService(publish/copy/archive)·9개 REST 엔드포인트·Swagger·E2E 검증 통과 ([`backend/E2E_REPORT.md`](./backend/E2E_REPORT.md)).

## 로컬 실행

```bash
docker-compose up -d                                         # PostgreSQL + pgAdmin
cd backend
./gradlew build                                              # 컴파일 + 테스트
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun               # 8080 기동
```

> 프론트엔드 · DynamicPage 는 후속 phase 에서 추가된다.

> 신규 모듈 메타 생성은 [`docs/META_GENERATION_GUIDE.md`](./docs/META_GENERATION_GUIDE.md) 참고.

## 더 보기

- 절대 규칙 / 개발 프로세스 → [`CLAUDE.md`](./CLAUDE.md)
- 제품 요구사항 → [`docs/PRD.md`](./docs/PRD.md)
- 아키텍처 → [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md)
- 의사결정 기록 → [`docs/ADR.md`](./docs/ADR.md)
- UI 토큰·컴포넌트 규칙 → [`docs/UI_GUIDE.md`](./docs/UI_GUIDE.md)
