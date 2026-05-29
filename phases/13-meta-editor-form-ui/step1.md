# Step 1: meta-editor-list-page

## 읽어야 할 파일
- `/docs/PRD.md` §5-4·`/docs/ARCHITECTURE.md` §16
- `/phases/13-meta-editor-form-ui/step0.md`
- `/frontend/src/pages/system/MetaPage.vue` (기존 메타 viewer)

## 작업
**`/system/meta-editor` 진입점 페이지** — 메타 그룹·버전 목록 + 신규 그룹·복사·편집 진입.

### 1. 라우터·메뉴
- `/system/meta-editor` 라우트 추가, 메뉴 시드에 등록 (permission_code='META_EDIT').

### 2. `pages/system/MetaEditorPage.vue`
- 좌측 패널: 모든 group_id 그룹의 리스트 (`GET /api/meta/groups` — 신규 endpoint 또는 기존 `/api/meta?groupBy=group_id` — 단순 구현 위해 백엔드에 group 목록 endpoint 추가 또는 client 측 집계).
  - **권장**: 백엔드 `GET /api/meta/groups` 추가 — group_id, 최신 PUBLISHED 버전, DRAFT 존재 여부 반환.
- 우측 패널: 선택된 group_id 의 버전 이력 (`GET /api/meta/group/{groupId}/versions` — 기존).
- 액션 버튼:
  - "신규 그룹 만들기" → 다이얼로그 (group_id·title·systemType·packageType·major·minor 입력 → `POST /api/meta` 의 빈 본문 DRAFT 생성).
  - 각 버전 행: "편집(DRAFT 만)" / "복사" / "발행" / "보관".
  - "편집" 클릭 → `/system/meta-editor/:metaId` 로 라우팅 (다음 step 들의 편집 화면).

### 3. UI 패턴
- 데이터 그리드: shadcn DataTable.
- 각 버전 행에 status 뱃지(StatusBadge 재사용 — DRAFT/PUBLISHED/DEPRECATED/ARCHIVED).
- 미리보기 버튼 → 새 탭 또는 사이드 패널에서 `<DynamicPage :metaId="..." :rows="[]" />` 렌더 (props.rows=[] 로 빈 그리드, 폼 미리보기 다이얼로그 가능).

### 4. 권한
- `META_EDIT` 또는 ROLE_ADMIN 만 접근 — RequirePermission 컴포넌트 사용.

### 5. 테스트
- 라우트 200 + 권한 없는 사용자 진입 시 빈 화면 또는 redirect (frontend 단 가드).

## Acceptance Criteria
```bash
cd frontend
test -f src/pages/system/MetaEditorPage.vue
grep -q "/system/meta-editor" src/router/index.ts
grep -q "META_EDIT" src/pages/system/MetaEditorPage.vue

pnpm type-check
pnpm lint
pnpm build
pnpm test

pnpm dev &
sleep 6
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/system/meta-editor)" = "200"
kill %1
```

## 금지사항
- DRAFT 가 아닌 메타에 "편집" 버튼 노출 금지. 대신 "복사 후 편집".
- 메타 그룹 목록 client 측 hard-coded 금지 — `/api/meta/groups` 또는 동등 endpoint.
- 미리보기 렌더 시 메타가 깨진 경우 page 전체 깨짐 금지 (asPageMetaBody 로 좁히기).
- 백엔드 수정은 group 목록 endpoint 만 — 그 외 수정 금지.
- 운영 코드 console.log 금지.
