# M1 E2E 검증 보고서

## 환경
- 검증 일시: 2026-05-28 23:23 (KST)
- Docker 이미지: `postgres:16` (`itg-postgres`), `dpage/pgadmin4:latest`
- Spring Boot: 3.5.0, Java: 21.0.11 (LTS), Gradle: 8.14.5
- profile: `local` (`SPRING_PROFILES_ACTIVE=local`)
- DB 접속: `localhost:5432` / `itgdb` / `itg`
- OpenAPI: SpringDoc 2.8.6, OAS 3.1.0

## 시나리오 결과
| # | 단계 | HTTP | 결과 | 비고 |
|---|------|------|------|------|
| 1 | `docker-compose up -d` + `bootRun` + `GET /actuator/health` | 200 | PASS | `{"status":"UP"}` |
| 2 | DRAFT 시드 INSERT (`itg-sample-e2e-v1-1`) | — | PASS | `INSERT 0 1` |
| 3 | `GET /api/meta/active/itg-sample-e2e` (배포 없음) | 404 | PASS | `errorCode=META_NOT_PUBLISHED`, `success=false` |
| 4 | `PATCH /api/meta/itg-sample-e2e-v1-1/publish` | 200 | PASS | `metaStatus=PUBLISHED`, `message="배포되었습니다."` |
| 5 | `GET /api/meta/active/itg-sample-e2e` → v1-1 | 200 | PASS | `id=itg-sample-e2e-v1-1`, `metaStatus=PUBLISHED` |
| 6 | `POST /api/meta/itg-sample-e2e-v1-1/copy` → v1-2 DRAFT | 201 | PASS | `id=itg-sample-e2e-v1-2`, `metaStatus=DRAFT`, `minorVersion=2` |
| 7 | `GET /api/meta/group/itg-sample-e2e/versions` | 200 | PASS | 2건 반환, v1-2(DRAFT) → v1-1(PUBLISHED) 순 (minor DESC) |
| 8 | `PATCH /api/meta/itg-sample-e2e-v1-2/publish` + v1-1 자동 DEPRECATED 검증 | 200 | PASS | v1-2 `PUBLISHED`, v1-1 `DEPRECATED` (Service.deprecatePublished + DB 트리거 이중 안전망) |
| 9 | `GET /api/meta/active/itg-sample-e2e` → v1-2 | 200 | PASS | `id=itg-sample-e2e-v1-2` |
| 10 | DRAFT `v1-3` 직접 INSERT 후 active 무영향 확인 | 200 | PASS | active 여전히 v1-2 (DRAFT 노출 차단) |
| 11 | `GET /api/meta/system/ITSM/active` + `GET /api/meta/package/PACKAGE` | 200 | PASS | system/ITSM/active 에 v1-2 만 노출. package/PACKAGE 에는 v1-1(DEPRECATED)·v1-2(PUBLISHED)·v1-3(DRAFT) 3건 |
| 12 | `PATCH /api/meta/itg-sample-e2e-v1-3/archive` | 200 | PASS | `metaStatus=ARCHIVED`, `message="보관 처리되었습니다."` |
| 13 | OpenAPI 산출물 추출 (`/v3/api-docs`, `/v3/api-docs.yaml`) | 200 | PASS | `backend/openapi/itg-api-spec.json` (10,996B), `itg-api-spec.yaml` (13,878B). 9개 경로 모두 등록 |
| 14 | 백엔드 종료 + 시드 `DELETE` | — | PASS | `DELETE 3`, remaining=0 |

## 핵심 검증 사실

### PRD §5-2 버전 라우팅 4규칙 — 모두 충족
1. **PUBLISHED 최신 1건만 노출** — 5단계(v1-1)·9단계(v1-2) 에서 `page_meta_active` 뷰가 동일 groupId 에서 (major, minor) 가 가장 높은 PUBLISHED 단 1건만 반환 확인.
2. **PUBLISHED 없으면 미노출(404)** — 3단계에서 DRAFT 만 존재할 때 `META_NOT_PUBLISHED` 로 404 반환 확인.
3. **새 PUBLISHED 전환 시 기존 자동 DEPRECATED** — 8단계에서 v1-2 PUBLISHED 전환 직후 v1-1 의 `metaStatus` 가 즉시 `DEPRECATED` 로 변경됨을 단일 트랜잭션 내에서 확인.
4. **복사본은 항상 DRAFT 시작** — 6단계에서 PUBLISHED v1-1 을 복사한 결과가 `metaStatus=DRAFT`, `minorVersion=2` 로 생성됨 확인.

### ADR-006 자동 DEPRECATE 이중 안전망
- **Service 레이어**: `MetaService.publish()` 가 `metaRepository.deprecatePublished(groupId, metaId)` 를 명시적으로 호출 후 `Entity.publish()` 로 자신을 PUBLISHED 로 전환.
- **DB 트리거**: `sql/init/01_schema.sql` 의 `trg_auto_deprecate` BEFORE UPDATE 트리거가 동일 group_id 의 다른 PUBLISHED 를 DEPRECATED 로 강제.
- 8단계의 v1-2 publish 한 번에 v1-1 이 DEPRECATED 로 즉시 전이됨이 확인되어 **Service 호출 경로**와 **DB 강제** 두 안전망 모두 살아 있는 상태에서 일관된 결과를 보장.

### ADR-011 Swagger 민감정보 차단
- `/v3/api-docs` 의 모든 `@Schema(example)` 값이 가상 샘플(`itg-ticket-v1-2`, `itg-ticket`, `ITSM 티켓 관리`, `dev-team@example.com`) 로만 구성됨 확인.
- 실제 IP·이메일·사번·시리얼·계약번호 출현 없음.

### 핵심 설계 사상 — DRAFT 노출 절대 금지
- 10단계에서 DRAFT `v1-3` 를 DB 에 직접 INSERT 한 후 `GET /api/meta/active/itg-sample-e2e` 가 여전히 PUBLISHED v1-2 만 반환. `page_meta_active` 뷰의 `WHERE meta_status = 'PUBLISHED'` 필터가 클라이언트 노출 경로의 마지막 방어선으로 동작함을 검증.

## 등록된 OpenAPI 경로 (9개)
- `POST   /api/meta/{metaId}/copy`
- `PATCH  /api/meta/{metaId}/publish`
- `PATCH  /api/meta/{metaId}/archive`
- `GET    /api/meta/{metaId}`
- `GET    /api/meta/system/{systemType}/package/{packageType}`
- `GET    /api/meta/system/{systemType}/active`
- `GET    /api/meta/package/{packageType}`
- `GET    /api/meta/group/{groupId}/versions`
- `GET    /api/meta/active/{groupId}`

## 산출물
- `backend/openapi/itg-api-spec.json` — OpenAPI 3.1.0 JSON baseline (M1 시점)
- `backend/openapi/itg-api-spec.yaml` — OpenAPI 3.1.0 YAML baseline (M1 시점)
- `backend/E2E_REPORT.md` — 본 보고서

## 결함
없음. PRD §5-2 4규칙, ADR-006 이중 안전망, ADR-011 민감정보 차단, 핵심 설계 사상의 DRAFT 노출 금지 모두 시나리오로 검증됨.
