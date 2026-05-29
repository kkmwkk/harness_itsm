# Step 2: form-submit-integration

## 읽어야 할 파일

- `/CLAUDE.md` — "절대 규칙" (API 통신은 `useFetch`, console.log 금지)
- `/docs/UI_GUIDE.md` — §5-8 토스트 (우측 상단 stack, 4초 자동, 좌측 시맨틱 보더 4px, 본문 14px/400)
- `/docs/ARCHITECTURE.md` — §4 동적 렌더링 흐름, §7 API 설계
- `/phases/3-itsm-ticket-backend/index.json` step 2 — POST `/api/tickets` 응답 형태 (`ApiResponse<TicketResponse>` 201)
- `/phases/4-itsm-ticket-frontend/step0.md` — usePageData reload
- `/phases/2-dynamic-render/step5.md` — DynamicPage dialog-form 액션 + onFormSubmit
- `/frontend/src/components/dynamic/DynamicPage.vue`·`DynamicForm.vue`·`composables/usePageData.ts`·`lib/api.ts`

## 작업

이 step 의 목적은 **`DynamicForm` 의 submit 이 실제 백엔드 POST 를 호출하고, 성공 시 그리드를 새로고침 + 토스트를 띄우며, 실패 시 에러 토스트를 표시하는 것**이다. UI_GUIDE §5-8 의 토스트 규격을 따른다.

### 1. 토스트 인프라

shadcn-vue 의 toast 또는 sonner 컴포넌트를 추가:

```bash
cd frontend
pnpm dlx shadcn-vue@latest add sonner
# 또는
pnpm dlx shadcn-vue@latest add toast
```

> sonner 가 더 단순 (글로벌 mount 한 번). UI_GUIDE §5-8 의 규격(우측 상단·시맨틱 4px 보더)을 sonner 의 `richColors` 옵션 또는 직접 custom 으로 맞춘다.

#### `src/main.ts` 에 글로벌 mount

```vue
<!-- App.vue 또는 별도 -->
<template>
  <RouterView />
  <Sonner position="top-right" :duration="4000" rich-colors />
</template>
```

### 2. composable — `src/composables/useDataMutation.ts`

POST/PATCH/DELETE 의 공통 helper. `useApiFetch` 를 활용하되 호출 시점 제어 (`refetch: false`, `immediate: false`).

```ts
import { ref, type Ref } from 'vue';
import { useApiFetch } from '@/lib/api';
import type { ApiEnvelope } from '@/types/meta';

export interface MutationResult<TIn, TOut> {
  isLoading:  Ref<boolean>;
  error:      Ref<string | null>;
  submit:     (path: string, payload: TIn) => Promise<TOut | null>;
}

export function useDataMutation<TIn, TOut>(): MutationResult<TIn, TOut> {
  const isLoading = ref(false);
  const error     = ref<string | null>(null);

  async function submit(path: string, payload: TIn): Promise<TOut | null> {
    isLoading.value = true;
    error.value     = null;
    try {
      const { data, statusCode, error: fetchError } = await useApiFetch(path, {
        immediate: false, refetch: false,
      }).post(payload).json<ApiEnvelope<TOut>>();
      if (fetchError.value || (statusCode.value && statusCode.value >= 400)) {
        const msg = data.value?.message ?? fetchError.value?.message ?? '저장 실패';
        error.value = msg;
        return null;
      }
      return data.value?.data ?? null;
    } finally {
      isLoading.value = false;
    }
  }

  return { isLoading, error, submit };
}
```

> VueUse useFetch 의 `.post(...).json<...>()` 패턴이 비동기 helper 로 쓰일 수 있는지 검증 필요. 안 되면 `fetch` 직접 호출 + Authorization 헤더 주입은 `useApiFetch` 의 inner 동작과 동등하게 작성.

### 3. `DynamicPage` 갱신 — submit 통합

기존 `onFormSubmit` 가 `console.warn` mock 인 부분을 실 POST 로 교체.

```vue
<script setup lang="ts">
import { toast } from 'vue-sonner';      // 또는 shadcn 의 useToast
import { useDataMutation } from '@/composables/useDataMutation';
/* ... 기존 import ... */

const { submit: submitForm, isLoading: isSubmitting } = useDataMutation<
  Record<string, unknown>, Record<string, unknown>
>();

async function onFormSubmit(values: Record<string, unknown>) {
  if (!body.value?.api) return;
  const result = await submitForm(body.value.api, values);
  if (result) {
    toast.success(`${meta.value?.title ?? '항목'} 이(가) 생성되었습니다.`);
    dialogOpen.value = false;
    await reload();                              // 그리드 새로고침
  } else {
    toast.error('생성에 실패했습니다.');         // 상세는 error.value 또는 별도 description
  }
}
</script>
```

> POST 본문은 그대로 폼 values 를 보냄. 백엔드는 `TicketCreateRequest` 형태를 기대 (`title`·`content`·`priority`·`category`·`assigneeId`). 검증 메시지가 form 의 `errors` 또는 토스트 양쪽에 보여질 수 있게.

### 4. UI_GUIDE §5-8 준수

- 토스트 우측 상단 stack.
- 본문 14px / 400.
- 자동 사라짐 4초 (`:duration="4000"`).
- 좌측 시맨틱 4px 보더: 성공=success, 에러=danger.
- blur 사용 안 함.

sonner 의 기본 스타일이 위 규격과 다르면 `~/styles/toast.css` 로 override:

```css
[data-sonner-toaster] [data-toast][data-type="success"] { border-left: 4px solid var(--color-success); }
[data-sonner-toaster] [data-toast][data-type="error"]   { border-left: 4px solid var(--color-danger); }
[data-sonner-toaster] [data-toast] {
  font-size: 14px; font-weight: 400;
  background-color: var(--color-surface);
  color: var(--color-foreground);
  box-shadow: 0 8px 24px rgba(0,0,0,0.12);     /* UI_GUIDE §8 Overlay shadow */
  border: 1px solid var(--color-border);
}
```

### 5. 단위 테스트 (선택, 가벼움)

`useDataMutation` 의 성공/실패 경로를 mock 으로 검증하는 것이 복잡하므로, **본 step 은 e2e (step 3) 가 주된 검증**. 단위 테스트는 다음 케이스 정도:

`src/composables/__tests__/useDataMutation.spec.ts` — submit 호출 시 `isLoading` 토글 검증 (mock fetch). 결과 nullable 변환 검증.

> 또는 본 step 의 단위 테스트를 생략하고 step 3 의 e2e 에서 시나리오로 검증. **테스트 부담 최소화**.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) sonner 또는 toast 컴포넌트 도입
test -d src/components/ui/sonner -o -d src/components/ui/toast
grep -q '"vue-sonner"\|"@/components/ui/sonner"\|"@/components/ui/toast"' src/**/*.ts src/**/*.vue 2>/dev/null || \
  grep -Rln 'sonner\|useToast' src/

# 2) 파일
test -f src/composables/useDataMutation.ts

# 3) DynamicPage 의 onFormSubmit 가 더 이상 console.warn mock 이 아닌 실 호출
! grep -q "console.warn.*\[DynamicPage\] form submit (mock)" src/components/dynamic/DynamicPage.vue

# 4) 정적 + 테스트
pnpm type-check
pnpm lint
pnpm build
pnpm test

# 5) dev 라우트 회귀
pnpm dev &
sleep 6
for p in /itsm /_dev/dynamic-page /system/meta; do
  test "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:5173$p)" = "200"
done
kill %1
```

수동 검증 (backend + 시드 살아있는 상태):
- `/itsm` 진입 → 그리드 5건 표시.
- "등록" 버튼 → 다이얼로그.
- 필수(`title`·`priority`) 비워서 저장 → 검증 에러 표시.
- 유효 값 입력 + 저장 → 토스트 성공 + 다이얼로그 닫힘 + 그리드에 6번째 행(새 티켓) 등장.
- 백엔드를 끄고 저장 시도 → 에러 토스트.

## 검증 절차

1. AC 통과.
2. 아키텍처 체크리스트:
   - `useDataMutation` 이 `useApiFetch` 위에서 동작하는가? (CLAUDE.md 절대 규칙)
   - DynamicPage 의 onFormSubmit 가 mock 에서 실 POST 로 전환되었는가?
   - 성공 시 그리드 새로고침(reload)·다이얼로그 닫힘·토스트가 동시에 동작하는가?
   - 토스트가 UI_GUIDE §5-8 의 규격 (우측 상단·14px/400·시맨틱 4px 보더)인가?
   - 실패 경로(백엔드 오프) 도 에러 토스트로 표시되는가?
3. step 2 업데이트:
   - 성공 → `"summary": "useDataMutation composable (POST 공통, isLoading/error/submit) + DynamicPage 의 onFormSubmit 실 POST 로 교체 (성공→reload+토스트, 실패→에러 토스트) + sonner 도입 + UI_GUIDE §5-8 규격 토스트 스타일 override."`

## 금지사항

- `axios`·`ky`·`ofetch` 등 별도 HTTP 라이브러리 추가 금지. CLAUDE.md 절대 규칙.
- 토스트에 그라데이션·blur(장식) 추가 금지. UI_GUIDE.
- 토스트 자동 사라짐을 1초 이하로 설정하지 마라. 너무 짧으면 사용자가 읽지 못한다.
- DynamicPage 가 도메인 (ticket) 특화 로직을 갖지 않게 — submit 은 `meta.api` POST + 응답 정규화 만. ticket 전용 분기 금지. ADR-004.
- 사용자 모듈 API 호출 금지.
- 인증 토큰을 환경변수 하드코딩 금지 (기존 패턴 유지).
- 백엔드 코드 수정 금지.
- 운영 코드에 `console.log` 잔류 금지.
- 상태 전이 UI 추가 금지 — 본 step 은 생성(POST) 만.
- 새 엔드포인트 추가 금지 (백엔드 변경 금지).
