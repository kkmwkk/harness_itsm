# Step 0: ui-messages-catalog

## 읽어야 할 파일

- `/CLAUDE.md` v2.1·절대 규칙
- `/docs/PRD.md` §6 UX 베이스라인
- `/docs/ARCHITECTURE.md` §17-1 메시지 카탈로그
- `/docs/ADR.md` ADR-020 (UX 메시지 카탈로그 — raw 백엔드 토큰 노출 금지)
- `/frontend/src/composables/usePageMeta.ts`·`usePageData.ts`·`useDataMutation.ts` (현재 error 처리)
- `/frontend/src/components/dynamic/DynamicPage.vue`·`pages/system/MetaPage.vue` (사용자 노출 메시지)

## 작업

이 step 의 목적은 **사용자 화면에 노출되는 모든 메시지를 단일 카탈로그(`lib/ui-messages.ts`) 로 통합하고, composable·페이지의 error/loading/empty 메시지를 카탈로그로 매핑하는 것**이다. 백엔드 `errorCode` 토큰의 사용자 화면 직접 노출을 차단한다.

### 1. 카탈로그 — `frontend/src/lib/ui-messages.ts`

```ts
/**
 * 사용자 화면 노출 메시지 단일 카탈로그 (ADR-020).
 * 백엔드 errorCode 토큰은 분기·로깅 용도이며 화면에는 한글 메시지만 노출.
 * 다국어 도입(v2.2) 시 본 파일만 키별 다국어 리소스로 전환.
 */
export const UI = {
  empty: {
    grid:    '아직 등록된 항목이 없습니다.',
    metaNotPublished: '아직 준비된 화면이 없습니다. 메타가 등록되지 않았거나 배포되지 않은 상태입니다.',
    metaNotPublishedHint: '시스템 관리자에게 메타 등록·배포를 요청하거나, 메타 관리에서 DRAFT 를 PUBLISHED 로 전환하세요.',
    menuEmpty: '접근 가능한 메뉴가 없습니다. 관리자에게 권한을 요청하세요.',
  },
  error: {
    metaLoad:     '메타를 불러올 수 없습니다.',
    metaShape:    '메타 본문 형식이 올바르지 않습니다. 관리자에게 문의하세요.',
    dataLoad:     '데이터를 불러올 수 없습니다.',
    submit:       '저장에 실패했습니다.',
    submitDetail: (entityName: string) => `${entityName} 저장에 실패했습니다.`,
    network:      '네트워크 연결을 확인하세요.',
    authRequired: '로그인이 필요합니다.',
    forbidden:    '이 기능에 접근할 권한이 없습니다.',
    notFound:     '요청한 데이터를 찾을 수 없습니다.',
    invalidInput: '입력값을 확인하세요.',
    unknown:      '일시적인 오류가 발생했습니다. 잠시 후 다시 시도하세요.',
  },
  loading: {
    meta:    '화면 정의를 불러오는 중...',
    data:    '데이터를 불러오는 중...',
    menu:    '메뉴를 불러오는 중...',
    submit:  '저장 중...',
  },
  success: {
    created:  (entityName: string) => `${entityName} 이(가) 생성되었습니다.`,
    updated:  (entityName: string) => `${entityName} 이(가) 수정되었습니다.`,
    deleted:  (entityName: string) => `${entityName} 이(가) 삭제되었습니다.`,
    loggedIn: (userName: string) => `${userName}님 환영합니다.`,
    loggedOut:'로그아웃되었습니다.',
  },
} as const;

/**
 * 백엔드 errorCode → 사용자 메시지 매핑.
 * 매핑되지 않은 코드는 UI.error.unknown 으로 fallback.
 */
const ERROR_CODE_MAP: Record<string, string> = {
  META_NOT_PUBLISHED: UI.empty.metaNotPublished,
  META_NOT_FOUND:     UI.error.metaLoad,
  TICKET_NOT_FOUND:   UI.error.notFound,
  ASSET_NOT_FOUND:    UI.error.notFound,
  USER_NOT_FOUND:     UI.error.notFound,
  VALIDATION_FAILED:  UI.error.invalidInput,
  AUTH_REQUIRED:      UI.error.authRequired,
  FORBIDDEN:          UI.error.forbidden,
  LOGIN_FAILED:       '아이디 또는 비밀번호가 올바르지 않습니다.',
  INVALID_TOKEN:      UI.error.authRequired,
  INVALID_REQUEST:    UI.error.invalidInput,
  DATA_INTEGRITY:     UI.error.invalidInput,
  INTERNAL_ERROR:     UI.error.unknown,
};

export function mapErrorCode(code: string | null | undefined): string {
  if (!code) return UI.error.unknown;
  return ERROR_CODE_MAP[code] ?? UI.error.unknown;
}
```

### 2. composable 매핑 적용

#### `composables/usePageMeta.ts`
- 현재 `errorMsg` 계산이 `data.value?.errorCode` 의 raw 접두사를 제거하는 휴리스틱. **`mapErrorCode(data.value?.errorCode)` 로 교체**.
- `notPublished` 분기 시 `null` 반환은 유지 (DynamicPage 가 카탈로그의 `UI.empty.metaNotPublished` 표시).

#### `composables/usePageData.ts`
- error 메시지가 raw → `mapErrorCode(data.value?.errorCode)` 또는 `UI.error.dataLoad`.

#### `composables/useDataMutation.ts`
- 실패 시 `error.value = mapErrorCode(...) || UI.error.submit`.

#### `lib/api.ts` 의 `onFetchError`
- 기존 `ctx.error = new Error(\`${code}: ${msg}\`)` → 코드는 분기·로깅용으로만 별도 보존, 메시지는 `mapErrorCode(code)`.

### 3. 페이지·컴포넌트 적용

#### `DynamicPage.vue`
- 현재 `notPublished` 카드의 안내 텍스트를 `UI.empty.metaNotPublished` + `UI.empty.metaNotPublishedHint` 로 교체.
- `metaError` 카드의 메시지를 `UI.error.metaLoad`.
- `dataError` 카드의 메시지를 `UI.error.dataLoad`.

#### `pages/LoginPage.vue`
- `toast.error('아이디 또는 비밀번호가 올바르지 않습니다.')` → `UI.error.loginFailed` 같은 키 추가 또는 기존 매핑 활용.

#### `pages/system/MetaPage.vue`
- notPublished·error 메시지 카탈로그 매핑.

#### 기타 등록 토스트
- `toast.success` 호출이 산재한 곳을 `UI.success.created/updated/...` 로 교체.

### 4. 단위 테스트

`src/lib/__tests__/ui-messages.spec.ts`:

1. `UI_카탈로그_모든_키_string_타입`.
2. `mapErrorCode_META_NOT_PUBLISHED_매핑`.
3. `mapErrorCode_unknown_code_fallback`.
4. `mapErrorCode_null_fallback`.
5. `UI.success.created_함수형_라벨_생성`.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 카탈로그 파일
test -f src/lib/ui-messages.ts
grep -q "ERROR_CODE_MAP\|mapErrorCode" src/lib/ui-messages.ts
grep -q "metaNotPublished" src/lib/ui-messages.ts

# 2) composable 매핑
grep -q "ui-messages\|UI\\.error\\|mapErrorCode" src/composables/usePageMeta.ts
grep -q "ui-messages\|UI\\.error\\|mapErrorCode" src/composables/usePageData.ts
grep -q "ui-messages\|UI\\.error\\|mapErrorCode" src/composables/useDataMutation.ts

# 3) DynamicPage 도 카탈로그 사용
grep -q "ui-messages\|UI\\." src/components/dynamic/DynamicPage.vue

# 4) raw errorCode 코드명이 화면 노출용으로 안 남음 (검색 — 보수적으로 확인)
! grep -RIn "META_NOT_PUBLISHED:\|TICKET_NOT_FOUND:" src/components/ src/pages/ 2>/dev/null

# 5) 정적·테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 검증 절차

1. AC 통과.
2. 아키텍처 체크:
   - `UI` 카탈로그가 empty/error/loading/success 4 카테고리 풀세트?
   - `mapErrorCode` 가 백엔드 코드 12+ 개 매핑?
   - composable·DynamicPage·LoginPage·MetaPage 가 모두 카탈로그 사용?
   - 사용자 화면에 백엔드 errorCode 접두사("CODE: 메시지") 노출 없음?
3. step 0 업데이트:
   - 성공 → `"summary": "lib/ui-messages.ts (UI.empty/error/loading/success 카탈로그 + ERROR_CODE_MAP 백엔드 12+ 코드 매핑 + mapErrorCode fallback) + usePageMeta/usePageData/useDataMutation/lib/api.ts 의 error 가 카탈로그 매핑 + DynamicPage·LoginPage·MetaPage 적용. raw errorCode 화면 노출 차단(ADR-020)."`

## 금지사항

- 카탈로그 키 이름을 백엔드 errorCode 와 동일하게 만들지 마라 — frontend 자기 키 네임스페이스 유지.
- 메시지에 백엔드 errorCode 토큰("META_NOT_PUBLISHED" 등) 포함 금지. 한글 문장만.
- 다국어 도입을 본 step 에서 시도하지 마라 — 카탈로그만 정착, i18n 은 v2.2.
- 카탈로그를 페이지마다 hard copy 금지. 단일 import.
- 백엔드 코드 수정 금지.
- 운영 코드 console.log 금지.
- 메시지 길이를 과도하게 길지 않게 (한 줄 20~50자 권장, 다단 안내는 hint 별도).
