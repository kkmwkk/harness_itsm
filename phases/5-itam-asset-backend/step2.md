# Step 2: asset-controller-and-swagger

## 읽어야 할 파일

- `/CLAUDE.md` — Controller 비즈니스 금지·Swagger 어노테이션 필수·민감정보 금지
- `/docs/ADR.md` — ADR-009 ApiResponse·ADR-011 민감정보
- `/phases/5-itam-asset-backend/step0~1.md` — 산출물
- `/backend/src/main/java/com/nkia/itg/itsm/ticket/controller/TicketController.java` — 패턴 참고
- `/backend/src/main/java/com/nkia/itg/common/response/PageResponse.java` — 평탄화 유틸
- `/backend/src/main/java/com/nkia/itg/common/security/SecurityConfig.java` — permitAll allowlist (이번 phase 에 `/api/assets/**` 추가 필요)

## 작업

이 step 의 목적은 **AssetController + Swagger + 이력 복원 endpoint 를 만들고, SecurityConfig 의 permitAll 에 `/api/assets/**` 를 추가하며, `@WebMvcTest` 로 검증하는 것**이다.

### 1. `SecurityConfig` 패치

phase 3 의 hotfix 와 동일한 흐름. `/api/assets/**` 도 permitAll 추가.

```java
.requestMatchers("/api/meta/**").permitAll()
.requestMatchers("/api/tickets/**").permitAll()
.requestMatchers("/api/assets/**").permitAll()      // ← 이 phase 에서 추가
.anyRequest().authenticated()
```

> 인증 강화는 별도 phase 의 책임 (TODO 주석 유지).

### 2. `AssetController` — `com.nkia.itg.itam.asset.controller`

```java
@Tag(name = "ITAM Asset — 자산원장 관리")
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController { /* ... */ }
```

#### 엔드포인트 일람

| 메서드 | 경로 | Service | 응답 |
|---|---|---|---|
| POST | `/api/assets` | `create(req)` | 201 + `ApiResponse<AssetResponse>` |
| GET | `/api/assets` | `search(...)` | 200 + `ApiResponse<PageResponse<AssetSummary>>` |
| GET | `/api/assets/{id}` | `getById(id)` | 200 + `ApiResponse<AssetResponse>` |
| GET | `/api/assets/by-no/{assetNo}` | `getByAssetNo(assetNo)` | 200 + `ApiResponse<AssetResponse>` |
| PATCH | `/api/assets/{id}` | `update(id, req)` | 200 |
| PATCH | `/api/assets/{id}/status` | `changeStatus(id, req)` | 200 + 메시지 "상태가 변경되었습니다." |
| PATCH | `/api/assets/{id}/assign` | `assign(id, req)` | 200 |
| **GET** | **`/api/assets/{id}/registration-meta`** | **`getRegistrationMeta(id)`** | **200 + `ApiResponse<PageMetaResponse>`** (이력 복원) |

> `/registration-meta` 가 본 phase 의 차별점. 자산 등록 시점의 메타를 반환 (DEPRECATED·ARCHIVED 라도). PRD §5-2 활용 사례.

#### Swagger

각 메서드에 `@Operation` + 경로변수 `@Parameter(example)` + `@ApiResponses`. 가상 샘플:
- `id`: `42`
- `assetNo`: `"AST-00042"`
- `assigneeId`: `"assignee-sample-1"`
- `pageGroupId`: `"itg-asset"`

`registration-meta` 의 `description`:
> "자산 등록 시점에 사용된 PageMeta 를 반환한다. 메타가 현재 DEPRECATED·ARCHIVED 라도 그대로 반환 — 자산 이력 화면 복원에 사용 (PRD §5-2)."

### 3. `@WebMvcTest` — `AssetControllerTest`

`@WebMvcTest(AssetController.class)` + `@MockBean AssetService`. 케이스:

1. `POST_create_201`.
2. `POST_create_name_누락_400`.
3. `POST_create_pageGroupId_누락_400`.
4. `GET_search_200_PageResponse`.
5. `GET_by_id_없으면_404`.
6. `PATCH_status_허용되지_않은_전이_400_INVALID_REQUEST`.
7. `PATCH_status_RETIRED_400`.
8. `PATCH_assign_RETIRED_400`.
9. `GET_registration_meta_DEPRECATED_메타도_정상_반환`.
10. `GET_by_no_200`.
11. `PATCH_update_200_부분_업데이트`.
12. `POST_create_META_NOT_PUBLISHED_400_또는_404` (메타 없을 때 도메인 정합성 거부).

> SecurityConfig 의 `/api/assets/**` 가 permitAll 로 추가되었는지도 회귀 확인 (테스트 슬라이스에 SecurityConfig import).

### 4. Swagger 노출 확인

부팅 후 `/v3/api-docs` 에 8 개 asset 경로 모두 포함:
- `/api/assets` (POST·GET)
- `/api/assets/{id}` (GET·PATCH)
- `/api/assets/by-no/{assetNo}` (GET)
- `/api/assets/{id}/status`, `/api/assets/{id}/assign` (PATCH)
- `/api/assets/{id}/registration-meta` (GET)

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend

# 1) 파일
test -f src/main/java/com/nkia/itg/itam/asset/controller/AssetController.java
grep -q "/api/assets/\\*\\*" src/main/java/com/nkia/itg/common/security/SecurityConfig.java

# 2) 단위 테스트
./gradlew test --tests "com.nkia.itg.itam.asset.controller.*"
./gradlew test
./gradlew build

# 3) bootRun + Swagger 경로 확인
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q UP
curl -fsS http://localhost:8080/v3/api-docs > /tmp/spec.json
python3 -c "import json; d=json.load(open('/tmp/spec.json')); \
            need={'/api/assets','/api/assets/{id}','/api/assets/{id}/registration-meta'}; \
            missing=need-set(d['paths'].keys()); assert not missing, missing"
kill %1
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크리스트:
   - `SecurityConfig` 에 `/api/assets/**` permitAll 추가?
   - Controller 가 비즈니스 로직 없이 Service 호출만?
   - `/registration-meta` 가 별도 경로로 분리되었고 DEPRECATED 메타도 반환?
   - 모든 `@Schema(example)` 가상 샘플?
   - `Page<T>` 대신 `PageResponse<T>` 평탄화?
3. step 2 업데이트:
   - 성공 → `"summary": "AssetController 8 엔드포인트 (POST create / GET search·byId·byAssetNo / PATCH update·status·assign / GET registration-meta 이력 복원) + SecurityConfig 에 /api/assets/** permitAll 추가 + Swagger 어노테이션 + @WebMvcTest 12 케이스. /v3/api-docs 에 asset 경로 등록 확인."`

## 금지사항

- DELETE 엔드포인트 추가 금지 (자산은 archive·retire 로 처리).
- `Page<T>` 직접 노출 금지 (`PageResponse<T>` 평탄화).
- `@Schema(example)` 에 실 운영 데이터 금지.
- Controller try/catch 금지 (GlobalExceptionHandler 위임).
- 사용자 검색·자동완성 엔드포인트 금지.
- 인증 강화 (JWT) 도입 금지 — 다음 phase.
- 프런트엔드 수정 금지.
- ticket 모듈·meta 모듈 수정 금지.
- 자산-별 권한 체크 로직 금지 (별도 phase 의 ADR).
