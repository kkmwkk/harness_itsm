# Step 2: shadcn-vue-setup

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `/CLAUDE.md` — "기술 스택" (shadcn/vue, 소스 코드 소유 방식)
- `/docs/UI_GUIDE.md` — §4-1 폰트 스택(Pretendard Variable 우선), §5 컴포넌트 규칙 (Button 변형 6종, Input radius 8px, Card radius 12px, Dialog 4단계 폭, DataTable 행 높이·sticky 헤더·zebra 금지)
- `/docs/ADR.md` — ADR-001 (shadcn/vue 채택 이유 — 소스 소유, 라이브러리 락인 회피), ADR-007 (그리드 이원화 — DataTable + AG Grid, AG Grid 도입은 다음 phase)
- `/phases/1-frontend-foundation/step0.md`·`step1.md` — Vite/Vue/TS 스켈레톤과 Tailwind v4 토큰 매핑

이전 step 의 결과로 토큰 미니 갤러리(`_dev/TokenGallery.vue`) 가 동작 중. `@theme` 블록에 `--color-primary` 등이 박혀 있음.

## 작업

이 step 의 목적은 **shadcn/vue 를 frontend 에 통합하고, `Button`·`Input`·`Card`·`Dialog`·`DataTable` 5종 컴포넌트를 설치하여 토큰을 그대로 사용하도록 정착시키는 것**이다. Pretendard 폰트도 같이 도입한다. AG Grid 는 다음 phase.

### 1. shadcn/vue 사전 의존성

shadcn/vue 는 `radix-vue` (또는 `reka-ui`, fork) + `class-variance-authority` + `tailwind-variants` + `clsx` + `tailwind-merge` + `lucide-vue-next` 를 기반으로 한다.

shadcn/vue CLI 가 위 의존성을 자동 설치하지만, 명시적으로:

```bash
cd frontend
pnpm add class-variance-authority clsx tailwind-merge lucide-vue-next
pnpm add radix-vue        # 또는 시점에 따라 'reka-ui' (shadcn-vue 의 최신 권장)
pnpm add -D @types/node   # path 모듈 타입
```

> 시점에 따라 `radix-vue` 가 `reka-ui` 로 이름이 바뀌었을 수 있다. `shadcn-vue` CLI 가 요구하는 패키지명을 그대로 사용하라.

### 2. shadcn/vue CLI 초기화

```bash
cd frontend
pnpm dlx shadcn-vue@latest init
```

대화형 프롬프트에서 다음을 선택:
- Style: `default`
- Base color: 임의 (어차피 우리 토큰으로 덮어씀 — `Slate` 같은 무난한 값)
- Tailwind config 경로: (v4 사용이므로 묻지 않거나 N/A)
- Global CSS 경로: `src/assets/styles/tokens.css` 또는 별도 `src/assets/styles/shadcn.css`
- CSS 변수 사용: yes
- Components path: `@/components/ui`
- Utils path: `@/lib/utils`
- Aliases: `@/*` (이미 step 0 의 tsconfig 에 박혀 있음)

생성되는 파일:
- `frontend/components.json` — shadcn/vue 설정.
- `frontend/src/lib/utils.ts` — `cn()` 헬퍼.
- (CLI 가 추가하는 base CSS 변수가 우리 `tokens.css` 와 충돌 가능 — 아래 §3 참고)

### 3. 토큰 충돌 정리

shadcn/vue CLI 가 `--background`·`--primary` 같은 변수를 자체 형식으로 추가할 수 있다. 우리는 step 1 에서 이미 `--color-primary` 등을 시맨틱 토큰으로 박았으므로, **shadcn 컴포넌트가 우리 토큰을 사용하도록 매핑**한다.

방법은 두 가지:

**A. shadcn 변수 → 우리 토큰 매핑 (권장)**

`src/assets/styles/shadcn-mapping.css` (새 파일):

```css
@layer base {
  :root {
    /* shadcn 의 시맨틱 이름이 우리 토큰을 가리키도록 별칭 */
    --background:        var(--color-background);
    --foreground:        var(--color-foreground);
    --card:              var(--color-surface);
    --card-foreground:   var(--color-foreground);
    --popover:           var(--color-surface);
    --popover-foreground:var(--color-foreground);
    --primary:           var(--color-primary);
    --primary-foreground:var(--color-primary-foreground);
    --secondary:         var(--color-surface-muted);
    --secondary-foreground: var(--color-foreground);
    --muted:             var(--color-surface-muted);
    --muted-foreground:  var(--color-foreground-muted);
    --accent:            var(--color-surface-hover);
    --accent-foreground: var(--color-foreground);
    --destructive:       var(--color-danger);
    --destructive-foreground: #ffffff;
    --border:            var(--color-border);
    --input:             var(--color-border);
    --ring:              var(--color-ring);
    --radius:            var(--radius-md);
  }
  .dark {
    --background:        var(--color-background);
    --foreground:        var(--color-foreground);
    --card:              var(--color-surface);
    --card-foreground:   var(--color-foreground);
    /* ... 동일 매핑 ... */
  }
}
```

`src/main.ts` 의 import 순서를 다음과 같이 한다 (매핑이 tokens 다음, shadcn 컴포넌트가 사용하기 전에):

```ts
import './assets/styles/tokens.css';
import './assets/styles/shadcn-mapping.css';
import './assets/styles/base.css';
```

**B. shadcn 변수만 사용**

`tokens.css` 를 shadcn 의 변수명에 맞춰 다시 작성. 단, 이 phase 는 **A 방식 사용** — 시맨틱 토큰 이름(`--color-primary`)이 더 의미를 잘 드러내며, `UI_GUIDE.md` 와 일치한다.

### 4. 컴포넌트 5종 추가

```bash
cd frontend
pnpm dlx shadcn-vue@latest add button
pnpm dlx shadcn-vue@latest add input
pnpm dlx shadcn-vue@latest add card
pnpm dlx shadcn-vue@latest add dialog
pnpm dlx shadcn-vue@latest add table     # shadcn 의 Table primitive
# 선택: data-table 은 별도 helper 가 필요할 수 있음 — 이 step 에서는 Table primitive 까지
```

생성되는 위치: `src/components/ui/<component>/`. 각 컴포넌트는 여러 `.vue` 파일로 분리될 수 있다 (`Card.vue`, `CardHeader.vue` 등). **shadcn/vue 가 만들어준 파일은 그대로 유지** — 소스 소유 모델의 핵심.

### 5. Pretendard Variable 폰트 통합

CDN 또는 npm 양자택일. **권장: npm 패키지** (오프라인·CSP 친화):

```bash
pnpm add pretendard
```

`src/main.ts` 상단(또는 `tokens.css`)에서 import:

```ts
import 'pretendard/dist/web/variable/pretendardvariable.css';
```

> Pretendard 미설치 환경에서도 토큰의 `--font-sans` 폴백(Inter, system-ui) 으로 동작하지만, 이 phase 에서는 명시 설치.

### 6. lucide 아이콘

`lucide-vue-next` 는 위에서 이미 추가. shadcn 컴포넌트가 내부에서 사용. 추가 작업 없음.

### 7. 검증 페이지 — `src/views/_dev/ShadcnSampler.vue`

토큰 갤러리(`TokenGallery.vue`) 와 별도로, shadcn 컴포넌트가 우리 토큰을 따르는지 한눈에 검증.

요구사항:
- **Button** 6 변형 (`default`/`secondary`/`outline`/`ghost`/`destructive`/`link`) 한 줄.
- **Input** 1 종 (placeholder "검색...").
- **Card** 헤더+본문 1 종.
- **Dialog** 1 종 (트리거 버튼 → 열림).
- **Table** 컬럼 3개 × 행 3개 (id/title/status) — DataTable 의 시각 토대.

스켈레톤:

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import {
  Dialog, DialogTrigger, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import {
  Table, TableHeader, TableHead, TableBody, TableRow, TableCell,
} from '@/components/ui/table';

const rows = [
  { id: 'itg-ticket-v1-1', title: '샘플 페이지', status: 'DRAFT' },
  { id: 'itg-ticket-v1-2', title: '샘플 페이지', status: 'PUBLISHED' },
  { id: 'itg-ticket-v1-3', title: '샘플 페이지', status: 'DEPRECATED' },
];
const open = ref(false);
</script>

<template>
  <main class="mx-auto max-w-5xl p-6 space-y-10">
    <h1>Shadcn Sampler</h1>

    <section class="space-x-2">
      <Button>Default</Button>
      <Button variant="secondary">Secondary</Button>
      <Button variant="outline">Outline</Button>
      <Button variant="ghost">Ghost</Button>
      <Button variant="destructive">Destructive</Button>
      <Button variant="link">Link</Button>
    </section>

    <section>
      <Input placeholder="검색..." />
    </section>

    <section>
      <Card>
        <CardHeader><CardTitle>샘플 카드</CardTitle></CardHeader>
        <CardContent>본문 14px / 400. Hairline 보더만, 그림자 없음.</CardContent>
      </Card>
    </section>

    <section>
      <Dialog v-model:open="open">
        <DialogTrigger as-child>
          <Button variant="outline">Open dialog</Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader><DialogTitle>샘플 다이얼로그</DialogTitle></DialogHeader>
          <p>모달 본문 — 14px / 400.</p>
        </DialogContent>
      </Dialog>
    </section>

    <section>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>id</TableHead>
            <TableHead>title</TableHead>
            <TableHead>status</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="r in rows" :key="r.id">
            <TableCell class="font-mono text-[13px]">{{ r.id }}</TableCell>
            <TableCell>{{ r.title }}</TableCell>
            <TableCell>{{ r.status }}</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </section>
  </main>
</template>
```

`App.vue` 는 임시로 `ShadcnSampler` 와 `TokenGallery` 를 둘 다 보여주도록 변경하거나, `ShadcnSampler` 로 단일 교체. 라우팅은 step 3.

### 8. UI 토큰 일치 확인 (수동 검증 체크리스트)

- `Button[variant=default]` 의 배경이 `--color-primary` (`#0066cc`).
- `Input` 의 보더가 1px solid `--color-border`, focus 시 ring `--color-ring`.
- `Card` 의 radius 가 12px (`--radius-lg`).
- `Dialog` overlay 가 blur 없이 단순 어두운 fade.
- `Table` 헤더 배경이 `--color-surface-muted`, 행 호버가 `--color-surface-hover`, zebra-stripe 없음.

## Acceptance Criteria

```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM/frontend

# 1) 의존성 — shadcn 인프라
pnpm install
grep -E '"class-variance-authority"|"clsx"|"tailwind-merge"|"lucide-vue-next"' package.json | wc -l | xargs -I{} test {} -ge 4

# 2) shadcn 컴포넌트 5종 생성 확인
test -d src/components/ui/button
test -d src/components/ui/input
test -d src/components/ui/card
test -d src/components/ui/dialog
test -d src/components/ui/table

# 3) cn() 유틸 존재
test -f src/lib/utils.ts && grep -q "export function cn" src/lib/utils.ts

# 4) Pretendard 설치
grep -q '"pretendard"' package.json

# 5) shadcn-mapping.css 존재 + tokens 별칭
test -f src/assets/styles/shadcn-mapping.css
grep -q "var(--color-primary)" src/assets/styles/shadcn-mapping.css

# 6) 타입·린트·빌드
pnpm type-check
pnpm lint
pnpm build
test -f dist/index.html

# 7) dev 서버 부팅 + 샘플러 렌더
pnpm dev &
DEV_PID=$!
sleep 5
curl -fsS http://localhost:5173 -o /tmp/index.html
grep -q 'id="app"' /tmp/index.html
kill $DEV_PID
```

수동 검증:
- 브라우저에서 ShadcnSampler 가 보여야 하고, Button default 배경이 `#0066cc`, Dialog 오버레이가 blur 없는 단순 dim, Table 에 zebra-stripe 가 없는지 시각 확인.

## 검증 절차

1. 위 AC 커맨드를 실행한다. 모든 단계가 통과해야 한다.
2. 아키텍처 체크리스트:
   - shadcn/vue CLI 가 생성한 `components.json`·`src/lib/utils.ts`·`src/components/ui/<...>` 파일들이 정상 존재하는가?
   - `src/components/ui/` 의 5 개 컴포넌트가 모두 들어있는가? (Button·Input·Card·Dialog·Table)
   - `shadcn-mapping.css` 가 시맨틱 토큰(`--color-primary` 등) 으로 shadcn 의 `--primary`·`--background`·`--border`·`--ring` 등을 가리키도록 별칭 매핑하는가?
   - Pretendard Variable 폰트가 npm 또는 import 로 적재되는가?
   - shadcn 의 기본 회색·블루 외에 우리 토큰의 `#0066cc` 가 Primary 로 적용되는가? (다른 색이면 매핑 실패)
   - shadcn 가 생성한 컴포넌트 파일을 수동 수정하지 않았는가? (소스 소유는 보존, 그러나 이 step 에서는 그대로 유지)
3. 결과에 따라 `phases/1-frontend-foundation/index.json` 의 step 2 를 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "shadcn/vue 통합(components.json + lib/utils.ts) + 컴포넌트 5종(Button·Input·Card·Dialog·Table) + Pretendard Variable + shadcn-mapping.css 로 시맨틱 토큰(--color-primary 등)을 shadcn 변수(--primary 등)에 별칭 매핑. ShadcnSampler.vue 로 시각 검증."`
   - 수정 3회 시도 후에도 실패 → `"status": "error"`, `"error_message": "<구체적 에러>"`
   - 사용자 개입 필요 (CLI 가 대화형으로 멈춤·shadcn-vue 버전 변경 등) → `"status": "blocked"`, `"blocked_reason": "<구체적 사유>"` 후 즉시 중단

## 금지사항

- shadcn/vue 가 생성한 컴포넌트 파일을 임의 수정하지 마라. 이유: 이 step 의 목적은 통합이지 커스터마이징이 아니다. 변형이 필요하면 다음 phase 에서 별도 ADR 로 명시.
- 토큰 매핑 방향을 거꾸로 하지 마라 (shadcn 변수 → `tokens.css` 에 박기). 이유: `UI_GUIDE.md` 의 시맨틱 토큰 이름이 도메인 자산. 별칭은 shadcn → tokens 으로.
- Tailwind v3 의 `tailwind.config.js` 안에 shadcn 색상 토큰을 넣지 마라. 이유: v4 사용. config 파일 도입 시 정신 모델 분기.
- shadcn 의 `data-table` 컴포넌트를 이 step 에서 추가하지 마라. 이유: TanStack Table 의존 + 컬럼 정의 추상화는 다음 phase 의 `DynamicGrid` 구현 시 함께. 이 step 은 Table primitive 까지.
- AG Grid Vue3 의존성을 추가하지 마라. 이유: 다음 phase 의 책임. 이 phase 의 의존성 폭발 회피.
- VeeValidate / Zod / Pinia / Vue Router / VueUse 를 이 step 에서 설치하지 마라. 이유: step 3·4 의 책임.
- 임의의 그라데이션 배경·blur(장식)·gradient orb 를 ShadcnSampler 에 추가하지 마라. 이유: UI_GUIDE §2 금지 패턴.
- Tailwind v4 의 `@tailwindcss/postcss` 패키지를 추가 설치하지 마라. 이유: Vite 플러그인(`@tailwindcss/vite`)이 이미 동작 중.
- shadcn 컴포넌트에 `style="..."` 인라인 색·radius 를 박지 마라. 이유: 토큰 기반 일관성. shadcn variant 또는 Tailwind 유틸리티만.
- `_dev/ShadcnSampler.vue` 에 실제 운영 데이터·사용자 이름 등을 넣지 마라. 이유: ADR-011 — 가상 샘플 (`샘플 페이지`, `itg-ticket-v1-1`).
- `console.log` 를 컴포넌트·검증 페이지에 남기지 마라. ESLint 가 error. 디버그 흔적은 제거.
