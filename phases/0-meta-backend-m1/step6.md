# Step 6: meta-controller-and-swagger

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "절대 규칙" (Controller 비즈니스 로직 금지, Swagger 어노테이션 필수, `@Schema(example)` 민감정보 금지, `ApiResponse<T>` 래퍼)
- `/docs/ARCHITECTURE.md` — §7 API 설계 (엔드포인트 일람표)
- `/docs/ADR.md` — ADR-009 (`ApiResponse<T>`), ADR-011 (민감정보 금지)
- `/phases/0-meta-backend-m1/step3.md` — `ApiResponse<T>`·`GlobalExceptionHandler`·`SwaggerConfig`·`SecurityConfig` 위치
- `/phases/0-meta-backend-m1/step5.md` — `MetaService` 메서드 시그니처·DTO 정의

이전 step 의 산출물(특히 Service 메서드 시그니처)을 정확히 알고 있어야 Controller 호출이 1:1 매핑된다.

## 작업

이 step 의 목적은 **HTTP 레이어(`MetaController`) 를 만들고 Swagger 어노테이션·예제 값을 완비하며, `@WebMvcTest` 로 컨트롤러를 단위 검증하는 것**이다. 비즈니스 로직은 Service 호출만 하고, 분기·예외 처리는 `GlobalExceptionHandler` 에 위임한다.

### 1. `MetaController` — `com.nkia.itg.meta.controller.MetaController`

```java
package com.nkia.itg.meta.controller;

@Tag(name = "Meta - 페이지 메타 관리")
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final MetaService metaService;
    /* 메서드들 */
}
```

#### 1-1. 엔드포인트 일람 (Service 1:1 매핑)

| 메서드 | 경로 | Service 호출 | 응답 본문 |
|---|---|---|---|
| GET | `/api/meta/active/{groupId}` | `getActive(groupId)` | `ApiResponse<PageMetaResponse>` |
| GET | `/api/meta/group/{groupId}/versions` | `getVersions(groupId)` | `ApiResponse<List<PageMetaVersionResponse>>` |
| GET | `/api/meta/{metaId}` | `getById(metaId)` | `ApiResponse<PageMetaResponse>` |
| PATCH | `/api/meta/{metaId}/publish` | `publish(metaId)` | `ApiResponse<PageMetaResponse>` (메시지 "배포되었습니다.") |
| PATCH | `/api/meta/{metaId}/archive` | `archive(metaId)` | `ApiResponse<PageMetaResponse>` (메시지 "보관 처리되었습니다.") |
| POST | `/api/meta/{metaId}/copy` | `copy(metaId)` | `ApiResponse<PageMetaResponse>` (status 201, 메시지 "새 버전이 생성되었습니다.") |
| GET | `/api/meta/system/{systemType}/active` | `getActiveBySystem(systemType)` | `ApiResponse<List<PageMetaResponse>>` |
| GET | `/api/meta/package/{packageType}` | `getByPackage(packageType)` | `ApiResponse<List<PageMetaResponse>>` |
| GET | `/api/meta/system/{systemType}/package/{packageType}` | `getBySystemAndPackage(...)` | `ApiResponse<List<PageMetaResponse>>` |

모든 응답은 `ResponseEntity.ok(ApiResponse.ok(...))` 또는 `ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(..., msg))` 패턴.

#### 1-2. Swagger 어노테이션 요건 (전수 적용)

각 메서드에:

- `@Operation(summary = "...", description = "...")` — 한 줄 요약 + 비즈니스 의미.
- 경로 변수에 `@Parameter(description = "...", example = "<가상-샘플>")`.
- 가능한 응답 코드는 `@ApiResponses({@ApiResponse(responseCode = "200", ...), @ApiResponse(responseCode = "404", ...)})` 로 명시.
- 모든 `example` 값은 가상 샘플 사용:
  - groupId 예시: `"itg-ticket"`
  - metaId 예시: `"itg-ticket-v1-2"`
  - systemType 예시: `"ITSM"`
  - packageType 예시: `"PACKAGE"`

예시 한 건:

```java
@Operation(
    summary = "화면 노출용 메타 조회 (PUBLISHED 최신 버전)",
    description = "groupId 기준 PUBLISHED 상태 중 (major, minor) 가 가장 높은 단 1건만 반환. " +
                  "DynamicPage 가 화면 렌더링에 사용하는 핵심 API. DRAFT 메타는 절대 노출되지 않는다."
)
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "배포된 버전이 없음")
})
@GetMapping("/active/{groupId}")
public ResponseEntity<ApiResponse<PageMetaResponse>> getActive(
    @Parameter(description = "페이지 그룹 ID", example = "itg-ticket")
    @PathVariable String groupId
) {
    return ResponseEntity.ok(ApiResponse.ok(metaService.getActive(groupId)));
}
```

> `ApiResponse` 의 import 충돌(`io.swagger.v3.oas.annotations.responses.ApiResponse` vs `com.nkia.itg.common.response.ApiResponse`) 주의 — Swagger 쪽은 풀패스 또는 별칭(`import ... as`) 사용 불가하므로 풀패스 사용 또는 메서드 단위로 `@io.swagger.v3.oas.annotations.responses.ApiResponse(...)` 직접 표기.

#### 1-3. DTO 스키마 어노테이션 (`PageMetaResponse`)

각 필드에 `@Schema(description = "...", example = "...")` 부여 (DTO 가 Record 이므로 컴포넌트 위치 — 필드 선언 위에 어노테이션 가능). 예시 값은 가상 샘플만. 예:

- `id` → `example = "itg-ticket-v1-2"`
- `title` → `example = "ITSM 티켓 관리"`
- `groupId` → `example = "itg-ticket"`
- `majorVersion` → `example = "1"`
- `minorVersion` → `example = "2"`
- `metaJson` → `example = "{ \"api\": \"/api/tickets\", ... }"` (간단한 가짜 구조)

### 2. Controller 테스트 — `com.nkia.itg.meta.controller.MetaControllerTest`

`@WebMvcTest(MetaController.class)` + `@MockBean MetaService`. 케이스:

1. `GET_active_그룹의_PUBLISHED_있으면_200_과_PageMetaResponse_반환` — service mock 으로 응답 stub, mockMvc 로 status 200 + jsonPath `$.success == true` + `$.data.metaStatus == "PUBLISHED"`.
2. `GET_active_그룹에_PUBLISHED_없으면_404_와_META_NOT_PUBLISHED` — service 가 `ITGException("META_NOT_PUBLISHED", ..., HttpStatus.NOT_FOUND)` 던질 때 status 404 + `$.errorCode == "META_NOT_PUBLISHED"`.
3. `PATCH_publish_정상_200_과_메시지_배포되었습니다`.
4. `PATCH_publish_DRAFT_아닌_메타_400_INVALID_STATUS`.
5. `POST_copy_정상_201_과_새_DRAFT_응답`.
6. `GET_group_versions_정렬된_리스트_반환`.
7. `GET_system_active_ITSM_PUBLISHED_만_반환`.
8. `GET_package_PACKAGE_목록_반환`.
9. `PATCH_archive_정상_200_과_메시지_보관_처리되었습니다`.
10. `GET_meta_by_id_없으면_404_META_NOT_FOUND`.

테스트는 `MockMvc` 사용. Security 가 영향을 미치면 `@AutoConfigureMockMvc(addFilters = false)` 또는 `@WithMockUser` 등으로 패스. **`/api/meta/**` 가 `permitAll` 이므로 기본 설정으로 통과해야 한다 — 통과하지 못하면 step 3 의 `SecurityConfig` 설정을 재검토.**

> `@WebMvcTest` 의 슬라이스에 `SecurityConfig`·`GlobalExceptionHandler` 가 포함되는지 확인. 누락 시 `@Import(SecurityConfig.class)`·`@Import(GlobalExceptionHandler.class)` 명시.

### 3. Swagger 노출 확인용 통합 검증 (테스트가 아닌 부팅 후 curl)

부팅 + curl 로:

- `GET /swagger-ui/index.html` → 200.
- `GET /v3/api-docs` → JSON, `paths` 에 `/api/meta/active/{groupId}` 외 8개 경로 모두 존재.
- `GET /v3/api-docs.yaml` → YAML, 같은 내용.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend
./gradlew test --tests "com.nkia.itg.meta.controller.*"
./gradlew test                                     # 전체 테스트 회귀 확인
./gradlew build

# 부팅 + Swagger 검증
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 8
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'
curl -fsS -I http://localhost:8080/swagger-ui/index.html | head -1 | grep -q "200"
curl -fsS http://localhost:8080/v3/api-docs > /tmp/itg-api-spec.json
python3 -c "import json; d=json.load(open('/tmp/itg-api-spec.json')); \
            paths=set(d['paths'].keys()); \
            need={'/api/meta/active/{groupId}','/api/meta/group/{groupId}/versions','/api/meta/{metaId}', \
                  '/api/meta/{metaId}/publish','/api/meta/{metaId}/archive','/api/meta/{metaId}/copy', \
                  '/api/meta/system/{systemType}/active','/api/meta/package/{packageType}', \
                  '/api/meta/system/{systemType}/package/{packageType}'}; \
            missing=need-paths; print('OK' if not missing else 'MISSING: '+str(missing)); \
            assert not missing"
kill %1
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. 컨트롤러 테스트 10 케이스 + 전체 회귀 + Swagger 9 경로 존재 검증 모두 통과해야 한다.
2. 아키텍처 체크리스트:
   - `MetaController` 가 비즈니스 로직 없이 Service 호출만 하는가? (분기·예외 처리는 Service/Handler 위임)
   - 9 개 엔드포인트가 모두 구현되었고 ARCHITECTURE.md §7 표와 일치하는가?
   - 모든 메서드에 `@Operation` 어노테이션과 경로 변수 `@Parameter(example)` 가 있는가?
   - 모든 `example` 값이 가상 샘플인가? 실 IP/이메일/서버명/사번 노출 없음. (ADR-011)
   - Swagger import 충돌(`ApiResponse` 이름 중복)이 풀패스로 회피되었는가?
   - 응답은 일관되게 `ResponseEntity<ApiResponse<...>>` 인가? (ADR-009)
   - `POST /copy` 가 status 201 인가?
3. 결과에 따라 `phases/0-meta-backend-m1/index.json` 의 step 6 을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "MetaController 9 엔드포인트 (active/versions/by-id/publish/archive/copy/system-active/package/system-package) + @Operation·@Parameter·@ApiResponses 어노테이션 완비 + 가상 샘플 예시 + @WebMvcTest 10 케이스. /v3/api-docs 에 9 경로 모두 등록 확인."`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "<구체적 사유>"` 후 즉시 중단

## 금지사항

- Controller 에서 `if/else` 분기·DB 조회·트랜잭션 코드를 작성하지 마라. 이유: 레이어 책임 분리. 비즈니스 로직은 Service.
- Controller 에서 try/catch 로 예외를 잡아 응답을 만들지 마라. 이유: `GlobalExceptionHandler` 에 위임. 예외를 그대로 throw 한다.
- `@Schema(example = ...)` 에 실 운영 데이터 넣지 마라. 이유: ADR-011. 가상 샘플(`"itg-ticket-v1-2"`, `"샘플 페이지"`, `example.com`) 만 사용.
- `@Operation(summary = "...")` 또는 `@Parameter` 를 한 메서드라도 누락하지 마라. 이유: CLAUDE.md 절대 규칙.
- 응답 본문을 `ApiResponse` 로 감싸지 않고 `PageMetaResponse` 등을 직접 반환하지 마라. 이유: ADR-009.
- 새 엔드포인트를 추가하지 마라 (예: POST `/api/meta` 직접 생성). 이유: 본 phase 의 범위는 **이미 존재하는 메타의 조회·전이·복사**만. 메타 생성 엔드포인트는 다음 phase 의 admin API 로 별도 설계. 시드는 SQL 또는 step 7 시나리오에서 직접 INSERT.
- `PathVariable` 의 Enum 변환(`SystemType`/`PackageType`) 시 대소문자 자동 변환 등 커스텀 컨버터를 추가하지 마라. 이유: 기본 동작(대문자 정확 일치)으로 충분. 변환 규칙은 ADR 로 명시 후 도입.
- 인증 강제를 위해 `SecurityConfig` 를 수정하지 마라. 이유: 본 phase 는 `/api/meta/**` permitAll. 인증은 다음 phase 의 별도 step.
- 통합 테스트(Testcontainers) 를 컨트롤러 레이어에서 다시 작성하지 마라. 이유: Repository(step 4)·E2E(step 7) 에서 이미 다룸. 컨트롤러는 `@WebMvcTest` 로 충분.
