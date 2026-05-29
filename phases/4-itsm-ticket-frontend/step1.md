# Step 1: status-priority-badges

## 읽어야 할 파일

- `/docs/UI_GUIDE.md` — §5-5 상태 뱃지 (pill radius 9999, padding 2px 10px, 12px/600, 시맨틱 토큰 10% alpha 배경 + 본색 텍스트), `metaStatus` 매핑 4종, 우선순위 4단계 매핑(CRITICAL 만 danger)
- `/docs/ARCHITECTURE.md` — §5 필드 타입 매핑 (`status` → `StatusBadge`, `priority` → `PriorityBadge`)
- `/phases/2-dynamic-render/step0.md`·`step1.md`·`step3.md` — FieldType 정의, DataTable 셀 렌더, useGridColumns
- `/phases/4-itsm-ticket-frontend/step0.md` — usePageData
- `/frontend/src/types/meta-body.ts` — `FieldType` 12종 정의
- `/frontend/src/composables/useGridColumns.ts` — `toDataTableColumns`·`toAgGridColDefs` 현재 매핑
- `/frontend/src/components/ui/data-table/DataTable.vue` — TanStack `FlexRender` 사용

## 작업

이 step 의 목적은 **`StatusBadge`/`PriorityBadge` 컴포넌트를 만들고, DataTable / AG Grid 셀이 메타의 `field.type === 'status' | 'priority'` 일 때 자동으로 뱃지로 렌더링되도록 매핑을 확장하는 것**이다.

### 1. 공통 뱃지 컴포넌트

#### `src/components/common/StatusBadge.vue`

요구사항 (UI_GUIDE §5-5):
- pill (`rounded-full`), padding `px-2.5 py-0.5`, font `text-[12px] font-semibold`.
- 매핑:
  - `DRAFT`        → `--color-warning` (`작성 중`)
  - `PUBLISHED`    → `--color-success` (`배포 중`)
  - `DEPRECATED`   → `--color-neutral` (`구버전`)
  - `ARCHIVED`     → `--color-foreground-subtle` (`보관`)
  - ITSM 티켓 상태:
    - `OPEN`        → `--color-info`    (`접수`)
    - `IN_PROGRESS` → `--color-warning` (`진행 중`)
    - `RESOLVED`    → `--color-success` (`해결됨`)
    - `CLOSED`      → `--color-neutral` (`종료`)
- 라벨 텍스트는 한글 (위 매핑). 정의되지 않은 값은 `value` 그대로 + 중립색.

```vue
<script setup lang="ts">
import { computed } from 'vue';

interface Props { value: string | null | undefined; }
const props = defineProps<Props>();

interface BadgeSpec { label: string; color: string; bg: string; }

const STATUS_MAP: Record<string, BadgeSpec> = {
  DRAFT:        { label: '작성 중', color: 'text-warning',        bg: 'bg-warning/10' },
  PUBLISHED:    { label: '배포 중', color: 'text-success',        bg: 'bg-success/10' },
  DEPRECATED:   { label: '구버전', color: 'text-neutral',        bg: 'bg-neutral/10' },
  ARCHIVED:     { label: '보관',   color: 'text-foreground-subtle', bg: 'bg-surface-muted' },
  OPEN:         { label: '접수',   color: 'text-info',           bg: 'bg-info/10' },
  IN_PROGRESS:  { label: '진행 중', color: 'text-warning',        bg: 'bg-warning/10' },
  RESOLVED:     { label: '해결됨', color: 'text-success',        bg: 'bg-success/10' },
  CLOSED:       { label: '종료',   color: 'text-neutral',        bg: 'bg-neutral/10' },
};

const spec = computed<BadgeSpec>(() => {
  const v = props.value ?? '';
  return STATUS_MAP[v] ?? { label: v || '-', color: 'text-foreground-muted', bg: 'bg-surface-muted' };
});
</script>

<template>
  <span :class="['inline-flex items-center rounded-full px-2.5 py-0.5 text-[12px] font-semibold',
                 spec.bg, spec.color]">
    {{ spec.label }}
  </span>
</template>
```

> Tailwind 의 `bg-warning/10` 은 v4 + 우리 토큰(`--color-warning`)으로 10% alpha 배경을 만든다. 토큰 변수가 hex 면 alpha 적용이 가능 — `tokens.css` 의 변수가 hex 인지 확인 후, 필요하면 `rgb` 형태로 전환 (Tailwind v4 의 `/<alpha>` 문법은 hex 도 지원).

#### `src/components/common/PriorityBadge.vue`

UI_GUIDE §5-5: "티켓 우선순위(`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`) 도 동일한 pill 규격, 무채색 4단계 + `CRITICAL` 만 `--color-danger`."

매핑:
- `LOW`      → `text-foreground-subtle` + `bg-surface-muted`        + 라벨 `낮음`
- `MEDIUM`   → `text-foreground-muted`  + `bg-surface-muted`        + 라벨 `보통`
- `HIGH`     → `text-foreground`        + `bg-surface-hover`        + 라벨 `높음`
- `CRITICAL` → `text-danger`            + `bg-danger/10`            + 라벨 `긴급`

구조는 StatusBadge 와 동일.

### 2. 그리드 셀 렌더링 매핑 — `useGridColumns` 확장

현재 `toDataTableColumns`·`toAgGridColDefs` 는 단순 필드 매핑. type 정보를 셀 렌더에 활용하도록 확장.

#### DataTable (TanStack) — `cell` 옵션

```ts
import { h } from 'vue';
import StatusBadge   from '@/components/common/StatusBadge.vue';
import PriorityBadge from '@/components/common/PriorityBadge.vue';

export function toDataTableColumns<TData>(meta: GridMeta): ColumnDef<TData, unknown>[] {
  return meta.columns.map((c) => ({
    accessorKey: c.field,
    header:      c.label,
    size:        c.width,
    cell: ({ getValue }) => {
      const v = getValue() as unknown;
      if (c.type === 'status')   return h(StatusBadge,   { value: v as string });
      if (c.type === 'priority') return h(PriorityBadge, { value: v as string });
      // date 도 간단 포맷팅 가능 (Intl.DateTimeFormat) — 본 step 은 status/priority 만
      return v == null ? '' : String(v);
    },
  }));
}
```

#### AG Grid — `cellRenderer`

AG Grid 32.x 는 Vue 컴포넌트를 `cellRenderer` 로 받을 수 있다. 단, frameworkComponents 등록 또는 `cellRenderer: StatusBadge` 직접 전달. 보다 단순한 방법은 `cellRendererFramework` 의 v32 변형 또는 `cellRenderer: 'agStatusBadgeRenderer'` 등록.

가장 단순한 방식: AG Grid 의 `cellRenderer` 에 Vue 컴포넌트를 직접 등록. AG Grid Vue3 는 자동 인식.

```ts
export function toAgGridColDefs(meta: GridMeta): ColDef[] {
  return meta.columns.map((c) => {
    const col: ColDef = { field: c.field, headerName: c.label };
    if (c.width  !== undefined) col.width  = c.width;
    if (c.flex   !== undefined) col.flex   = c.flex;
    if (c.pinned !== undefined) col.pinned = c.pinned;
    if (c.type === 'status')   col.cellRenderer = StatusBadge;
    if (c.type === 'priority') col.cellRenderer = PriorityBadge;
    return col;
  });
}
```

> AG Grid v32 의 cellRenderer 가 Vue SFC 를 받는지 검증 필요. 만약 string 이름 + 별도 등록을 요구하면 `gridOptions.components` 에 등록.

### 3. 검증 페이지 갱신 (선택)

`_dev/DataTableSampler.vue` 또는 새 `BadgeGallery.vue` 로 두 뱃지를 한 화면에서 시각 확인:
- StatusBadge: `DRAFT`·`PUBLISHED`·`DEPRECATED`·`ARCHIVED`·`OPEN`·`IN_PROGRESS`·`RESOLVED`·`CLOSED`·`UNKNOWN`(fallback).
- PriorityBadge: `LOW`·`MEDIUM`·`HIGH`·`CRITICAL`.

라우트 `/_dev/badges` 한 개 추가.

### 4. 단위 테스트 — `src/components/common/__tests__/Badge.spec.ts`

Vitest + happy-dom + `@vue/test-utils`. 케이스:
1. `StatusBadge_DRAFT_은_작성_중_라벨` — mount 후 텍스트 확인.
2. `StatusBadge_undefined_은_-_라벨_fallback`.
3. `StatusBadge_CLOSED_은_종료_라벨`.
4. `PriorityBadge_CRITICAL_은_긴급_라벨_과_danger_클래스`.
5. `PriorityBadge_LOW_은_낮음_라벨`.

> happy-dom 도입은 phase 2 에서 unit test 만 했으므로 환경 재확인. 미설치면 `pnpm add -D happy-dom @vue/test-utils` + vitest config 의 `environment: 'happy-dom'` 으로 변경 (단 일부 테스트만). 또는 기존 `'node'` 환경에서 컴포넌트 렌더링 테스트를 회피하고 매핑 함수만 분리해서 테스트.

가장 단순한 방향: **매핑 함수를 별도로 export 하고 그것만 테스트**.

```ts
// StatusBadge.vue 의 STATUS_MAP 을 export 또는 src/lib/badges.ts 로 이동
export function statusSpec(value: string | null | undefined): BadgeSpec { ... }
export function prioritySpec(value: string | null | undefined): BadgeSpec { ... }
```

- 테스트는 spec 함수 6~8 케이스로 갈음.

### 5. 그리드 인터랙션 회귀 확인

기존 `/_dev/dynamic-grid` 의 mock 데이터 (status / DRAFT/PUBLISHED/...) 가 뱃지로 표시되는지 시각 확인.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 파일 존재
test -f src/components/common/StatusBadge.vue
test -f src/components/common/PriorityBadge.vue
test -f src/lib/badges.ts                       # spec 함수 위치
test -f src/components/common/__tests__/Badge.spec.ts

# 2) useGridColumns 가 type=status/priority 셀 렌더 추가
grep -q "StatusBadge\|statusSpec" src/composables/useGridColumns.ts
grep -q "PriorityBadge\|prioritySpec" src/composables/useGridColumns.ts

# 3) 정적 + 테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 4) 기존 dev 라우트 회귀
pnpm dev &
sleep 6
for p in /_dev/dynamic-grid /_dev/dynamic-page /_dev/data-table /system/meta /itsm; do
  test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173$p)" = "200"
done
kill %1
```

수동 검증 (backend + 시드 5건 살아있는 상태):
- `/itsm` 진입 → status 컬럼이 `접수`·`진행 중`·`해결됨`·`종료` 뱃지로 표시.
- priority 컬럼이 `낮음`·`보통`·`높음`·`긴급` 뱃지, `긴급` 만 빨강.
- 뱃지 외 영역에 그라데이션·blur·gradient orb 없음.

## 검증 절차

1. AC 통과.
2. 아키텍처 체크리스트:
   - StatusBadge·PriorityBadge 가 pill radius 9999, 12px/600, padding 2px 10px 인가? (UI_GUIDE §5-5)
   - 시맨틱 색을 상태/우선순위에만 사용하는가? (UI_GUIDE §3-5)
   - `LOW`/`MEDIUM`/`HIGH` 는 무채색, `CRITICAL` 만 danger 인가?
   - useGridColumns 가 type 에 따라 cell renderer 를 자동 매핑하는가? (메타-주도 렌더링)
   - 정의되지 않은 status 값은 fallback (text-foreground-muted + 원본 value) 로 표시되는가?
3. step 1 업데이트:
   - 성공 → `"summary": "StatusBadge·PriorityBadge (UI_GUIDE §5-5 pill 12px/600, 시맨틱 토큰 10% alpha) + lib/badges.ts spec 함수 분리(8 status + 4 priority 매핑). useGridColumns 가 type=status/priority 셀에 자동 매핑(DataTable cell h() + AG Grid cellRenderer). 단위 테스트 5~8 케이스 + dev 갤러리 /_dev/badges."`

## 금지사항

- 시맨틱 색을 일반 UI(버튼·헤더·테이블 행) 에 추가 금지. UI_GUIDE §3-5 — 상태 표현 전용.
- `LOW`/`MEDIUM`/`HIGH` 에 컬러 적용 금지 (무채색 4단계, `CRITICAL` 만 예외).
- 뱃지 안에 아이콘 추가 금지 (UI_GUIDE 의 pill 규격은 라벨만).
- AG Grid Enterprise cellRenderer 사용 금지 (Community 만).
- 사용자 모듈 API 호출 금지.
- 백엔드 코드 수정 금지.
- date/date-range/file 의 cell 렌더링은 본 step 에서 다루지 마라. status/priority 만.
- 운영 코드에 `console.log` 잔류 금지.
- 가상 샘플 (status `CUSTOM` 등) 테스트 시 실 운영 코드값 추가 금지.
