# Step 2: ag-grid-adapter

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/docs/UI_GUIDE.md` — §5-4 AG Grid 섹션 (테마: 직접 만든 `ag-theme-itg`, 기본 `alpine` 직접 사용 금지), §3 색상 토큰
- `/docs/ADR.md` — ADR-007 (AG Grid Community 만, Enterprise 금지)
- `/phases/2-dynamic-render/step0.md`·`step1.md` — meta-body 타입, shadcn DataTable
- `/frontend/src/assets/styles/tokens.css` — UI_GUIDE 토큰 정의

## 작업

이 step 의 목적은 **AG Grid Vue3 Community 를 frontend 에 통합하고, UI_GUIDE 토큰을 AG Grid CSS 변수에 매핑한 `ag-theme-itg` 테마를 정착시키며, 1000행 이상 대용량 시나리오의 데모 페이지를 만드는 것**이다. `DynamicGrid` 의 자동 분기는 step 3.

### 1. 의존성

```bash
cd frontend
pnpm add ag-grid-community ag-grid-vue3
```

> Enterprise 패키지(`ag-grid-enterprise`) 는 절대 설치하지 않는다.

### 2. 모듈 등록

AG Grid 32.x 는 모듈식. `src/lib/ag-grid-modules.ts` 에서 Community 모듈만 등록:

```ts
import {
  ModuleRegistry, ClientSideRowModelModule, RowApiModule,
  ValidationModule,
} from 'ag-grid-community';

ModuleRegistry.registerModules([
  ClientSideRowModelModule,
  RowApiModule,
  ValidationModule,
]);
```

`src/main.ts` 상단에서 한 번 import 해 사이드이펙트로 등록:

```ts
import '@/lib/ag-grid-modules';
```

> 모듈 등록 누락은 AG Grid 의 가장 흔한 함정. 명시적 한 파일에 모아둔다.

### 3. 테마 — `src/assets/styles/ag-theme-itg.css`

AG Grid 32.x 의 새로운 CSS 변수 기반 테마 시스템을 활용한다. `--ag-*` 변수에 UI_GUIDE 토큰을 매핑:

```css
@import "ag-grid-community/styles/ag-grid.css";

.ag-theme-itg {
  /* 폰트 */
  --ag-font-family:  var(--font-sans);
  --ag-font-size:    13px;

  /* 색상 (UI_GUIDE §3) */
  --ag-background-color:           var(--color-surface);
  --ag-foreground-color:           var(--color-foreground);
  --ag-header-background-color:    var(--color-surface-muted);
  --ag-header-foreground-color:    var(--color-foreground);
  --ag-border-color:               var(--color-border);
  --ag-row-border-color:           var(--color-border-subtle);
  --ag-row-hover-color:            var(--color-surface-hover);
  --ag-selected-row-background-color: var(--color-surface-selected);

  /* 사이즈 — UI_GUIDE §5-4 의 default 40px 행 */
  --ag-grid-size:        4px;
  --ag-row-height:       40px;
  --ag-header-height:    40px;
  --ag-cell-horizontal-padding: 12px;

  /* 포커스 (UI_GUIDE §11 — :focus-visible 2px) */
  --ag-input-focus-border-color:        var(--color-ring);
  --ag-range-selection-border-color:    var(--color-ring);

  /* zebra-stripe 비활성 (UI_GUIDE 금지) */
  --ag-odd-row-background-color: var(--color-surface);

  font-family: var(--ag-font-family);
}

/* 헤더 폰트 가중치 (UI_GUIDE §4-2 그리드 헤더 13px / 600) */
.ag-theme-itg .ag-header-cell-label {
  font-weight: 600;
}

/* 다크 모드 — 토큰이 .dark 셀렉터로 자동 반전되므로 별도 처리 불필요. 다만 확인용 */
.dark .ag-theme-itg {
  /* tokens.css 의 .dark 토큰을 위 변수가 자동 따라옴 */
}
```

`src/main.ts` 에서 import:

```ts
import '@/assets/styles/ag-theme-itg.css';
```

> AG Grid 32.x 가 기본 제공하는 `alpine`·`balham`·`material` 테마 CSS 는 **import 하지 않는다** (UI_GUIDE §12 Don't — `alpine` 기본 테마 직접 사용 금지).

### 4. 검증 페이지 — `src/views/_dev/AgGridSampler.vue`

라우트 `/_dev/ag-grid` 추가. 1500 행 mock 데이터로 가상 스크롤 동작 확인.

```vue
<script setup lang="ts">
import { AgGridVue } from 'ag-grid-vue3';
import type { ColDef } from 'ag-grid-community';

interface Row { id: string; title: string; status: string; assignee: string; createdAt: string; }

const rows: Row[] = Array.from({ length: 1500 }, (_, i) => ({
  id:        `itg-sample-${String(i + 1).padStart(4, '0')}`,
  title:     `샘플 항목 ${i + 1}`,
  status:    ['DRAFT','PUBLISHED','DEPRECATED','ARCHIVED'][i % 4]!,
  assignee:  `샘플 사용자 ${i % 7}`,
  createdAt: `2026-05-${String((i % 28) + 1).padStart(2, '0')}`,
}));

const columnDefs: ColDef[] = [
  { field: 'id',        headerName: 'ID',     width: 200, pinned: 'left' },
  { field: 'title',     headerName: '제목',   flex: 1 },
  { field: 'status',    headerName: '상태',   width: 120 },
  { field: 'assignee',  headerName: '담당자', width: 140 },
  { field: 'createdAt', headerName: '등록일', width: 120 },
];

const defaultColDef: ColDef = {
  sortable: true,
  resizable: true,
};
</script>

<template>
  <main class="mx-auto max-w-6xl p-6 space-y-4">
    <h1>AG Grid Sampler (1500 rows)</h1>
    <div class="ag-theme-itg" style="height: 600px; width: 100%;">
      <AgGridVue
        :rowData="rows"
        :columnDefs="columnDefs"
        :defaultColDef="defaultColDef"
        :rowSelection="'single'"
        style="height: 100%; width: 100%;"
      />
    </div>
  </main>
</template>
```

라우트:

```ts
{ path: '/_dev/ag-grid', component: () => import('@/views/_dev/AgGridSampler.vue') },
```

### 5. 토큰 일치 검증 (수동)

- 브라우저에서 `/_dev/ag-grid` 진입.
- 헤더 배경이 `--color-surface-muted` (라이트 회색).
- 행 호버 시 `--color-surface-hover`.
- 행 선택 시 `--color-surface-selected` (옅은 파랑).
- 본문 텍스트가 Pretendard / 13px.
- 정렬 아이콘 클릭 시 정상 정렬.
- 1500 행이 가상 스크롤로 60fps 부근 부드럽게 스크롤.
- zebra-stripe 가 적용되지 않은 단일 배경.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 의존성 — Community 만
grep -q '"ag-grid-community"' package.json
grep -q '"ag-grid-vue3"' package.json
! grep -q '"ag-grid-enterprise"' package.json     # Enterprise 절대 금지

# 2) 파일 존재
test -f src/lib/ag-grid-modules.ts
test -f src/assets/styles/ag-theme-itg.css
test -f src/views/_dev/AgGridSampler.vue
grep -q '/_dev/ag-grid' src/router/index.ts

# 3) main.ts 가 모듈 + 테마 import
grep -q "ag-grid-modules" src/main.ts
grep -q "ag-theme-itg" src/main.ts

# 4) UI_GUIDE 금지 — alpine 등 기본 테마 import 없음
! grep -RIn "ag-theme-alpine" src/ frontend
! grep -RIn "ag-theme-balham" src/ frontend

# 5) 정적 검증
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 6) dev 부팅 + 라우트 200
pnpm dev &
sleep 6
test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173/_dev/ag-grid)" = "200"
kill %1
```

## 검증 절차

1. 위 AC 전부 통과.
2. 아키텍처 체크리스트:
   - `ag-grid-enterprise` 가 의존성에 없는가? (ADR-007)
   - `ag-theme-alpine` / `balham` / `material` 등 기본 테마 CSS 가 import 되지 않는가? (UI_GUIDE Don't)
   - `--ag-*` 변수가 UI_GUIDE 의 시맨틱 토큰(`--color-*`) 을 가리키도록 매핑되었는가?
   - `--ag-odd-row-background-color` 가 `--ag-background-color` 와 같은 값 (zebra 없음) 인가?
   - 헤더 폰트 가중치 600 (UI_GUIDE §4-2 의 그리드 헤더 규칙) 인가?
3. step 2 status 업데이트:
   - 성공 → `"summary": "ag-grid-community + ag-grid-vue3 Community 도입(Enterprise 차단). lib/ag-grid-modules.ts 에 모듈 등록(ClientSideRowModelModule 등). ag-theme-itg.css 로 --ag-* 변수를 UI_GUIDE 토큰(--color-*)에 매핑(헤더/호버/선택/zebra-off/폰트). AgGridSampler.vue 1500행 mock 가상 스크롤 검증."`

## 금지사항

- `ag-grid-enterprise` 패키지를 설치하지 마라. 이유: ADR-007 라이선스. Community 기능만.
- `ag-theme-alpine` / `balham` / `material` CSS 를 import 하지 마라. 이유: UI_GUIDE Don't.
- AG Grid 내부 셀에 인라인 `style="color: ..."` 색을 박지 마라. 이유: 토큰 일관성. cellClass / cellStyle 도 가급적 회피, 필요 시 토큰 변수 참조.
- `RangeSelectionModule`·`MasterDetailModule`·`ServerSideRowModelModule` 등 Enterprise 모듈을 import 하지 마라. 이유: 런타임 라이선스 경고.
- 백엔드 코드 수정 금지.
- DataTable·DynamicGrid 코드를 이 step 에서 수정하지 마라. 이유: 본 step 은 AG Grid 단독 도입.
- 1500행 mock 데이터에 실 운영 이름·이메일을 박지 마라. 가상 샘플만.
