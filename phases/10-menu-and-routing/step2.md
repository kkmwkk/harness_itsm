# Step 2: system-admin-pages

## 읽어야 할 파일

- `/CLAUDE.md` 절대 규칙
- `/docs/ARCHITECTURE.md` §7-3 사용자·부서·역할·권한·메뉴 API
- `/phases/9-auth-and-users/index.json` step 3 summary — endpoints 일람
- `/phases/10-menu-and-routing/step0~1.md`
- `/frontend/src/components/ui/data-table/DataTable.vue` (목록 사용)
- `/frontend/src/components/dynamic/DynamicForm.vue` (재사용 — 단순 폼)

## 작업

이 step 의 목적은 **시스템 관리 페이지 4종(`UserPage`·`DeptPage`·`RolePage`·`MenuPage`) 을 정적 페이지로 구현**하는 것이다. 메타-드리븐 DynamicPage 가 아닌 직접 작성. 이유: 메뉴 트리·사용자 권한 같은 메타-관리 자체를 DynamicPage 로 만들면 재귀적이고, 또 권한 가드(USER_ADMIN 만 가능 등) 가 화면 단에서 명확해야 안전.

### 1. 공통 UI 패턴

각 페이지는 `PageHeader` + 상단 검색·등록 버튼 + `DataTable` + 등록·편집 다이얼로그 (DynamicForm 또는 simple form). useDataMutation + 토스트.

### 2. `UserPage.vue` — `/system/users`

요구 기능 (Step 9 의 백엔드 endpoints 위에서):
- 목록: `GET /api/users?page&size&kw&deptId&role&status` → DataTable (ID·username·name·email·department·status·roles).
- 검색바: 키워드·부서·역할·상태 필터.
- 등록 버튼 → DynamicForm 또는 직접 폼 (`POST /api/users`).
- 행 클릭 → 편집 다이얼로그 (`PATCH /api/users/{id}`).
- 행 액션: 잠금·잠금해제·퇴직·역할 할당/해제 (PATCH 호출).
- 권한: `useAuthStore.hasPermission('USER_ADMIN')` 검사로 등록·편집·액션 버튼 표시 분기.

상세 폼 필드: username, password (등록 시만), name, email, phone, departmentId (선택), roleCodes (다중).

### 3. `DeptPage.vue` — `/system/depts`

부서 트리 표시. `GET /api/departments/tree` 호출.

- 좌측 트리 (재귀 컴포넌트 — Sidebar 의 MenuTreeNode 와 비슷한 패턴) → 노드 클릭 시 우측 상세.
- 우측: 코드·이름·부모·관리자 폼.
- 등록·편집·이동·비활성화 액션.
- 권한: `DEPT_ADMIN`.

### 4. `RolePage.vue` — `/system/roles`

- 좌측: 역할 목록 DataTable (code·name·description).
- 우측: 선택된 역할의 권한 목록 + grant/revoke 인터랙션.
- 권한 목록은 `GET /api/permissions` 로 전체 받아 체크박스로 표시.
- 권한: `ROLE_ADMIN`.

### 5. `MenuPage.vue` — `/system/menus`

- 메뉴 트리 (재귀) — Sidebar 와 같은 구조지만 권한 필터 없음 (관리자 전용).
- 우측: 선택된 메뉴의 label·icon·route·groupId·permissionCode·sort 폼.
- 등록·이동(parent/sort 변경)·비활성화.
- 권한: `MENU_ADMIN`.

> 4 페이지 모두 비슷한 패턴이지만 각각의 UI 가 도메인에 맞춰져야 함. 본 step 은 **각 페이지의 MVP** 만 구현 (목록 + 등록·편집 다이얼로그). 깊은 편집 UX (드래그 정렬·트리 이동 등) 는 후속 phase.

### 6. 공통 helper

각 페이지의 페이지·검색·CRUD 패턴은 비슷하므로 단순 helper:

```ts
// frontend/src/lib/admin-crud.ts
export interface PageQuery {
  page: number; size: number; kw?: string;
}
export function buildAdminQueryUrl(base: string, q: PageQuery, extra: Record<string,string>): string { ... }
```

(또는 `usePageData` 그대로 재사용)

### 7. 권한 가드 컴포넌트 — `RequirePermission.vue` (선택)

```vue
<script setup lang="ts">
import { useAuthStore } from '@/stores/useAuthStore';
interface Props { code: string | string[]; }
const props = defineProps<Props>();
const auth = useAuthStore();
const codes = Array.isArray(props.code) ? props.code : [props.code];
const allowed = codes.some(c => auth.hasPermission(c) || auth.hasRole(c));
</script>
<template>
  <slot v-if="allowed" />
  <slot v-else name="fallback" />
</template>
```

각 시스템 페이지의 등록·편집 버튼을 이 컴포넌트로 감싸기.

### 8. 단위 테스트

각 페이지의 비즈니스 로직(권한 체크·검색 파라미터 빌드) 만 가벼운 unit. 컴포넌트 전체 mount 테스트는 e2e(step 3) 가 다룬다.

`src/lib/__tests__/admin-crud.spec.ts`:
- `buildAdminQueryUrl_기본_+_filters_파라미터`.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 페이지 파일 (stub 에서 실 구현으로 갱신)
grep -q "useApiFetch\|DataTable" src/pages/system/UserPage.vue
grep -q "useApiFetch" src/pages/system/DeptPage.vue
grep -q "useApiFetch" src/pages/system/RolePage.vue
grep -q "useApiFetch" src/pages/system/MenuPage.vue

# 2) RequirePermission (또는 동등 컴포넌트)
test -f src/components/common/RequirePermission.vue || \
  grep -q "useAuthStore().hasPermission\|hasPermission" src/pages/system/UserPage.vue

# 3) 정적·테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 4) dev 부팅 + SPA 라우트 200
pnpm dev &
sleep 6
for p in /system/users /system/depts /system/roles /system/menus; do
  test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173$p)" = "200"
done
kill %1
```

수동 검증 (admin 로그인 후):
- `/system/users` → 시드 사용자 3명 표시.
- 등록 다이얼로그 → 신규 사용자 생성 → 성공 토스트 + 목록 reload.
- 부서/역할/메뉴 페이지도 각각 동작.

## 검증 절차

1. AC + 수동 검증 통과.
2. 아키텍처 체크:
   - 각 페이지에 `@PreAuthorize` 와 매칭되는 클라이언트 측 권한 가드 적용?
   - 비밀번호 변경·역할 할당 같은 액션이 권한 가드 후에만 노출?
   - 목록 페이지가 useApiFetch + DataTable 패턴 일관?
   - 메뉴/부서 트리 컴포넌트가 재귀 패턴?
3. step 2 업데이트:
   - 성공 → `"summary": "시스템 관리 4 페이지 정식 구현 — UserPage(목록·검색·등록·잠금/퇴직/역할), DeptPage(트리 + 등록·이동·비활성), RolePage(역할 권한 grant/revoke), MenuPage(메뉴 트리 + 등록·이동·비활성) + RequirePermission 권한 가드 + admin-crud helper. 시드 사용자·메뉴 실 CRUD 동작 확인."`

## 금지사항

- 비밀번호 평문 표시·전송 금지. 등록·변경 시만 입력, 응답·목록에 절대 없음.
- USER_ADMIN 권한 없는 사용자에게 등록·편집 버튼 노출 금지.
- 메뉴 페이지에서 자기·자손 부모로 이동 시 백엔드가 거부하므로 frontend 도 가드 (또는 백엔드 거부 후 토스트).
- 시스템 페이지를 DynamicPage 로 만들지 마라 — 재귀적 + 권한 가드 명확성 떨어짐.
- 페이지마다 분리된 useApiFetch 사용 — store 도입은 별도 ADR.
- 백엔드 코드 수정 금지.
- 운영 코드 console.log 금지.
