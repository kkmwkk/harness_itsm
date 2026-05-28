# Step 4: meta-repository

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "절대 규칙" (Meta: 화면 노출 메타 조회는 `page_meta_active` 뷰 또는 동등 로직)
- `/docs/ARCHITECTURE.md` — §3-2 화면 노출용 뷰, §3-3 자동 DEPRECATE 트리거, §7 API 설계 (필요 조회 쿼리)
- `/docs/ADR.md` — ADR-006 (버전 그룹·자동 DEPRECATE), ADR-003 (JSONB)
- `/phases/0-meta-backend-m1/step1.md` — `page_meta` 스키마·뷰·트리거 정의
- `/phases/0-meta-backend-m1/step2.md` — `PageMeta` Entity 구조·Enum 정의
- `/phases/0-meta-backend-m1/step3.md` — `ITGException` (필요 시 Repository 호출부에서 던질 수 있음, 단 Repo 자체는 가급적 던지지 않음)

이전 step 의 Entity·Enum 정의를 정확히 따른다. 필드명·패키지 경로 일치.

## 작업

이 step 의 목적은 **`PageMeta` 의 데이터 액세스 레이어를 만들고, Testcontainers Postgres 로 통합 테스트하는 것**이다. Service 의 비즈니스 로직(`publish`, `copy` 등) 은 다음 step.

### 1. `MetaRepository` — `com.nkia.itg.meta.repository.MetaRepository`

`JpaRepository<PageMeta, String>` 상속. 메서드 시그니처:

```java
package com.nkia.itg.meta.repository;

import com.nkia.itg.meta.domain.*;
import com.nkia.itg.meta.entity.PageMeta;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface MetaRepository extends JpaRepository<PageMeta, String> {

    /**
     * 화면 노출용: groupId 기준 PUBLISHED 상태 중 (major, minor) 가 가장 높은 단 1건.
     * page_meta_active 뷰와 동등한 로직을 JPA Derived Query 로 표현.
     */
    Optional<PageMeta> findTopByGroupIdAndMetaStatusAndActiveTrueOrderByMajorVersionDescMinorVersionDesc(
        String groupId, MetaStatus metaStatus
    );

    /** 그룹 전체 버전 이력 (DRAFT 포함, ARCHIVED 제외 옵션은 호출자가 필터). */
    List<PageMeta> findAllByGroupIdOrderByMajorVersionDescMinorVersionDesc(String groupId);

    /**
     * 동일 (groupId, majorVersion) 의 최대 minorVersion.
     * 복사 시 다음 minor 번호 산출용.
     */
    @Query("""
        select max(p.minorVersion)
          from PageMeta p
         where p.groupId      = :groupId
           and p.majorVersion = :majorVersion
        """)
    Optional<Integer> findMaxMinorVersion(@Param("groupId") String groupId,
                                          @Param("majorVersion") int majorVersion);

    /**
     * 동일 groupId 의 기존 PUBLISHED 를 DEPRECATED 로 일괄 전환 (자신 제외).
     * DB 트리거(trg_auto_deprecate)와 이중 안전망. 같은 트랜잭션에서 호출되어야 한다.
     * @return 영향받은 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update PageMeta p
           set p.metaStatus = com.nkia.itg.meta.domain.MetaStatus.DEPRECATED,
               p.updatedAt  = current_timestamp
         where p.groupId    = :groupId
           and p.id         <> :excludeId
           and p.metaStatus = com.nkia.itg.meta.domain.MetaStatus.PUBLISHED
        """)
    int deprecatePublished(@Param("groupId") String groupId,
                           @Param("excludeId") String excludeId);

    /** 모듈별 배포 메타 목록 (PUBLISHED + active=true). */
    List<PageMeta> findAllBySystemTypeAndMetaStatusAndActiveTrueOrderByGroupIdAscMajorVersionDescMinorVersionDesc(
        SystemType systemType, MetaStatus metaStatus
    );

    /** 패키지 구분별 메타 목록 (전체 상태). */
    List<PageMeta> findAllByPackageTypeOrderByGroupIdAscMajorVersionDescMinorVersionDesc(
        PackageType packageType
    );

    /** 시스템 + 패키지 복합 조회 (전체 상태). */
    List<PageMeta> findAllBySystemTypeAndPackageTypeOrderByGroupIdAscMajorVersionDescMinorVersionDesc(
        SystemType systemType, PackageType packageType
    );
}
```

> 메서드명이 길어지는 부담이 있지만 Derived Query 의 명확성을 우선. 필요해지면 QueryDSL 로 리팩터링하되, 이 step 에서는 도입하지 않는다.

> `deprecatePublished` 는 `clearAutomatically + flushAutomatically` 로 영속성 컨텍스트 일관성을 유지한다.

### 2. 통합 테스트 — `com.nkia.itg.meta.repository.MetaRepositoryIT`

Testcontainers 기반. `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + 명시적 Testcontainers `@Container`.

#### 2-1. 베이스 테스트 클래스 (재사용)

`src/test/java/com/nkia/itg/support/PostgresIntegrationTestBase.java`:

```java
package com.nkia.itg.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class PostgresIntegrationTestBase {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("itgdb")
            .withUsername("itg")
            .withPassword("itg1234")
            .withInitScript("init/01_schema.sql");  // src/test/resources/init/01_schema.sql

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
```

`src/test/resources/init/01_schema.sql` 은 `/sql/init/01_schema.sql` 의 사본을 둔다 (Testcontainers `withInitScript` 가 컨테이너 부팅 시 실행). 두 파일이 어긋나지 않도록 **이 step 에서 명시적으로 동기화**하고, 향후에는 빌드 시 복사하도록 Gradle task 를 만들어도 좋다 (이 step 에선 단순 복사).

#### 2-2. 테스트 케이스

`MetaRepositoryIT extends PostgresIntegrationTestBase`. 다음 케이스를 작성한다:

1. `findTopByGroupId..._PUBLISHED_가_여러개일_때_가장_높은_버전_반환` — v1.1·v1.2 PUBLISHED 두 건 직접 INSERT (트리거 우회를 위해 native query 또는 `saveAndFlush` 후 다른 행은 DEPRECATED 강제 갱신 회피 위해 첫 행만 INSERT 후 트리거 영향 검증). **테스트는 트리거를 우회하지 말고 트리거의 동작을 검증하는 형태로 작성**한다 — 즉, 첫 PUBLISHED 행을 saveAndFlush 한 뒤 두 번째 PUBLISHED 행을 saveAndFlush 하면, 첫 행이 DEPRECATED 로 바뀌어 있어야 한다.
2. `findMaxMinorVersion_그룹_내_같은_major_의_최대_minor_반환` — v1.1 / v1.2 / v1.3 + v2.1 입력 후 `findMaxMinorVersion('grp', 1) == 3`, `(grp, 2) == 1`.
3. `findMaxMinorVersion_매칭없으면_Optional_empty`.
4. `deprecatePublished_같은_그룹_다른_PUBLISHED_들을_모두_DEPRECATED` — v1.1·v1.2 PUBLISHED 직접 native insert (트리거 회피 위해 `@Sql` 또는 EntityManager native query) 후 `deprecatePublished("grp", "v1-2")` 호출 → v1-1 만 DEPRECATED 로 바뀌고 v1-2 는 PUBLISHED 유지, 반환값 = 1.
5. `자동_DEPRECATE_트리거_동작_확인` — Repository.save 로 v1-1 PUBLISHED 저장 후, v1-2 를 DRAFT 로 저장 → `metaStatus = PUBLISHED` 로 변경 → flush. v1-1 의 metaStatus 가 DEPRECATED 로 바뀌어야 한다 (트리거 작동 검증).
6. `UNIQUE_제약_위반_시_DataIntegrityViolationException` — 동일 (groupId, major, minor) 두 건 저장 시도.
7. `findAllBySystemType..._필터_및_정렬` — 여러 systemType 의 PUBLISHED·DRAFT 섞어 저장, ITSM PUBLISHED 만 정렬된 순서로 반환.
8. `findAllByPackageType..._필터_및_정렬` — PACKAGE 와 CUSTOM 섞어 저장, PACKAGE 만 반환.

> 트리거 회피가 필요한 경우(예: 케이스 4) 는 `@Sql` 으로 native INSERT 를 사용한다. Repository.save 만 사용하면 자동 DEPRECATE 가 작동해 의도와 어긋날 수 있다.

### 3. Gradle 도움 (선택)

테스트 리소스 동기화를 단순화하기 위해 `backend/build.gradle` 의 `processTestResources` 또는 `test` task 에 schema 복사 task 를 추가하는 것이 가능하지만, 이 step 에서는 **수동 복사**로 충분하다 (한 번뿐). 자동화는 future ADR.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend
./gradlew test --tests "com.nkia.itg.meta.repository.*"
./gradlew test --tests "com.nkia.itg.support.*"   # 베이스 클래스에 별도 테스트 있을 경우
./gradlew build

# Docker 컨테이너로 실 부팅도 여전히 통과
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 8
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'
kill %1
```

> Testcontainers 가 처음 실행될 때 `postgres:16` 이미지를 풀(pull) 한다 — 첫 실행은 시간이 걸린다. Docker Desktop 이 실행 중이어야 한다.

## 검증 절차

1. 위 AC 커맨드를 실행한다. 통합 테스트 8케이스가 모두 통과해야 한다.
2. 아키텍처 체크리스트:
   - `MetaRepository` 의 메서드 시그니처가 위 명세와 일치하는가? (Derived Query 명, JPQL `@Query` 구조)
   - `deprecatePublished` 가 `@Modifying(clearAutomatically=true, flushAutomatically=true)` 인가?
   - 자동 DEPRECATE 트리거 동작이 통합 테스트로 검증되었는가? (트리거 + Service 이중 안전망 — ADR-006)
   - Testcontainers 가 `/sql/init/01_schema.sql` 의 사본(`src/test/resources/init/01_schema.sql`) 으로 스키마를 부팅하는가? 두 파일이 어긋나지 않는가?
   - `ddl-auto: validate` 로 부팅하는가?
3. 결과에 따라 `phases/0-meta-backend-m1/index.json` 의 step 4 를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "MetaRepository (Derived Query 5종 + @Modifying deprecatePublished + findMaxMinorVersion) + PostgresIntegrationTestBase (Testcontainers postgres:16 + withInitScript) + MetaRepositoryIT 8 케이스(트리거 동작·UNIQUE 위반·정렬 검증 포함) 통과. src/test/resources/init/01_schema.sql 동기화."`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`
   - 사용자 개입 필요 (Docker Desktop 미실행, Testcontainers 권한 등) → `"status": "blocked"`, `"blocked_reason": "<구체적 사유>"` 후 즉시 중단

## 금지사항

- `MetaRepository` 에서 `@Transactional` 을 선언하지 마라. 이유: 트랜잭션 경계는 Service 레이어에서만 (ADR 및 CLAUDE.md 규칙).
- 통합 테스트에서 트리거(`trg_auto_deprecate`) 의 동작을 회피·무력화하지 마라 (예: 트리거를 DROP 하고 테스트). 이유: 트리거는 production 안전망이다. 트리거 회피가 필요한 케이스는 의도가 명확한 native INSERT 한정.
- 두 번째 `/init/01_schema.sql` 사본의 내용을 임의 수정하지 마라. 원본(`/sql/init/01_schema.sql`)과 1:1 동일해야 한다. 이유: production 과 테스트 스키마 일치 유지.
- QueryDSL 의존성을 이 step 에서 도입하지 마라. 이유: Derived Query 와 `@Query` 로 이 phase 의 모든 쿼리를 표현 가능. 의존성을 늘리지 않는다.
- `findTop...` 메서드명에 `Active` 조건을 빼지 마라 (`findTopByGroupIdAndMetaStatus...` 형태로 단축). 이유: `active=false` 인 행이 화면에 노출되는 사고를 막기 위해 `AndActiveTrue` 강제.
- Service·Controller 코드를 이 step 에서 작성하지 마라.
- `EntityManager` 를 직접 주입해서 native query 를 운영 코드에 흩뿌리지 마라. 운영 코드는 Repository 메서드를 통해서만 DB 에 접근한다. native query 는 통합 테스트의 트리거 회피 시나리오 한정 허용.
