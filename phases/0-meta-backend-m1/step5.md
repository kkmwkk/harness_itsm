# Step 5: meta-service

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "핵심 설계 사상" (DRAFT 절대 노출 금지, 복사본은 항상 DRAFT, PUBLISHED 전환 시 기존 자동 DEPRECATED), "절대 규칙"(트랜잭션 경계는 Service)
- `/docs/ARCHITECTURE.md` — §7 API 설계 (Service 가 책임지는 비즈니스 동작들), §9 트랜잭션·동시성
- `/docs/ADR.md` — ADR-006 (버전 그룹·복사본 DRAFT 강제·자동 DEPRECATE 이중 안전망), ADR-012 (TestFixture 중앙 관리), ADR-013 (TDD)
- `/phases/0-meta-backend-m1/step2.md` — `PageMeta` Entity 의 도메인 메서드(`publish`/`copyAs`/`archive`)
- `/phases/0-meta-backend-m1/step3.md` — `ITGException`·`ApiResponse`
- `/phases/0-meta-backend-m1/step4.md` — `MetaRepository` 메서드 시그니처 (어떤 쿼리가 가능한지)

이전 step 의 산출물을 꼼꼼히 읽고, Entity 의 도메인 메서드를 Service 에서 호출하는 패턴을 유지하라. Service 가 Entity 의 필드를 직접 변경하지 않는다.

## 작업

이 step 의 목적은 **`MetaService` 비즈니스 로직을 TDD 로 구현하고, `PageMetaResponse`·`PageMetaVersionResponse` DTO 와 `MetaFixture` 를 정착시키는 것**이다. Controller(HTTP 레이어) 는 다음 step.

### 1. DTO Record — `com.nkia.itg.meta.dto`

#### 1-1. `PageMetaResponse`

```java
package com.nkia.itg.meta.dto;

import com.nkia.itg.meta.domain.*;
import com.nkia.itg.meta.entity.PageMeta;
import java.time.LocalDateTime;
import java.util.Map;

public record PageMetaResponse(
    String        id,
    String        title,
    SystemType    systemType,
    PackageType   packageType,
    String        groupId,
    int           majorVersion,
    int           minorVersion,
    MetaStatus    metaStatus,
    Map<String, Object> metaJson,
    boolean       active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static PageMetaResponse from(PageMeta e) {
        return new PageMetaResponse(
            e.getId(), e.getTitle(),
            e.getSystemType(), e.getPackageType(),
            e.getGroupId(), e.getMajorVersion(), e.getMinorVersion(),
            e.getMetaStatus(), e.getMetaJson(),
            e.isActive(),
            e.getCreatedAt(), e.getUpdatedAt()
        );
    }
    public String versionLabel() { return "v" + majorVersion + "." + minorVersion; }
}
```

#### 1-2. `PageMetaVersionResponse`

목록 조회 시 본문(`metaJson`) 을 제외한 경량 응답:

```java
package com.nkia.itg.meta.dto;

import com.nkia.itg.meta.domain.MetaStatus;
import com.nkia.itg.meta.entity.PageMeta;
import java.time.LocalDateTime;

public record PageMetaVersionResponse(
    String        id,
    String        groupId,
    int           majorVersion,
    int           minorVersion,
    MetaStatus    metaStatus,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static PageMetaVersionResponse from(PageMeta e) {
        return new PageMetaVersionResponse(
            e.getId(), e.getGroupId(),
            e.getMajorVersion(), e.getMinorVersion(),
            e.getMetaStatus(), e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
```

### 2. `MetaService` — `com.nkia.itg.meta.service.MetaService`

```java
package com.nkia.itg.meta.service;

@Service
@Transactional       // 클래스 레벨 — Service 의 모든 메서드를 트랜잭션 안에서.
@RequiredArgsConstructor
public class MetaService {
    private final MetaRepository metaRepository;
    /* ... */
}
```

#### 2-1. 메서드 시그니처 (모두 public)

```java
/** 화면 노출용: groupId 기준 PUBLISHED 최신 1건. 없으면 ITGException("META_NOT_PUBLISHED", 404). */
@Transactional(readOnly = true)
PageMetaResponse getActive(String groupId);

/** 특정 메타 단건 (이력 복원용). 없으면 ITGException("META_NOT_FOUND", 404). */
@Transactional(readOnly = true)
PageMetaResponse getById(String metaId);

/** 그룹 전체 버전 이력 (DRAFT 포함). 빈 그룹은 빈 List 반환 (예외 X). */
@Transactional(readOnly = true)
List<PageMetaVersionResponse> getVersions(String groupId);

/**
 * 배포: DRAFT → PUBLISHED.
 * 동일 groupId 의 기존 PUBLISHED 는 자동 DEPRECATED (Service 레이어 명시 호출 + DB 트리거 이중 안전망).
 * @throws ITGException META_NOT_FOUND (404) | INVALID_STATUS (400, DRAFT 가 아닌 메타)
 */
PageMetaResponse publish(String metaId);

/**
 * 보관: → ARCHIVED. 어느 상태에서든 가능. 이미 ARCHIVED 면 no-op (현재 상태 그대로 응답).
 * @throws ITGException META_NOT_FOUND (404)
 */
PageMetaResponse archive(String metaId);

/**
 * 복사: 동일 (groupId, majorVersion) 의 최대 minor + 1 로 신규 DRAFT 생성.
 * 복사본 id 는 '{groupId}-v{major}-{newMinor}' 패턴.
 * @throws ITGException META_NOT_FOUND (404)
 */
PageMetaResponse copy(String metaId);

/** 모듈별 배포 메타 목록 (PUBLISHED + active=true). */
@Transactional(readOnly = true)
List<PageMetaResponse> getActiveBySystem(SystemType systemType);

/** 패키지 구분별 메타 목록 (전체 상태). */
@Transactional(readOnly = true)
List<PageMetaResponse> getByPackage(PackageType packageType);

/** 시스템 + 패키지 복합 조회. */
@Transactional(readOnly = true)
List<PageMetaResponse> getBySystemAndPackage(SystemType systemType, PackageType packageType);
```

#### 2-2. `publish` 핵심 의사코드

```java
public PageMetaResponse publish(String metaId) {
    PageMeta target = metaRepository.findById(metaId)
        .orElseThrow(() -> new ITGException(
            "META_NOT_FOUND", "메타를 찾을 수 없습니다: " + metaId, HttpStatus.NOT_FOUND));

    if (target.getMetaStatus() != MetaStatus.DRAFT) {
        throw new ITGException(
            "INVALID_STATUS",
            "DRAFT 상태만 배포 가능합니다. 현재 상태: " + target.getMetaStatus(),
            HttpStatus.BAD_REQUEST);
    }

    // ① Service 레이어 명시 처리: 기존 PUBLISHED → DEPRECATED.
    //    DB 트리거(trg_auto_deprecate) 와 이중 안전망 (ADR-006).
    metaRepository.deprecatePublished(target.getGroupId(), target.getId());

    // ② 도메인 메서드로 상태 전이.
    target.publish();   // 내부에서 DRAFT 가 아니면 IllegalStateException → GlobalHandler 가 400 처리

    return PageMetaResponse.from(metaRepository.save(target));
}
```

#### 2-3. `copy` 핵심 의사코드

```java
public PageMetaResponse copy(String metaId) {
    PageMeta origin = metaRepository.findById(metaId)
        .orElseThrow(() -> new ITGException(
            "META_NOT_FOUND", "메타를 찾을 수 없습니다: " + metaId, HttpStatus.NOT_FOUND));

    int nextMinor = metaRepository
        .findMaxMinorVersion(origin.getGroupId(), origin.getMajorVersion())
        .orElse(0) + 1;

    String newId = "%s-v%d-%d".formatted(origin.getGroupId(), origin.getMajorVersion(), nextMinor);
    PageMeta copied = origin.copyAs(newId, nextMinor);   // Entity 도메인 메서드, 항상 DRAFT (ADR-006)

    return PageMetaResponse.from(metaRepository.save(copied));
}
```

> `getActive` 는 `findTopByGroupIdAndMetaStatusAndActiveTrueOrderByMajorVersionDescMinorVersionDesc(groupId, PUBLISHED)` 사용.

> 모든 변환은 `PageMetaResponse.from(...)` / `PageMetaVersionResponse.from(...)` 의 정적 메서드를 통해. Service 는 Entity 를 외부로 노출하지 않는다.

### 3. `MetaFixture` — `src/test/java/com/nkia/itg/fixture/MetaFixture.java`

```java
package com.nkia.itg.fixture;

import com.nkia.itg.meta.domain.*;
import com.nkia.itg.meta.entity.PageMeta;
import java.util.Map;

public final class MetaFixture {
    private MetaFixture() {}

    public static PageMeta.PageMetaBuilder baseBuilder(String groupId, int major, int minor) {
        return PageMeta.builder()
            .id("%s-v%d-%d".formatted(groupId, major, minor))
            .title("샘플 페이지 " + groupId)
            .systemType(SystemType.ITSM)
            .packageType(PackageType.PACKAGE)
            .groupId(groupId)
            .majorVersion(major)
            .minorVersion(minor)
            .metaJson(Map.of())
            .active(true);
    }

    public static PageMeta draft(String groupId, int major, int minor) {
        return baseBuilder(groupId, major, minor).metaStatus(MetaStatus.DRAFT).build();
    }

    public static PageMeta published(String groupId, int major, int minor) {
        return baseBuilder(groupId, major, minor).metaStatus(MetaStatus.PUBLISHED).build();
    }

    public static PageMeta archived(String groupId, int major, int minor) {
        return baseBuilder(groupId, major, minor).metaStatus(MetaStatus.ARCHIVED).build();
    }
}
```

### 4. 단위 테스트 — `com.nkia.itg.meta.service.MetaServiceTest`

`@ExtendWith(MockitoExtension.class)`, `@Mock MetaRepository`, `@InjectMocks MetaService`. 케이스:

1. `publish_성공_시_기존_PUBLISHED_를_DEPRECATED_처리하고_본인은_PUBLISHED` — `deprecatePublished(groupId, id)` 호출 + 결과 `metaStatus == PUBLISHED` 검증.
2. `publish_DRAFT_가_아닌_메타는_ITGException_INVALID_STATUS_400`.
3. `publish_존재하지_않는_메타는_ITGException_META_NOT_FOUND_404`.
4. `copy_새_minor_는_기존_최대_minor_플러스_1_이고_DRAFT` — `findMaxMinorVersion("grp",1) → 2` 일 때, 새 행의 minorVersion=3, metaStatus=DRAFT, id="grp-v1-3" 검증.
5. `copy_매핑이_없으면_minor_는_1` — `findMaxMinorVersion → empty` 시 새 minor=1 (실제로는 원본 own 의 minor 와 같지 않을 수 있음, 따라서 기대값 = `orElse(0) + 1` = 1).
6. `copy_원본_없으면_META_NOT_FOUND`.
7. `getActive_그룹의_PUBLISHED_가_없으면_META_NOT_PUBLISHED_404` — `findTopBy... → empty` 시 ITGException.
8. `getActive_정상_경로` — Mock 으로 PUBLISHED 1건 반환 시 응답 DTO 값 검증.
9. `getVersions_정렬_및_경량_DTO_변환` — 3건 반환 시 `PageMetaVersionResponse` 로 변환되고 `metaJson` 필드가 없음.
10. `archive_어떤_상태든_ARCHIVED_변환` — DRAFT/PUBLISHED/DEPRECATED 입력 시 모두 ARCHIVED. 단 기존 PUBLISHED 를 archive 해도 deprecatePublished 는 호출되지 않는다 (publish 만 트리거).
11. `getActiveBySystem` / `getByPackage` / `getBySystemAndPackage` — Mock 결과를 PageMetaResponse 리스트로 변환.

> **테스트는 한글 메서드명 또는 `@DisplayName` 한글 표기**. `given / when / then` 주석으로 단계 구분.

> 시간 의존 코드(`createdAt`/`updatedAt`)는 검증에서 제외하거나 `assertThat(...).isNotNull()` 정도로만 다룬다. Clock 추상화는 이 phase 범위 아님.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend
./gradlew test --tests "com.nkia.itg.meta.service.*"
./gradlew test --tests "com.nkia.itg.fixture.*"   # MetaFixture 자체 검증 테스트가 있다면
./gradlew test                                     # 전체 테스트 (이전 step 까지 깨지지 않음 확인)
./gradlew build

# 부팅 확인
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 8
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'
kill %1
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. 단위 테스트 11 케이스 + 이전 step 통합 테스트 모두 통과해야 한다.
2. 아키텍처 체크리스트:
   - `MetaService` 가 `@Transactional` 클래스 레벨, 조회 메서드는 `@Transactional(readOnly = true)` 인가?
   - `publish` 가 `deprecatePublished` 를 명시 호출하는가? (Service + DB 트리거 이중 안전망 — ADR-006)
   - `copy` 가 `findMaxMinorVersion + 1` 으로 새 minor 를 산출하고, Entity 의 `copyAs()` 를 사용해 항상 DRAFT 로 시작하는가?
   - DTO 변환이 `PageMetaResponse.from(...)` / `PageMetaVersionResponse.from(...)` 정적 메서드를 통해서만 일어나는가?
   - `MetaService` 가 `PageMeta` Entity 의 필드를 직접 set 하지 않는가? (도메인 메서드 호출만)
   - `MetaFixture` 가 `src/test/java/com/nkia/itg/fixture/` 에 위치하고 빌더 베이스를 공유하는가? (ADR-012)
   - 예외는 `ITGException` 으로 일관되게 던지는가? (errorCode, httpStatus 명시)
3. 결과에 따라 `phases/0-meta-backend-m1/index.json` 의 step 5 를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "MetaService (publish/copy/archive/getActive/getById/getVersions/getActiveBySystem/getByPackage/getBySystemAndPackage) + PageMetaResponse/PageMetaVersionResponse Record + MetaFixture (draft/published/archived) + Mockito 단위 테스트 11 케이스. publish 가 deprecatePublished + Entity.publish() 이중 안전망 호출."`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`
   - 사용자 개입 필요 → `"status": "blocked"`, `"blocked_reason": "<구체적 사유>"` 후 즉시 중단

## 금지사항

- `MetaService` 메서드에서 Entity 의 필드를 직접 set 하지 마라 (예: `target.setMetaStatus(PUBLISHED)`). 이유: 도메인 규칙(`publish()` 는 DRAFT 만 허용)을 우회할 수 있다. 도메인 메서드를 호출하라.
- `publish` 에서 `deprecatePublished` 를 호출하지 않고 "DB 트리거가 해주니까" 라며 생략하지 마라. 이유: ADR-006 의 이중 안전망 원칙. 트리거가 없거나 비활성화된 환경에서도 안전해야 한다.
- `copy` 시 원본의 `metaStatus` 를 그대로 복사하지 마라 (예: 원본이 PUBLISHED 면 복사본도 PUBLISHED). 이유: ADR-006 — 복사본은 항상 DRAFT.
- `copy` 시 새 id 를 임의 UUID 등으로 만들지 마라. 이유: id 규약 `{groupId}-v{major}-{minor}` 패턴 유지. 검색·디버깅 시 의미가 보이는 식별자.
- `getActive` 의 결과에 `DRAFT`·`DEPRECATED`·`ARCHIVED` 가 섞일 수 있는 쿼리를 사용하지 마라. Repository 의 `findTop...MetaStatusAndActiveTrue...` 시그니처를 그대로 사용. 이유: 화면에 DRAFT 노출 사고 차단.
- 통합 테스트(Testcontainers) 를 이 step 에서 추가하지 마라. 이유: 단위 테스트(Mockito) 가 Service 의 책임 범위. Repository 의 실 DB 동작은 step 4 가 책임. 중복 회피.
- `MetaFixture` 에 실제 운영 사번·이메일·서버명 등을 넣지 마라. 이유: ADR-011.
- DTO 변환 메서드를 Service 안에 인라인으로 작성하지 마라 (`new PageMetaResponse(...)` 호출 산재). 이유: 변환 책임은 DTO 정적 팩토리에. Service 는 변환을 호출만.
- 직접 `EntityManager` 를 주입해 native query 를 작성하지 마라. 이유: Repository 추상화 우회. Repository 메서드를 통해서만.
