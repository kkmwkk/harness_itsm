# Step 0: meta-body-types

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "절대 규칙" (`any` 금지)
- `/docs/ARCHITECTURE.md` — §3-4 PageMeta JSON 본문 예시, §5 필드 타입 매핑 (백엔드 ↔ FieldType ↔ shadcn/vue 컴포넌트)
- `/docs/ADR.md` — ADR-004 (No-code: 모든 화면은 메타 한 건에서 동적 생성)
- `/phases/1-frontend-foundation/index.json` — step 4 의 `types/meta.ts` 가 `metaJson: Record<string, unknown>` 으로 두었다는 사실 확인
- `/frontend/src/types/meta.ts` — 현재 메타 골격 타입

이번 phase 의 작업 루트는 `/Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend/`. 백엔드 코드는 손대지 않는다.

## 작업

이 step 의 목적은 **메타 본문(`metaJson`) 의 구조화된 타입을 정의하고, 런타임 type guard 로 좁히는 함수와 단위 테스트를 마련하는 것**이다. 이후 step 의 모든 dynamic 컴포넌트는 이 타입 위에서 동작한다.

### 1. 새 파일 — `src/types/meta-body.ts`

ARCHITECTURE §5 매핑표 + §3-4 예시를 기반으로:

```ts
/** 필드 타입 — ARCHITECTURE §5 와 1:1 일치 */
export type FieldType =
  | 'text' | 'textarea' | 'number'
  | 'select' | 'radio' | 'checkbox'
  | 'date' | 'date-range'
  | 'user-picker' | 'file'
  | 'status' | 'priority';

/** 폼 필드 한 칸 정의 */
export interface FieldMeta {
  name:        string;            // form 모델의 key
  label:       string;
  type:        FieldType;
  required?:   boolean;
  /** 폼 그리드의 column span (예: 2-column 폼에서 span:2 면 전체 폭) */
  span?:       1 | 2;
  /** placeholder · helpText 등 부가 정보 */
  placeholder?: string;
  helpText?:    string;
  /** select/radio 의 정적 옵션 또는 동적 옵션 API */
  options?:    Array<{ value: string; label: string }>;
  optionsApi?: string;            // 예: '/api/codes/category'
  /** number/text 의 검증 */
  maxLength?:  number;
  min?:        number;
  max?:        number;
  pattern?:    string;            // RegExp 문자열
}

/** 폼 전체 정의 */
export interface FormMeta {
  layout: 'single-column' | 'two-column';
  fields: FieldMeta[];
}

/** 그리드 컬럼 한 개 */
export interface GridColumnMeta {
  field:   string;                // 행 데이터의 key
  label:   string;
  type:    FieldType;             // 셀 렌더링도 동일 매핑 사용
  width?:  number;                // px
  flex?:   number;                // flex 사용 시 width 무시
  pinned?: 'left' | 'right';
  /** md 이하에서 자동 숨김 (UI_GUIDE §6-3) */
  hideAt?: 'sm' | 'md';
}

/** 그리드 전체 정의 */
export interface GridMeta {
  columns:    GridColumnMeta[];
  /** 인라인 편집 활성화 (true 면 AG Grid 강제) */
  inlineEdit?: boolean;
  /** 엑셀 export 활성화 (true 면 AG Grid 강제) */
  export?:     boolean;
}

/** 액션 버튼 (PageHeader 우측, 그리드 툴바 등) */
export interface ActionMeta {
  id:    string;
  label: string;
  type:  'dialog-form' | 'export' | 'navigate' | 'custom';
  /** type='navigate' 일 때 라우트, type='dialog-form' 일 때 form 메타 참조 */
  to?:   string;
}

/** PageMeta.metaJson 의 강타입 형태 */
export interface PageMetaBody {
  /** 데이터 API 베이스 (예: '/api/tickets') */
  api:      string;
  grid:     GridMeta;
  form:     FormMeta;
  /** 상세 페이지 메타 (옵션) */
  detail?:  { fields: FieldMeta[] };
  actions?: ActionMeta[];
}
```

### 2. type guard — `src/lib/meta-body.ts`

런타임 검증 (외부에서 들어온 `Record<string, unknown>` 을 강타입으로 좁힐 때 사용):

```ts
import type {
  FieldType, FieldMeta, FormMeta, GridColumnMeta, GridMeta,
  ActionMeta, PageMetaBody,
} from '@/types/meta-body';

const FIELD_TYPES: readonly FieldType[] = [
  'text','textarea','number','select','radio','checkbox',
  'date','date-range','user-picker','file','status','priority',
];

function isObject(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function isFieldType(v: unknown): v is FieldType {
  return typeof v === 'string' && (FIELD_TYPES as readonly string[]).includes(v);
}

export function isFieldMeta(v: unknown): v is FieldMeta {
  if (!isObject(v)) return false;
  if (typeof v.name !== 'string')   return false;
  if (typeof v.label !== 'string')  return false;
  if (!isFieldType(v.type))         return false;
  return true;
}

export function isGridColumnMeta(v: unknown): v is GridColumnMeta {
  if (!isObject(v)) return false;
  if (typeof v.field !== 'string') return false;
  if (typeof v.label !== 'string') return false;
  if (!isFieldType(v.type))        return false;
  return true;
}

export function isFormMeta(v: unknown): v is FormMeta {
  if (!isObject(v)) return false;
  const layout = v.layout;
  if (layout !== 'single-column' && layout !== 'two-column') return false;
  if (!Array.isArray(v.fields)) return false;
  return v.fields.every(isFieldMeta);
}

export function isGridMeta(v: unknown): v is GridMeta {
  if (!isObject(v)) return false;
  if (!Array.isArray(v.columns)) return false;
  return v.columns.every(isGridColumnMeta);
}

export function isActionMeta(v: unknown): v is ActionMeta {
  if (!isObject(v)) return false;
  if (typeof v.id !== 'string')    return false;
  if (typeof v.label !== 'string') return false;
  const types = ['dialog-form','export','navigate','custom'] as const;
  return typeof v.type === 'string' && (types as readonly string[]).includes(v.type);
}

export function isPageMetaBody(v: unknown): v is PageMetaBody {
  if (!isObject(v)) return false;
  if (typeof v.api !== 'string') return false;
  if (!isGridMeta(v.grid)) return false;
  if (!isFormMeta(v.form)) return false;
  if (v.actions !== undefined) {
    if (!Array.isArray(v.actions)) return false;
    if (!v.actions.every(isActionMeta)) return false;
  }
  return true;
}

/** 좁히기 실패 시 명시 에러 */
export class MetaBodyShapeError extends Error {
  constructor(public readonly metaId: string, message: string) {
    super(`[${metaId}] ${message}`);
    this.name = 'MetaBodyShapeError';
  }
}

/** 좁히기 + 실패 시 에러 */
export function asPageMetaBody(metaId: string, v: unknown): PageMetaBody {
  if (!isPageMetaBody(v)) {
    throw new MetaBodyShapeError(metaId, 'metaJson 형태가 PageMetaBody 와 일치하지 않습니다.');
  }
  return v;
}
```

### 3. `usePageMeta` 갱신 (선택, 최소)

step 4 (phase 1) 의 `composables/usePageMeta.ts` 는 그대로 두고, **이번 step 에서는 `Record<string, unknown>` 그대로 둔다**. 다음 step 들에서 컴포넌트 진입 시 `asPageMetaBody(meta.id, meta.metaJson)` 으로 좁힌다 — 책임 분리.

> 만약 즉시 좁히도록 바꾸고 싶다면 `usePageMeta` 가 좁히기를 시도하되 실패해도 `meta` 자체는 반환하고 `bodyError: Ref<MetaBodyShapeError | null>` 만 별도 노출. 단, **본 step 은 타입 도입만**.

### 4. 단위 테스트

테스트 프레임워크: **Vitest** (Vue + Vite 생태 표준). 이번 phase 가 frontend 의 첫 단위 테스트이므로 셋업도 함께:

```bash
cd frontend
pnpm add -D vitest @vitest/coverage-v8
```

`frontend/vitest.config.ts`:

```ts
import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import path from 'node:path';

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': path.resolve(__dirname, './src') } },
  test: {
    environment: 'node',          // 본 step 은 DOM 불필요
    globals: false,
    coverage: { provider: 'v8' },
  },
});
```

`frontend/package.json` 의 scripts 에 추가:

```json
{
  "test":     "vitest run",
  "test:watch":"vitest"
}
```

`frontend/src/lib/__tests__/meta-body.spec.ts` — 다음 케이스 (한글 it 허용):

1. `isFieldType_지원되는_12종_true` — 모든 FieldType 값에 대해 true.
2. `isFieldType_지원되지_않는_문자열_false` — `'email'`, `''`, `null` 등 false.
3. `isFieldMeta_name_label_type_있어야_true`.
4. `isFieldMeta_type_없거나_unknown_false`.
5. `isFormMeta_layout_과_fields_배열_검증` — layout single/two, fields 모두 FieldMeta.
6. `isFormMeta_layout_잘못_false`.
7. `isGridMeta_columns_배열_검증`.
8. `isPageMetaBody_정상_샘플_true` — ARCHITECTURE §3-4 의 itg-ticket-v1-2 예시 JSON 사용.
9. `isPageMetaBody_actions_누락_허용`.
10. `isPageMetaBody_actions_있으면_각_요소_ActionMeta_검증`.
11. `asPageMetaBody_실패시_MetaBodyShapeError_metaId_포함`.

> mock 메타는 ARCHITECTURE §3-4 의 `itg-ticket-v1-2` 형태를 그대로 사용. 실 운영 데이터 없음 — 가상 샘플만.

### 5. ESLint

새 파일들이 ESLint flat config 의 적용 범위 안에 들어가는지 확인. `src/lib/__tests__/*` 도 type-check 대상이므로 `tsconfig.app.json` 의 `exclude` 에서 `__tests__` 가 빠지지 않게 (또는 별도 `tsconfig.test.json` 도입). 기존 phase 1 의 step 0 에서 `"exclude": ["src/**/__tests__/*"]` 가 있었다면 **이번 step 에서 해제** 또는 별도 tsconfig 분리.

가장 단순한 처리:
- `tsconfig.app.json` 의 `exclude` 에서 `src/**/__tests__/*` 제거.
- 또는 별도 `tsconfig.test.json` 두고 vitest 가 그것을 사용하도록.

본 step 은 첫 번째 단순 처리(`exclude` 제거)를 권장.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 새 파일 존재
test -f src/types/meta-body.ts
test -f src/lib/meta-body.ts
test -f src/lib/__tests__/meta-body.spec.ts
test -f vitest.config.ts
grep -q '"vitest"' package.json

# 2) 단위 테스트
pnpm test

# 3) 정적 검증
pnpm type-check
pnpm lint
pnpm build
```

## 검증 절차

1. 위 AC 커맨드 전부 통과.
2. 아키텍처 체크리스트:
   - `FieldType` 의 12종 값이 ARCHITECTURE §5 와 정확히 일치하는가?
   - type guard 가 `Record<string, unknown>` 을 안전하게 좁히는가? (`any` 캐스트 없음)
   - `isPageMetaBody` 가 ARCHITECTURE §3-4 의 `itg-ticket-v1-2` 예시를 true 로 판정하는가?
   - Vitest 가 ESM 환경에서 정상 동작하고, type-check 가 테스트 파일까지 포함하는가?
   - `MetaBodyShapeError` 가 `metaId` 와 함께 메시지를 던지는가?
3. 결과에 따라 `phases/2-dynamic-render/index.json` 의 step 0 을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "types/meta-body.ts (FieldType 12종 + FieldMeta/FormMeta/GridColumnMeta/GridMeta/ActionMeta/PageMetaBody) + lib/meta-body.ts (type guard + asPageMetaBody + MetaBodyShapeError) + Vitest 셋업 + 단위 테스트 11 케이스 통과"`
   - 실패 → `"status": "error"` / `"blocked"` 와 사유.

## 금지사항

- `any` 또는 `as unknown as PageMetaBody` 같은 강제 캐스트 사용 금지. 이유: 타입 안전성. type guard 와 `asPageMetaBody` 만 사용.
- `usePageMeta.ts` 의 시그니처를 깨지 마라 (현재 `meta: Ref<PageMeta | null>`). 이유: phase 1 의 산출물 호환성. 좁히기는 본 step 의 책임 밖.
- 백엔드 enum (`SystemType` 등) 을 이 파일에서 재정의하지 마라. 이미 `types/meta.ts` 에 있음. import 만.
- Vitest 외의 테스트 프레임워크(jest 등) 를 도입하지 마라. 이유: Vue+Vite 생태 표준.
- 테스트 데이터에 실 운영 정보(이름·이메일·서버명) 넣지 마라. 가상 샘플만. ADR-011.
- 폼/그리드 필드 정의에 "직접 구현 컴포넌트 클래스" 같은 함수 참조를 박지 마라. 이유: 메타는 직렬화 가능해야 한다. 모든 필드는 string·number·boolean·간단한 객체로만.
