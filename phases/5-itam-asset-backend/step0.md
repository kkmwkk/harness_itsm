# Step 0: asset-schema-and-entity

## 읽어야 할 파일

- `/CLAUDE.md` — "핵심 설계 사상" (메타 버전 그룹), "절대 규칙" (Java 21·Enum·`ddl-auto: validate`)
- `/docs/PRD.md` — §5-2 활용 사례 ITAM 자산원장 이력 관리 (등록 시점의 메타 버전 보존)
- `/docs/ARCHITECTURE.md` — §3 메타 모델, §2-3 백엔드 패키지 구조 (`itam/` 모듈 위치)
- `/docs/ADR.md` — ADR-006 (버전 그룹), ADR-013 (TDD)
- `/sql/init/01_schema.sql` — page_meta·트리거·`fn_touch_updated_at()`
- `/sql/init/02_ticket.sql` — ticket 패턴 참고
- `/backend/src/main/java/com/nkia/itg/itsm/ticket/entity/Ticket.java`·`domain/*` — ticket 스타일 참고
- `/backend/src/main/java/com/nkia/itg/meta/entity/PageMeta.java` — page_meta 의 id 컬럼 타입 (`VARCHAR(100)`)

ticket phase 3 의 패턴을 자산 도메인에 적용하되, **자산 이력 메타 보존**을 위한 추가 컬럼이 핵심.

## 작업

이 step 의 목적은 **ITAM 자산원장 스키마 + Enum + Entity + 상태 전이를 만들고, 자산 등록 시점의 메타 ID 를 보존하는 컬럼·매핑을 박는 것**이다.

### 1. DB 스키마 — `sql/init/05_asset.sql`

```sql
CREATE TABLE IF NOT EXISTS asset (
  id           BIGSERIAL PRIMARY KEY,
  asset_no     VARCHAR(32),                                            -- nullable, UNIQUE, IDENTITY 호환
  name         VARCHAR(200) NOT NULL,
  asset_type   VARCHAR(20)  NOT NULL
                CONSTRAINT chk_asset_type
                CHECK (asset_type IN ('HARDWARE','SOFTWARE','LICENSE','CONTRACT','SERVICE')),
  status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                CONSTRAINT chk_asset_status
                CHECK (status IN ('ACTIVE','STORAGE','RETIRED','REPLACED')),
  model        VARCHAR(100),
  serial_no    VARCHAR(60),
  category     VARCHAR(40),
  assignee_id  VARCHAR(60),
  location     VARCHAR(100),
  acquired_at  DATE,
  disposed_at  DATE,
  -- ────────────────────────────────────────────────────────────────
  -- 자산 등록 시점의 메타 ID 보존 (PRD §5-2 활용 사례)
  -- ────────────────────────────────────────────────────────────────
  page_meta_id_at_registration  VARCHAR(100) NOT NULL,                  -- 예: 'itg-asset-v1-1'
  created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_asset_no UNIQUE (asset_no),
  CONSTRAINT fk_asset_meta FOREIGN KEY (page_meta_id_at_registration)
    REFERENCES page_meta(id)
);

CREATE INDEX IF NOT EXISTS idx_asset_status     ON asset(status);
CREATE INDEX IF NOT EXISTS idx_asset_type       ON asset(asset_type);
CREATE INDEX IF NOT EXISTS idx_asset_assignee   ON asset(assignee_id);
CREATE INDEX IF NOT EXISTS idx_asset_created_at ON asset(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_asset_meta_at_reg ON asset(page_meta_id_at_registration);

DROP TRIGGER IF EXISTS trg_asset_touch_updated_at ON asset;
CREATE TRIGGER trg_asset_touch_updated_at
BEFORE UPDATE ON asset
FOR EACH ROW
EXECUTE FUNCTION fn_touch_updated_at();
```

> **FK 가 `page_meta(id)` 를 가리킴** — 자산 등록 시 사용한 메타가 DEPRECATED·ARCHIVED 되어도 자산 행에서 그 메타를 참조 가능 (메타는 물리 삭제 안 함, 상태만 전이).

### 2. Enum 2종 — `com.nkia.itg.itam.asset.domain`

```
backend/src/main/java/com/nkia/itg/itam/asset/
  ├── domain/
  │   ├── AssetType.java     enum { HARDWARE, SOFTWARE, LICENSE, CONTRACT, SERVICE }
  │   └── AssetStatus.java   enum { ACTIVE, STORAGE, RETIRED, REPLACED }
  ├── entity/
  │   └── Asset.java
  ├── repository/   (step 1)
  ├── service/      (step 1)
  ├── controller/   (step 2)
  └── dto/          (step 1·2)
```

### 3. `Asset` Entity — `com.nkia.itg.itam.asset.entity.Asset`

- 매핑은 컬럼명·타입·nullable 일치.
- PK: `Long id` (`@GeneratedValue(strategy = GenerationType.IDENTITY)`).
- `asset_no` nullable (ticket 패턴 동일 — IDENTITY 즉시 INSERT 호환).
- `assetType`·`status` Enum.STRING.
- `acquiredAt`·`disposedAt` 은 `LocalDate`.
- `pageMetaIdAtRegistration String` — NOT NULL, 직접 set (FK 로 page_meta).
- Lombok `@Getter`·`@Builder`·`@NoArgsConstructor(PROTECTED)`. `@Setter` 금지.

#### 도메인 메서드 (Entity)

```java
/** 자산번호 한 번만 부여. */
public void assignAssetNo(String assetNo) {
    if (this.assetNo != null && !this.assetNo.isBlank()) {
        throw new IllegalStateException("asset_no 는 이미 부여되었습니다: " + this.assetNo);
    }
    this.assetNo = assetNo;
}

/**
 * 상태 전이. 허용 매트릭스:
 *   ACTIVE   → STORAGE / RETIRED / REPLACED
 *   STORAGE  → ACTIVE / RETIRED
 *   REPLACED → RETIRED
 *   RETIRED  → 전이 불가 (도메인 예외)
 *
 * RETIRED 전이 시 disposedAt = LocalDate.now() (이미 set 이면 보존).
 */
public void changeStatus(AssetStatus next) {
    if (this.status == AssetStatus.RETIRED) {
        throw new IllegalStateException(
            "RETIRED 자산은 상태를 변경할 수 없습니다. (자산: " + this.assetNo + ")");
    }
    if (this.status == next) return;
    final boolean allowed = switch (this.status) {
        case ACTIVE   -> EnumSet.of(AssetStatus.STORAGE, AssetStatus.RETIRED, AssetStatus.REPLACED).contains(next);
        case STORAGE  -> EnumSet.of(AssetStatus.ACTIVE,  AssetStatus.RETIRED).contains(next);
        case REPLACED -> EnumSet.of(AssetStatus.RETIRED).contains(next);
        case RETIRED  -> false;
    };
    if (!allowed) {
        throw new IllegalStateException(
            "허용되지 않은 상태 전이: " + this.status + " → " + next);
    }
    this.status = next;
    if (next == AssetStatus.RETIRED && this.disposedAt == null) {
        this.disposedAt = LocalDate.now();
    }
}

public void assign(String assigneeId) {
    if (this.status == AssetStatus.RETIRED) {
        throw new IllegalStateException("RETIRED 자산은 담당자 할당 불가.");
    }
    this.assigneeId = assigneeId;
}

public void updateAttributes(String name, String model, String serialNo,
                              String category, String location) {
    if (this.status == AssetStatus.RETIRED) {
        throw new IllegalStateException("RETIRED 자산은 속성 변경 불가.");
    }
    if (name      != null) this.name      = name;
    if (model     != null) this.model     = model;
    if (serialNo  != null) this.serialNo  = serialNo;
    if (category  != null) this.category  = category;
    if (location  != null) this.location  = location;
}
```

### 4. 단위 테스트 — `AssetTest`

케이스:
1. `changeStatus_ACTIVE_to_STORAGE_허용`.
2. `changeStatus_ACTIVE_to_RETIRED_허용_disposedAt_set`.
3. `changeStatus_STORAGE_to_ACTIVE_허용`.
4. `changeStatus_REPLACED_to_RETIRED_허용`.
5. `changeStatus_RETIRED_불허_IllegalStateException`.
6. `changeStatus_매트릭스_외_전이_불허` (예: ACTIVE → REPLACED ← OK, STORAGE → REPLACED ← X).
7. `assignAssetNo_이미_set_되어_있으면_예외`.
8. `assign_RETIRED_불허`.
9. `updateAttributes_RETIRED_불허`.
10. `updateAttributes_부분_업데이트_null_은_변경_없음`.
11. `상태_전이_시_disposedAt_이미_set_이면_보존`.

## Acceptance Criteria

```bash
# 1) 파일
test -f /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/sql/init/05_asset.sql
grep -q "page_meta_id_at_registration" sql/init/05_asset.sql
grep -q "REFERENCES page_meta(id)" sql/init/05_asset.sql

cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/backend
test -f src/main/java/com/nkia/itg/itam/asset/domain/AssetType.java
test -f src/main/java/com/nkia/itg/itam/asset/domain/AssetStatus.java
test -f src/main/java/com/nkia/itg/itam/asset/entity/Asset.java

# 2) 단위 테스트
./gradlew test --tests "com.nkia.itg.itam.asset.entity.AssetTest"
./gradlew build

# 3) DB 적용 + 부팅 validate
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM
docker-compose up -d
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/05_asset.sql
docker exec -i itg-postgres psql -U itg -d itgdb -c "\d asset" | grep -q page_meta_id_at_registration

cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'
kill %1
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크리스트:
   - `page_meta_id_at_registration` 컬럼이 NOT NULL + FK → page_meta(id)?
   - Enum 값이 스키마 CHECK 와 1:1?
   - `asset_no` 가 nullable (IDENTITY 호환, ticket 패턴 동일)?
   - 도메인 메서드가 Entity 본문에 있고 Setter 없음?
   - 상태 매트릭스 (ACTIVE→STORAGE/RETIRED/REPLACED, STORAGE→ACTIVE/RETIRED, REPLACED→RETIRED, RETIRED→불가)?
   - 단위 테스트 11 케이스 통과?
3. step 0 업데이트:
   - 성공 → `"summary": "sql/init/05_asset.sql (asset 테이블 + page_meta_id_at_registration NOT NULL FK→page_meta + 인덱스 5종 + updated_at 트리거) + Enum 2종(AssetType/AssetStatus) + Asset Entity (asset_no nullable·@Enumerated.STRING·도메인 메서드 changeStatus 매트릭스 + RETIRED 가드 assign/updateAttributes/assignAssetNo) + 단위 테스트 11 케이스. ddl-auto: validate 부팅 통과."`

## 금지사항

- `Asset` 에 `@Setter` 금지.
- `asset_no` 를 NOT NULL 로 설정하지 마라 (ticket 의 hotfix 8d636a2 와 같은 결함 회피).
- `page_meta_id_at_registration` 을 nullable 로 두지 마라. 이유: 자산 등록 시점의 메타 보존이 본 도메인의 핵심 제약 (PRD §5-2).
- `RETIRED → ACTIVE` 같은 역방향 전이 허용 금지.
- ticket 모듈을 수정하지 마라.
- 프런트엔드 수정 금지.
- 단위 테스트에 실 운영 데이터 금지. 가상 샘플 (`샘플 자산`·`AST-SAMPLE-*`).
