# Step 1: asset-category-schema

## 읽어야 할 파일
- `/CLAUDE.md` v2.1·`/docs/PRD.md` §4-3·`/docs/ARCHITECTURE.md` §14-3
- `/phases/12-domain-depth-workflow-category/step0.md`
- `/sql/init/05_asset.sql`·`/backend/.../itam/asset/entity/Asset.java`

## 작업
자산 분류 트리(AssetCategory) + 자산 이력 이벤트(AssetLifecycleEvent) 도입. 기존 Asset 에 `category_code` 컬럼 추가.

### 1. DB — `sql/init/12_itam_category.sql`
```sql
CREATE TABLE IF NOT EXISTS asset_category (
  code              VARCHAR(40) PRIMARY KEY,     -- 'HW_LAPTOP'/'HW_SERVER'/'SW_LICENSE'/'CONTRACT_NDA'
  label             VARCHAR(80) NOT NULL,
  parent_code       VARCHAR(40),
  path              VARCHAR(255) NOT NULL,       -- '/HW/HW_LAPTOP/' 형태
  form_meta_group_id VARCHAR(100),               -- 'itg-asset-hw-laptop'
  active            BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order        INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_ac_parent FOREIGN KEY (parent_code) REFERENCES asset_category(code)
);
CREATE INDEX IF NOT EXISTS idx_ac_parent ON asset_category(parent_code);
CREATE INDEX IF NOT EXISTS idx_ac_path   ON asset_category(path);

ALTER TABLE asset ADD COLUMN IF NOT EXISTS category_code VARCHAR(40);
ALTER TABLE asset
  ADD CONSTRAINT fk_asset_category FOREIGN KEY (category_code) REFERENCES asset_category(code);
CREATE INDEX IF NOT EXISTS idx_asset_category ON asset(category_code);

CREATE TABLE IF NOT EXISTS asset_lifecycle_event (
  id            BIGSERIAL PRIMARY KEY,
  asset_id      BIGINT NOT NULL,
  event_type    VARCHAR(20) NOT NULL
                CONSTRAINT chk_ale_event CHECK (event_type IN
                  ('ACQUIRED','TRANSFERRED','REPAIRED','DISPOSED','RENEWED')),
  event_date    DATE NOT NULL DEFAULT CURRENT_DATE,
  by_user_id    BIGINT,
  payload       JSONB,
  created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_ale_asset FOREIGN KEY (asset_id) REFERENCES asset(id)
);
CREATE INDEX IF NOT EXISTS idx_ale_asset ON asset_lifecycle_event(asset_id, event_date DESC);

DROP TRIGGER IF EXISTS trg_ac_touch ON asset_category;
CREATE TRIGGER trg_ac_touch BEFORE UPDATE ON asset_category FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
```

### 2. Entity·Service·Repository
- `com.nkia.itg.itam.category.entity.AssetCategory` — path 자동 (Department 패턴 재사용).
- `AssetCategoryService.create/update/move/deactivate/getTree`.
- `AssetCategoryRepository extends JpaRepository<AssetCategory, String>`.
- `Asset` Entity 확장: `categoryCode` 필드.
- `AssetLifecycleEvent` Entity + `AssetLifecycleService.record(assetId, eventType, byUser, payload)`.
- 도메인 메서드: `AssetCategory.moveTo(newParent)` 자기·자손 가드.

### 3. AssetService 확장
- `create()` 가 `req.categoryCode` 받아 검증 + Asset.categoryCode set.
- `getAssetWithRegistrationMeta` — pageMetaIdAtRegistration 유지.
- `recordLifecycleEvent(assetId, ...)` — AssetLifecycleService 위임.

### 4. 단위 테스트
- `AssetCategoryService_create_path_자동` / `move_자기_자손_거부` / `getTree_path_정렬`.
- `AssetService_create_categoryCode_검증` / `recordLifecycleEvent_정상`.

## Acceptance Criteria
```bash
test -f sql/init/12_itam_category.sql
grep -q "CREATE TABLE IF NOT EXISTS asset_category"        sql/init/12_itam_category.sql
grep -q "CREATE TABLE IF NOT EXISTS asset_lifecycle_event" sql/init/12_itam_category.sql

cd backend
test -f src/main/java/com/nkia/itg/itam/category/entity/AssetCategory.java
test -f src/main/java/com/nkia/itg/itam/category/service/AssetCategoryService.java
test -f src/main/java/com/nkia/itg/itam/asset/entity/AssetLifecycleEvent.java
./gradlew test --tests "com.nkia.itg.itam.*"
./gradlew build

docker-compose up -d
docker exec -i itg-postgres psql -U itg -d itgdb < sql/init/12_itam_category.sql
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun &
sleep 10
curl -fsS http://localhost:8080/actuator/health | grep -q UP
kill %1
```

## 금지사항
- 분류 path 자동 계산을 DB 트리거로 구현 금지. Service.
- AssetCategory.moveTo 자기·자손 허용 금지.
- AssetLifecycleEvent 의 event_type 을 코드 안에서 String 하드코딩 금지 — Enum.
- 백엔드 다른 모듈 깨뜨리지 마라.
- 프런트엔드 수정 금지.
