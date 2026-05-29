# Step 1: asset-repository-and-service

## 읽어야 할 파일

- `/CLAUDE.md` — 절대 규칙 (트랜잭션 경계 Service)
- `/docs/PRD.md` — §5-2 자산 이력 메타 보존
- `/docs/ADR.md` — ADR-009·ADR-012·ADR-013
- `/phases/5-itam-asset-backend/step0.md` — Asset Entity·Enum·매트릭스
- `/backend/src/main/java/com/nkia/itg/itsm/ticket/{service,repository,dto}/**` — 패턴 참고
- `/backend/src/main/java/com/nkia/itg/meta/service/MetaService.java` — `getActive(groupId)` 시그니처

## 작업

이 step 의 목적은 **AssetRepository + AssetService + DTO 를 만들고, create() 시점에 `MetaService.getActive(groupId)` 로 PUBLISHED 메타 id 를 캡처해 `pageMetaIdAtRegistration` 에 저장하는 것**이다.

### 1. DTO Record — `com.nkia.itg.itam.asset.dto`

```java
public record AssetCreateRequest(
    @Schema(example = "샘플 자산")     @NotBlank @Size(max=200) String name,
    @Schema(example = "HARDWARE")     @NotNull AssetType assetType,
    @Schema(example = "SAMPLE-MODEL") String model,
    @Schema(example = "SN-SAMPLE-1")  String serialNo,
    @Schema(example = "노트북")        String category,
    @Schema(example = "assignee-sample-1") String assigneeId,
    @Schema(example = "본사 3층")      String location,
    @Schema(example = "2026-01-15")   LocalDate acquiredAt,
    // 자산이 어느 메타 그룹에 속하는지 (보통 'itg-asset')
    @Schema(example = "itg-asset")    @NotBlank String pageGroupId
) {}

public record AssetUpdateRequest(
    @Size(max=200) String name,
    String model, String serialNo, String category, String location
) {}

public record AssetStatusChangeRequest(
    @Schema(example = "STORAGE") @NotNull AssetStatus next
) {}

public record AssetAssignRequest(
    @Schema(example = "assignee-sample-2") String assigneeId
) {}

public record AssetResponse(
    Long id, String assetNo, String name,
    AssetType assetType, AssetStatus status,
    String model, String serialNo, String category,
    String assigneeId, String location,
    LocalDate acquiredAt, LocalDate disposedAt,
    String pageMetaIdAtRegistration,      // 핵심: 이력 복원 시 사용
    LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static AssetResponse from(Asset e) { /* ... */ }
}

public record AssetSummary(
    Long id, String assetNo, String name,
    AssetType assetType, AssetStatus status,
    String assigneeId, LocalDate acquiredAt
) {
    public static AssetSummary from(Asset e) { /* ... */ }
}
```

### 2. `AssetRepository` — `com.nkia.itg.itam.asset.repository`

```java
public interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findByAssetNo(String assetNo);

    @Query("""
        select a from Asset a
         where (:status     is null or a.status     = :status)
           and (:assetType  is null or a.assetType  = :assetType)
           and (:assigneeId is null or a.assigneeId = :assigneeId)
        """)
    Page<Asset> search(
        @Param("status") AssetStatus status,
        @Param("assetType") AssetType assetType,
        @Param("assigneeId") String assigneeId,
        Pageable pageable);
}
```

### 3. `AssetService` — `com.nkia.itg.itam.asset.service`

```java
@Service
@Transactional
@RequiredArgsConstructor
public class AssetService {
    private final AssetRepository assetRepository;
    private final MetaService     metaService;   // PUBLISHED 메타 id 캡처용
    /* ... */
}
```

#### `create()` 의사코드 — 메타 버전 보존 핵심

```java
public AssetResponse create(AssetCreateRequest req) {
    // 1) 등록 시점의 PUBLISHED 메타 id 캡처
    PageMetaResponse activeMeta = metaService.getActive(req.pageGroupId());
    String metaIdAtReg = activeMeta.id();         // 예: 'itg-asset-v1-1'

    Asset a = Asset.builder()
        .assetNo(null)                            // 일단 null, save 후 부여
        .name(req.name())
        .assetType(req.assetType())
        .status(AssetStatus.ACTIVE)
        .model(req.model())
        .serialNo(req.serialNo())
        .category(req.category())
        .assigneeId(req.assigneeId())
        .location(req.location())
        .acquiredAt(req.acquiredAt())
        .pageMetaIdAtRegistration(metaIdAtReg)    // ★ 메타 버전 보존
        .build();

    Asset saved = assetRepository.save(a);
    saved.assignAssetNo("AST-" + String.format("%05d", saved.getId()));
    return AssetResponse.from(saved);
}
```

> 메타가 없으면 (`getActive` 가 ITGException META_NOT_PUBLISHED 던짐) 자산 생성도 거부 — 도메인 정합성. 그대로 throw.

#### 다른 메서드 시그니처

```java
@Transactional(readOnly = true)
AssetResponse getById(Long id);                          // METAIL → ITGException ASSET_NOT_FOUND 404

@Transactional(readOnly = true)
AssetResponse getByAssetNo(String assetNo);

@Transactional(readOnly = true)
Page<AssetSummary> search(AssetStatus s, AssetType t, String assigneeId, int page, int size);

AssetResponse update(Long id, AssetUpdateRequest req);
AssetResponse changeStatus(Long id, AssetStatusChangeRequest req);
AssetResponse assign(Long id, AssetAssignRequest req);

/** 이력 복원용: 등록 시점의 메타를 반환 (DEPRECATED·ARCHIVED 라도). */
@Transactional(readOnly = true)
PageMetaResponse getRegistrationMeta(Long assetId);
```

`getRegistrationMeta` 는 `MetaService.getById(asset.pageMetaIdAtRegistration)` 위임 — 어떤 상태든 반환 (이력 화면 복원). DEPRECATED 일지언정 그대로 사용자에게 화면이 그려진다.

### 4. `AssetFixture` — `src/test/java/com/nkia/itg/fixture/AssetFixture.java`

ticket fixture 와 동일 패턴. 단, `pageMetaIdAtRegistration` 도 빌더에 포함 — 기본값 `"itg-asset-v1-1"`.

### 5. 단위 테스트 — `AssetServiceTest`

`@Mock AssetRepository`, `@Mock MetaService`, `@InjectMocks AssetService`. 케이스:

1. `create_시점에_MetaService_getActive_호출_pageMetaIdAtRegistration_저장` — Mock 으로 `metaService.getActive("itg-asset")` → `PageMetaResponse(id="itg-asset-v1-1",...)` 반환. 결과 `pageMetaIdAtRegistration=="itg-asset-v1-1"` 검증.
2. `create_assetNo_AST_5자리_자동_부여` — `save` 가 id=42 반환 시 `AST-00042`.
3. `create_PUBLISHED_메타_없으면_ITGException_그대로_전파` — Mock 이 `META_NOT_PUBLISHED` 던지면 그대로.
4. `getById_없으면_ASSET_NOT_FOUND_404`.
5. `update_RETIRED_불허_IllegalStateException`.
6. `changeStatus_ACTIVE_to_RETIRED_허용_disposedAt_set`.
7. `changeStatus_RETIRED_to_ACTIVE_거부_400`.
8. `assign_RETIRED_불허`.
9. `getRegistrationMeta_등록_시점의_메타_반환` — Mock `metaService.getById("itg-asset-v1-1")` → DEPRECATED 메타 반환해도 정상 반환. 이력 복원 시나리오 보증.
10. `search_파라미터_repository_에_전달` — captor.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend

# 1) 파일
test -f src/main/java/com/nkia/itg/itam/asset/repository/AssetRepository.java
test -f src/main/java/com/nkia/itg/itam/asset/service/AssetService.java
test -f src/main/java/com/nkia/itg/itam/asset/dto/AssetResponse.java
test -f src/main/java/com/nkia/itg/itam/asset/dto/AssetSummary.java
test -f src/main/java/com/nkia/itg/itam/asset/dto/AssetCreateRequest.java
test -f src/test/java/com/nkia/itg/fixture/AssetFixture.java

# 2) Entity 도메인 메서드 보강 (assignAssetNo 외 기존 그대로)
grep -q "assignAssetNo" src/main/java/com/nkia/itg/itam/asset/entity/Asset.java

# 3) 단위 테스트
./gradlew test --tests "com.nkia.itg.itam.asset.service.*"
./gradlew test         # 회귀 (전체)
./gradlew build
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크리스트:
   - Service 가 `@Transactional` 클래스 레벨, 조회 readOnly?
   - `create()` 가 `MetaService.getActive(groupId)` 호출하여 `pageMetaIdAtRegistration` 저장?
   - `assetNo` 가 save 후 dirty checking 으로 `AST-{id5}` 부여?
   - `getRegistrationMeta` 가 어떤 메타 상태든(DEPRECATED 포함) 반환?
   - 상태/RETIRED 가드는 Entity 도메인 메서드 위임?
   - 단위 테스트 10 케이스 통과?
3. step 1 업데이트:
   - 성공 → `"summary": "AssetRepository (findByAssetNo + search Page) + AssetService (create 시 MetaService.getActive 로 pageMetaIdAtRegistration 캡처 + getById/getByAssetNo/search/update/changeStatus/assign/getRegistrationMeta) + DTO Record 6종 + AssetFixture + Mockito 10 케이스. assetNo AST-{id5} 자동 부여(IDENTITY save dirty checking)."`

## 금지사항

- `create()` 에서 `pageMetaIdAtRegistration` 을 직접 받지 마라. `MetaService.getActive(req.pageGroupId())` 로만 결정 — 거짓말 방지.
- `getRegistrationMeta` 에서 메타 상태 필터링 금지. DEPRECATED·ARCHIVED 도 반환해야 이력 복원 가능.
- Service 안에서 Entity 의 필드 직접 set 금지. 도메인 메서드만.
- 사용자 모듈 호출 금지.
- 백엔드 다른 모듈(ticket·meta) 수정 금지.
- 프런트엔드 수정 금지.
- `EntityManager` 직접 주입 native query 운영 코드 금지.
- 테스트에 실 운영 데이터 금지.
