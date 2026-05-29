# Step 1: meta-validator

## 읽어야 할 파일

- `/CLAUDE.md` — 핵심 설계 사상·절대 규칙
- `/docs/ARCHITECTURE.md` — §3 메타 모델, §7 API 설계
- `/docs/ADR.md` — ADR-005·ADR-006·ADR-009
- `/phases/7-ai-meta-generation/step0.md` — generate_meta.py
- `/backend/src/main/java/com/nkia/itg/meta/service/MetaService.java`·`MetaController.java`·`entity/PageMeta.java`·`domain/*` — 기존 메타 모듈

## 작업

이 step 의 목적은 **PageMeta JSON 의 형식·필수 필드·field type 호환을 사전 검증하는 `MetaValidationService` 와 `POST /api/meta/dry-run` 엔드포인트를 만들어, 생성된 메타가 실 INSERT 전에 결함을 잡도록 하는 것**이다. 단위·@WebMvcTest 케이스로 검증.

### 1. `MetaValidationService` — `com.nkia.itg.meta.service.MetaValidationService`

```java
@Service
@RequiredArgsConstructor
public class MetaValidationService {

    public ValidationResult validate(PageMetaCreateRequest req) { ... }

    public record ValidationResult(
        boolean valid,
        List<ValidationIssue> issues
    ) {}

    public record ValidationIssue(
        Severity severity,                 // ERROR | WARNING
        String   path,                     // 예: "form.fields[2].type"
        String   code,                     // 예: "INVALID_FIELD_TYPE"
        String   message
    ) {
        public enum Severity { ERROR, WARNING }
    }
}
```

검증 규칙:

**ERROR** (어느 하나라도 있으면 invalid):
- `id` non-empty + `{groupId}-v{major}-{minor}` 패턴.
- `title` non-empty.
- `systemType` ∈ {ITSM, ITAM, PMS, COMMON, SYSTEM}.
- `packageType` ∈ {PACKAGE, CUSTOM}.
- `groupId` non-empty (영숫자·하이픈만).
- `majorVersion >= 1`, `minorVersion >= 1`.
- `metaStatus` ∈ {DRAFT, PUBLISHED, DEPRECATED, ARCHIVED}. **dry-run 의 default 권장은 DRAFT**.
- `metaJson` 내부:
  - `api` non-empty + `/api/` 로 시작.
  - `grid.columns` 배열, 각 컬럼의 `field`·`label`·`type`.
  - `form.layout` ∈ {single-column, two-column}.
  - `form.fields` 배열, 각 필드의 `name`·`label`·`type` + type 이 12 종 FieldType 안에.
  - `actions` (옵션) 의 각 action 의 `id`·`label`·`type` ∈ {dialog-form, export, navigate, custom}.

**WARNING**:
- `grid.columns` 의 `field` 가 `form.fields` 와 한 개도 매칭 안 됨 (잠재적 mismatch).
- `actions` 가 비어있음 (등록 버튼 없음).
- `form.fields` 가 비어있음 (조회 전용 메타).
- `metaStatus` 가 PUBLISHED 인데 신규 생성 (DRAFT 권장).

### 2. DTO — `PageMetaCreateRequest`

요청 본문은 `PageMeta` 의 풀 형태와 비슷:

```java
public record PageMetaCreateRequest(
    String       id,
    String       title,
    SystemType   systemType,
    PackageType  packageType,
    String       groupId,
    Integer      majorVersion,
    Integer      minorVersion,
    MetaStatus   metaStatus,
    Map<String, Object> metaJson
) {}
```

### 3. `POST /api/meta/dry-run` 엔드포인트 — `MetaController` 확장

```java
@Operation(
    summary = "메타 검증(dry-run)",
    description = "PageMeta 후보 JSON 의 형식·필수 필드·field type 호환을 사전 검증. " +
                  "DB INSERT 는 일어나지 않음. ERROR 가 1건이라도 있으면 invalid."
)
@PostMapping("/dry-run")
public ResponseEntity<ApiResponse<MetaValidationService.ValidationResult>> dryRun(
    @RequestBody PageMetaCreateRequest req
) {
    return ResponseEntity.ok(ApiResponse.ok(metaValidationService.validate(req)));
}
```

> 본 step 은 dry-run 만. 실 INSERT 엔드포인트(`POST /api/meta`) 는 별도 phase 의 admin API.

### 4. 단위 테스트

`MetaValidationServiceTest` — 각 검증 규칙을 케이스로:

1. `valid_정상_메타_ERROR_0`.
2. `id_패턴_불일치_ERROR_INVALID_ID_FORMAT`.
3. `systemType_null_ERROR`.
4. `groupId_특수문자_ERROR`.
5. `minorVersion_0_이하_ERROR`.
6. `metaStatus_없거나_invalid_ERROR`.
7. `metaJson.api_누락_ERROR`.
8. `metaJson.form.fields[2].type=email_ERROR_INVALID_FIELD_TYPE`.
9. `actions.id_누락_ERROR`.
10. `metaStatus=PUBLISHED_신규_WARNING_DRAFT_권장`.
11. `grid.columns_와_form.fields_매칭_0_WARNING`.
12. `valid_ERROR_가_있으면_valid_false`.

`MetaControllerDryRunTest` — `@WebMvcTest`:
1. `POST_dry_run_정상_200_valid_true`.
2. `POST_dry_run_invalid_200_valid_false_issues_표시`.

### 5. CLI 통합 (선택, 짧게)

`scripts/generate_meta.py` 의 출력을 dry-run 엔드포인트에 자동 POST 하는 wrapper 스크립트 `scripts/validate_meta.sh`:

```bash
#!/usr/bin/env bash
# 사용: scripts/validate_meta.sh <meta-json-file>
META_FILE="${1:?meta JSON 파일 경로}"
curl -fsS -X POST http://localhost:8080/api/meta/dry-run \
  -H 'Content-Type: application/json' \
  --data @"$META_FILE" | python3 -m json.tool
```

> bootRun 이 떠 있어야 함. 본 step 의 AC 에 통합 검증 포함.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend

# 1) 파일
test -f src/main/java/com/nkia/itg/meta/service/MetaValidationService.java
test -f src/main/java/com/nkia/itg/meta/dto/PageMetaCreateRequest.java

# 2) 단위 테스트
./gradlew test --tests "com.nkia.itg.meta.service.MetaValidationServiceTest"
./gradlew test --tests "com.nkia.itg.meta.controller.MetaControllerDryRunTest"
./gradlew test       # 전체 회귀
./gradlew build

# 3) 부팅 + dry-run cURL
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q UP

# 4) 정상 메타 dry-run
python3 scripts/generate_meta.py \
  --openapi backend/openapi/itg-api-spec.json \
  --request-dto TicketCreateRequest --response-dto TicketSummary \
  --group-id itg-ticket-dryrun --title "Dry-Run 검증용" \
  --system-type ITSM --package-type PACKAGE \
  --major 1 --minor 1 --api /api/tickets > /tmp/_meta.json
curl -fsS -X POST http://localhost:8080/api/meta/dry-run \
  -H 'Content-Type: application/json' --data @/tmp/_meta.json \
  | python3 -c "import json,sys; r=json.load(sys.stdin)['data']; assert r['valid']==True, r"

# 5) 불량 메타 dry-run — systemType 누락
echo '{"id":"x-v1-1","title":"x","groupId":"x","majorVersion":1,"minorVersion":1,"metaStatus":"DRAFT","metaJson":{"api":"/api/x","grid":{"columns":[]},"form":{"layout":"two-column","fields":[]}}}' \
  | curl -fsS -X POST http://localhost:8080/api/meta/dry-run \
    -H 'Content-Type: application/json' --data @- \
    | python3 -c "import json,sys; r=json.load(sys.stdin)['data']; \
                   assert r['valid']==False; \
                   assert any(i['code']=='INVALID_SYSTEM_TYPE' or 'SYSTEM_TYPE' in i['code'] for i in r['issues'])"

# 6) Swagger 등록 확인
curl -fsS http://localhost:8080/v3/api-docs \
  | python3 -c "import json,sys; d=json.load(sys.stdin); assert '/api/meta/dry-run' in d['paths']"
kill %1
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - dry-run 은 INSERT 안 함 (검증만)?
   - 응답이 `ApiResponse<ValidationResult>` 래퍼?
   - ERROR 1건이라도 있으면 `valid: false`?
   - issue 의 `path` 가 JSON 포인터 스타일(예: `form.fields[2].type`)?
   - Swagger 어노테이션 완비?
3. step 1 업데이트:
   - 성공 → `"summary": "MetaValidationService (ValidationResult/ValidationIssue/Severity) + 검증 규칙 — id 패턴/Enum 4종 강제 + groupId 영숫자-하이픈 + minor>=1 + metaJson(api 패턴, grid.columns, form.layout/fields type 12종, actions type) + WARNING(grid/form mismatch · actions empty · PUBLISHED 신규). POST /api/meta/dry-run (Swagger·ApiResponse 래퍼) + PageMetaCreateRequest DTO + scripts/validate_meta.sh wrapper. 단위 12 + Controller 2 케이스."`

## 금지사항

- dry-run 엔드포인트가 DB 에 INSERT 하지 마라. 이름 그대로 검증만.
- ERROR 와 WARNING 의 의미를 섞지 마라. ERROR=invalid blocker, WARNING=정보성.
- 새 메타 생성 endpoint (`POST /api/meta`) 를 이 step 에서 추가하지 마라. 본 step 은 검증만.
- field type 의 12종 외 값을 허용하지 마라.
- 외부 JSON Schema 라이브러리 (everit·networknt) 도입 금지. 내부 코드로 검증.
- 프런트엔드 코드 수정 금지.
- scripts/generate_meta.py 수정 금지 (이미 step 0).
- Controller try/catch 금지 (GlobalExceptionHandler).
- 운영 코드 console.log·System.out.println 금지.
