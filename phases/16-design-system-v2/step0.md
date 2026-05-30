# Step 0: design-language-and-tokens-v2

## 읽어야 할 파일
- `/CLAUDE.md`·`/docs/PRD.md`·`/docs/UI_GUIDE.md` (전체)
- `/docs/ARCHITECTURE.md` §16·17
- `/frontend/src/assets/styles/tokens.css`·`shadcn-mapping.css`·`base.css`
- 사용자 피드백: "전문가가 만든 솔루션이 아니라 평범한 대학생 수준" — shadcn 기본 룩·평면적·정보 밀도 낮음·도메인 특화 변별성 0

## 작업
**UI_GUIDE 를 "미니멀 회색·하얀색" 노선에서 "정보 밀도 + 시각 변별성 + 마이크로 인터랙션" 노선으로 재설계**. 토큰 시스템 확장 (모듈 컬러·elevation·motion·spacing).

### 1. UI_GUIDE.md 전면 개정
다음 섹션을 추가·교체:

- **§1 디자인 철학 v2**: 정보 밀도·도메인 시각 변별성·마이크로 인터랙션·다크 모드 우선 — "단일 액센트·미니멀" 노선 폐기.
- **§3 색상 — 모듈별 컬러 시스템**:
  - ITSM: indigo (`--color-itsm: #4f46e5`)
  - ITAM: teal (`--color-itam: #0d9488`)
  - PMS: violet (`--color-pms: #7c3aed`)
  - COMMON: amber (`--color-common: #d97706`)
  - SYSTEM: slate (`--color-system: #475569`)
  - 각 모듈 페이지는 헤더 띠·아이콘 배경·강조 텍스트에 모듈 컬러 사용.
- **§4 타이포 — 데이터 typography 강화**: tabular-nums·KPI 숫자 36px/700·trend indicator 13px 등.
- **§5 컴포넌트 추가**:
  - **KpiCard**: 큰 숫자·trend 화살표·sparkline·label.
  - **DonutChart·BarChart·LineChart·Sparkline**: ECharts 어댑터.
  - **KanbanBoard·KanbanColumn·KanbanCard**: 드래그 가능 보드.
  - **GanttChart·TimelineEvent**: 시간축 시각화.
  - **NotificationItem·ActivityFeedItem·MentionTag**.
  - **EmptyState** (lucide 아이콘 + 색 강조 + 행동 유도 버튼).
- **§6 elevation·shadow v2**:
  - `shadow-card`: `0 1px 2px rgba(0,0,0,0.05), 0 1px 3px rgba(0,0,0,0.05)` (얇은 카드 보더 강화)
  - `shadow-hover`: `0 4px 12px rgba(0,0,0,0.08)` (호버 카드)
  - `shadow-overlay`: 기존 `0 8px 24px rgba(0,0,0,0.12)` 유지
  - 영상 그림자 한정 규칙 폐기 — 카드·버튼·뱃지에도 미세 그림자 허용.
- **§7 motion — 정식 시스템**:
  - `--motion-fast: 120ms cubic-bezier(0.2, 0, 0, 1)` (button press·hover)
  - `--motion-base: 200ms cubic-bezier(0.2, 0, 0, 1)` (page transition·dialog open)
  - `--motion-slow: 320ms cubic-bezier(0.2, 0, 0, 1)` (kanban drag·gantt scroll)
  - `prefers-reduced-motion: reduce` 시 모든 motion 0.01ms.
- **§8 다크 모드 정식 채택**: `.dark` 클래스 토글로 모든 토큰 자동 전환. light/dark 두 색 모두 디자인 토큰.
- **§9 마이크로 인터랙션 카탈로그**:
  - Skeleton 로딩 (rectangle + shimmer)
  - Optimistic UI (mutation 후 즉시 반영 + 실패 시 rollback)
  - Hover detail (행 hover 시 우측 quick action 아이콘 fade-in)
  - Page transition (route 전환 시 fade + slide-up 8px)
  - Toast stagger animation
- **§10 폐기되는 v1 규칙**:
  - "단일 액센트만" → 모듈별 컬러 허용 (§3)
  - "그림자 영상 한정" → elevation 시스템으로 카드·버튼에 미세 그림자 (§6)
  - "그라데이션 금지" → 단색 강조 권장이지만 KPI 카드·empty state 에 제한적 사용 허용 (`from-X to-X/60` 같은 미묘한 톤)
  - "장식 목적 blur 금지" → TopBar frosted 외에 NotificationCenter·CommandPalette overlay 의 backdrop-blur 허용

### 2. tokens.css v2
기존 토큰 보존 + 신규 추가:

```css
@theme {
  /* 모듈 컬러 (light) */
  --color-itsm:        #4f46e5;
  --color-itsm-soft:   #eef2ff;  /* 10% alpha 대신 사용 */
  --color-itam:        #0d9488;
  --color-itam-soft:   #ccfbf1;
  --color-pms:         #7c3aed;
  --color-pms-soft:    #ede9fe;
  --color-common:      #d97706;
  --color-common-soft: #fef3c7;
  --color-system:      #475569;
  --color-system-soft: #f1f5f9;

  /* Elevation */
  --shadow-card:    0 1px 2px rgba(0,0,0,0.05), 0 1px 3px rgba(0,0,0,0.05);
  --shadow-hover:   0 4px 12px rgba(0,0,0,0.08);
  --shadow-overlay: 0 8px 24px rgba(0,0,0,0.12);

  /* Motion */
  --motion-fast:  120ms cubic-bezier(0.2, 0, 0, 1);
  --motion-base:  200ms cubic-bezier(0.2, 0, 0, 1);
  --motion-slow:  320ms cubic-bezier(0.2, 0, 0, 1);

  /* Spacing 확장 */
  --spacing-page-x: 24px;
  --spacing-section-y: 32px;
  --spacing-widget-gap: 16px;

  /* tabular nums 위한 폰트 feature */
  --font-feature-tabular: "tnum" on, "lnum" on;
}

/* 다크 모드 (전면 재설계) */
.dark {
  --color-background: #0b0d10;
  --color-surface:    #15181d;
  --color-surface-muted:   #1a1e24;
  --color-surface-hover:   #1f242b;
  --color-surface-selected:#13314f;

  --color-foreground:        #f5f7fa;
  --color-foreground-muted:  #a1a8b4;
  --color-foreground-subtle: #6b7280;

  --color-border:        #2a2f37;
  --color-border-subtle: #1f242b;

  --color-primary:      #6366f1;
  --color-primary-hover:#818cf8;

  /* 모듈 컬러 다크 */
  --color-itsm:        #818cf8;
  --color-itsm-soft:   #1e1b4b;
  --color-itam:        #5eead4;
  --color-itam-soft:   #134e4a;
  --color-pms:         #a78bfa;
  --color-pms-soft:    #2e1065;
  --color-common:      #fbbf24;
  --color-common-soft: #451a03;
  --color-system:      #94a3b8;
  --color-system-soft: #1e293b;

  --shadow-card:    0 1px 2px rgba(0,0,0,0.4),  0 1px 3px rgba(0,0,0,0.4);
  --shadow-hover:   0 4px 12px rgba(0,0,0,0.5);
  --shadow-overlay: 0 8px 24px rgba(0,0,0,0.6);
}
```

### 3. base.css 갱신
- body `font-feature-settings: var(--font-feature-tabular);` (숫자 자릿수 통일)
- `*:focus-visible { outline: 2px solid var(--color-ring); outline-offset: 2px; }` (기존 유지)
- `@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; } }`

### 4. 모듈 컬러 헬퍼 — `frontend/src/lib/module-color.ts`
```ts
import type { SystemType } from '@/types/meta';

export interface ModuleVisual {
  primaryVar: string;   // 'var(--color-itsm)'
  softVar:    string;   // 'var(--color-itsm-soft)'
  textClass:  string;   // 'text-itsm'
  bgSoftClass:string;   // 'bg-itsm-soft'
  borderClass:string;
  icon:       string;   // lucide icon name
}

export function moduleVisual(systemType: SystemType): ModuleVisual { ... }
```

### 5. 단위 테스트
- `moduleVisual` 매핑 5종 케이스.

## Acceptance Criteria
```bash
cd /Users/mwjeon/Projects/ai-work/harness_framework_ITSM

# UI_GUIDE 개정
grep -q "v2 재설계\|디자인 철학 v2\|모듈별 컬러" docs/UI_GUIDE.md

# 토큰 확장
grep -q "color-itsm\|color-itam\|color-pms" frontend/src/assets/styles/tokens.css
grep -q "shadow-card\|motion-fast\|motion-base" frontend/src/assets/styles/tokens.css
grep -q "\.dark {" frontend/src/assets/styles/tokens.css

# 헬퍼
test -f frontend/src/lib/module-color.ts
test -f frontend/src/lib/__tests__/module-color.spec.ts

cd frontend
pnpm type-check
pnpm lint
pnpm build
pnpm test
```

## 금지사항
- 기존 시멘틱 토큰(`--color-primary`·`--color-background` 등) 삭제·이름 변경 금지 — 호환성.
- 단순 hex 값 직접 컴포넌트에 박지 마라. 토큰만.
- 보라/인디고 클리셰는 모듈 컬러로만 (전체 글로벌 액센트 X).
- 다크 모드 토큰 매핑 누락 시 light/dark 전환 시 화면 박살.
- 운영 코드 console.log 금지.
