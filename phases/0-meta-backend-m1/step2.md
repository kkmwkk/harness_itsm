# Step 2: meta-domain-and-entity

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "핵심 설계 사상" (`metaStatus` 전이 규칙), "절대 규칙" (Backend Enum 사용 강제)
- `/docs/ARCHITECTURE.md` — §3 메타 모델, §3-4 PageMeta JSON 본문 예시
- `/docs/ADR.md` — ADR-005 (`systemType`·`packageType` 필수), ADR-006 (버전 그룹·복사본은 항상 DRAFT)
- `/phases/0-meta-backend-m1/step1.md` — `page_meta` 스키마 정의 (컬럼명·타입·CHECK 제약)
- `/sql/init/01_schema.sql` — 실제 스키마 (이전 step 산출물)

이전 step 의 스키마를 꼼꼼히 읽고, 컬럼명·타입·CHECK 제약을 엔티티에 정확히 1:1 매핑해야 한다. `ddl-auto: validate` 가 켜져 있으므로 매핑 불일치가 있으면 부팅 자체가 실패한다.

## 작업

이 step 의 목적은 **`page_meta` 의 도메인 모델(Enum 3종 + PageMeta Entity) 을 만들고, 도메인 메서드(`publish()`, `copyAs()`)를 Entity 에 박아두는 것**이다. Repository·Service 는 다음 step.

### 1. Enum 3종 — `com.nkia.itg.meta.domain`

```java
package com.nkia.itg.meta.domain;
public enum SystemType { ITSM, ITAM, PMS, COMMON, SYSTEM }
```

```java
package com.nkia.itg.meta.domain;
public enum PackageType { PACKAGE, CUSTOM }
```

```java
package com.nkia.itg.meta.domain;
public enum MetaStatus { DRAFT, PUBLISHED, DEPRECATED, ARCHIVED }
```

### 2. `PageMeta` Entity — `com.nkia.itg.meta.entity.PageMeta`

요구사항:
- `@Entity @Table(name = "page_meta")`.
- 필드 ↔ 컬럼명 매핑은 스키마와 정확히 일치 (snake_case ↔ camelCase, `@Column(name = "...")` 명시).
- `id String` (PK, 길이 100).
- `title String` (NOT NULL, 길이 200).
- `systemType SystemType` (`@Enumerated(EnumType.STRING) @Column(name="system_type", length=20, nullable=false)`).
- `packageType PackageType` (`@Enumerated(EnumType.STRING) @Column(name="package_type", length=10, nullable=false)`).
- `groupId String`, `majorVersion int`, `minorVersion int` (NOT NULL).
- `metaStatus MetaStatus` (`@Enumerated(EnumType.STRING)`, 기본값은 `DRAFT` — Entity 의 필드 초기값 + DB DEFAULT 양쪽 동일하게).
- `metaJson` — JSONB 매핑.
- `active boolean` (NOT NULL, 기본 `true`).
- `createdAt LocalDateTime`, `updatedAt LocalDateTime` — `@Column(updatable = false)` 로 createdAt 보호, updatedAt 은 DB 트리거(`trg_touch_updated_at`)가 갱신하므로 `@Column(insertable = true, updatable = true)` 로 두되 Entity 에서는 변경하지 않는 read-only 설계가 안전하다. 또는 `@PrePersist` 로 createdAt/updatedAt 초기화하고, DB 트리거의 갱신을 신뢰한다.
- Lombok: `@Getter` 허용, `@Setter` 금지(도메인 메서드로만 변경). `@Builder` 권장, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 와 `@AllArgsConstructor(access = AccessLevel.PRIVATE)` 조합.

#### JSONB 매핑 방법

Hibernate 6.x (Spring Boot 3.x 동봉) 는 `JdbcTypeCode` 로 JSON 매핑이 가능하다:

```java
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "meta_json", columnDefinition = "jsonb", nullable = false)
private Map<String, Object> metaJson;
```

`Map<String, Object>` 타입을 사용한다. 별도 `@TypeDef` / `hibernate-types-60` 의존성 추가는 **금지** (Hibernate 6 내장 기능으로 충분, 의존성을 늘리지 않는다).

#### 도메인 메서드 (Entity 본문에 박아둔다 — Service 가 직접 필드를 만지지 않는다)

```java
/** 배포: DRAFT → PUBLISHED 전이만 허용. 그 외 전이는 도메인 예외를 던진다. */
public void publish() {
    if (this.metaStatus != MetaStatus.DRAFT) {
        throw new IllegalStateException("DRAFT 상태만 배포할 수 있습니다. 현재: " + this.metaStatus);
    }
    this.metaStatus = MetaStatus.PUBLISHED;
}

/** 보관: 어느 상태에서든 가능하나, 이미 ARCHIVED 면 no-op. */
public void archive() {
    this.metaStatus = MetaStatus.ARCHIVED;
}

/**
 * 새 minorVersion 으로 복사. 복사본은 항상 DRAFT 로 시작.
 * @param newId         새 메타 id ('{groupId}-v{major}-{newMinor}' 패턴 권장)
 * @param newMinorVersion 새 minor 번호 (>= 1)
 * @return 새 PageMeta 객체 (영속화는 호출자 책임)
 */
public PageMeta copyAs(String newId, int newMinorVersion) {
    if (newMinorVersion < 1) {
        throw new IllegalArgumentException("minorVersion 은 1 이상이어야 합니다.");
    }
    return PageMeta.builder()
        .id(newId)
        .title(this.title)
        .systemType(this.systemType)
        .packageType(this.packageType)
        .groupId(this.groupId)
        .majorVersion(this.majorVersion)
        .minorVersion(newMinorVersion)
        .metaStatus(MetaStatus.DRAFT)        // ← 복사본은 항상 DRAFT (ADR-006)
        .metaJson(new java.util.HashMap<>(this.metaJson != null ? this.metaJson : java.util.Map.of()))
        .active(true)
        .build();
}

public String versionLabel() {
    return "v" + this.majorVersion + "." + this.minorVersion;
}
```

> `publish()` 가 같은 `groupId` 의 기존 PUBLISHED 를 DEPRECATE 시키는 책임은 **이 Entity 의 것이 아니다** — Repository 의 벌크 update 또는 DB 트리거가 처리한다. Entity 는 단일 행의 상태 전이만 책임진다.

### 3. 단위 테스트 — `com.nkia.itg.meta.entity.PageMetaTest`

다음 케이스를 JUnit 5 로 작성한다 (`given/when/then` 주석 포함, 한글 메서드명 허용):

- `publish_DRAFT_에서_PUBLISHED_로_전이` — `metaStatus` 가 `PUBLISHED` 로 바뀌어야 한다.
- `publish_PUBLISHED_에서_재호출_시_예외` — `IllegalStateException` 발생.
- `publish_ARCHIVED_에서_호출_시_예외` — 동일.
- `archive_어느_상태에서든_ARCHIVED_로_변경` — DRAFT·PUBLISHED·DEPRECATED 입력에 대해 모두 ARCHIVED.
- `copyAs_복사본은_항상_DRAFT_이고_minorVersion_은_지정값` — `minorVersion=3` 으로 복사 시 결과 metaStatus=DRAFT, minorVersion=3.
- `copyAs_원본_metaJson_과_복사본_metaJson_은_독립_인스턴스` — 복사본 map 을 수정해도 원본에 영향 없음.
- `copyAs_minorVersion_0_이하_입력_시_예외`.
- `versionLabel_은_v{major}.{minor}_포맷`.

테스트는 순수 도메인 단위 — `@SpringBootTest` 가 아닌 plain JUnit 으로 작성한다 (스프링 컨텍스트 불필요).

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend
./gradlew test --tests "com.nkia.itg.meta.entity.PageMetaTest"
./gradlew build

# Postgres 컨테이너 기동 후, validate 로 엔티티-스키마 일치 검증
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 8
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'
kill %1
# bootRun 에서 ddl-auto: validate 로 PageMeta ↔ page_meta 매핑 검증 성공 = 부팅 성공
```

## 검증 절차

1. 위 AC 커맨드를 실행한다. `./gradlew test`, `./gradlew build`, `bootRun` + `/actuator/health` 모두 통과해야 한다.
2. 아키텍처 체크리스트:
   - Enum 3종(`SystemType`/`PackageType`/`MetaStatus`)이 `com.nkia.itg.meta.domain` 패키지에 있고, 값 집합이 스키마 CHECK 제약과 1:1 일치하는가?
   - `PageMeta` 의 모든 컬럼 매핑이 스키마와 정확히 일치하는가? (특히 `system_type`·`package_type`·`group_id`·`major_version`·`minor_version`·`meta_status`·`meta_json`·`created_at`·`updated_at`)
   - `metaJson` 이 `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"` 로 매핑되었는가? 별도 `hibernate-types` 의존성을 추가하지 않았는가?
   - `publish()`·`copyAs()`·`archive()` 가 Entity 본문에 있고, Setter 가 없는가? (도메인 메서드로만 상태 변경)
   - `copyAs()` 가 `MetaStatus.DRAFT` 로 시작하는가? (ADR-006)
   - 테스트가 한글 `@DisplayName` 또는 한글 메서드명으로 의도를 잘 드러내는가?
3. 결과에 따라 `phases/0-meta-backend-m1/index.json` 의 step 2 를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "Enum 3종(SystemType/PackageType/MetaStatus) + PageMeta Entity (@JdbcTypeCode(SqlTypes.JSON)) + 도메인 메서드(publish/copyAs/archive) + 단위 테스트 8케이스, ddl-auto: validate 부팅 통과"`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`
   - 사용자 개입 필요 (스키마-엔티티 불일치 미해결, JSONB 매핑 라이브러리 부재 등) → `"status": "blocked"`, `"blocked_reason": "<구체적 사유>"` 후 즉시 중단

## 금지사항

- `PageMeta` 에 `@Setter` 를 붙이지 마라. 이유: 상태 변경 경로를 도메인 메서드(`publish`/`archive`/`copyAs`)로 좁힌다. Setter 가 있으면 잘못된 전이를 막을 수 없다.
- `hibernate-types-60` / `hypersistence-utils` 등 외부 JSONB 매핑 라이브러리를 추가하지 마라. 이유: Hibernate 6 내장 `@JdbcTypeCode(SqlTypes.JSON)` 로 충분하다. 의존성을 늘리지 않는다.
- `publish()` 안에서 다른 `PageMeta` 행을 갱신하지 마라 (DEPRECATE 처리). 이유: Entity 는 단일 행의 상태만 책임진다. 그룹 단위 처리는 Repository/Service/DB 트리거의 책임.
- Repository 인터페이스·Service 클래스를 미리 만들지 마라. 이유: step 4·5 의 책임. 빈 의존성으로 빌드가 깨진다.
- `metaJson` 타입을 `String` 으로 두지 마라. 이유: JSONB 의 인덱스(GIN)·쿼리 기능을 살리지 못한다. `Map<String, Object>` 사용.
- 테스트에 실제 운영 데이터(IP·이메일·사번·서버명) 를 넣지 마라. 가상 샘플(`"샘플-"`, `"itg-sample-..."`, `example.com`) 만 사용. 이유: ADR-011.
- `system_type` 등 컬럼을 `@Enumerated(EnumType.ORDINAL)` 로 매핑하지 마라. 이유: 스키마는 문자열 값(`'ITSM'` 등)을 강제하는 CHECK 제약을 사용한다. `EnumType.STRING` 필수.
