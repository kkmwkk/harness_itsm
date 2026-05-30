# UI 디자인 가이드 — Polestar10 ITG v2 (v2 재설계)

> 엔터프라이즈 운영 대시보드(ITSM·ITAM·PMS) UI 가이드. shadcn/vue + Tailwind CSS v4 의 CSS 변수 기반
> 테마로 구현한다. 모든 화면은 `page_meta` 한 건에서 동적으로 생성되므로, 본 가이드는 **메타가 만들어내는
> 화면들의 시각적 일관성**을 보장하기 위한 토큰·컴포넌트 규칙이다.
>
> **v2 재설계 배경 (2026-05-30)**: 1차(v1) "미니멀 회색·하얀색·단일 액센트" 노선이 *"전문가 솔루션이
> 아니라 평범한 대학생 수준 — 평면적·정보 밀도 낮음·도메인 변별성 0"* 라는 피드백을 받았다. v2 는
> **정보 밀도 + 도메인 시각 변별성 + 마이크로 인터랙션 + 다크 모드 우선**으로 노선을 전환한다. v1 의
> "단일 액센트만 / 그림자 영상 한정 / 그라데이션 전면 금지" 규칙은 §10 에서 폐기·완화한다.

---

## 1. 디자인 철학 v2

1. **정보 밀도가 곧 전문성이다.** 운영자는 한 화면에서 더 많은 사실을 읽고 싶어 한다. KPI·차트·그리드·
   타임라인을 위젯으로 조밀하게 배치하되, 여백·위계로 읽기 흐름을 잃지 않는다. (v1 의 "데이터가 주인공"은
   유지하되, "크롬을 비우는 것"보다 "사실을 더 많이·정확히 보여주는 것"을 우선한다.)
2. **모듈은 색으로 구별된다.** ITSM·ITAM·PMS·COMMON·SYSTEM 다섯 모듈은 각자의 컬러 정체성을 갖는다
   (§3). 사용자는 헤더 띠·아이콘 색만 보고 지금 어느 모듈에 있는지 즉시 안다.
3. **마이크로 인터랙션이 품질을 전달한다.** hover quick-action·skeleton 로딩·optimistic UI·page
   transition·toast stagger 가 "살아 있는" 제품 인상을 만든다 (§9). 정식 motion 시스템(§7) 위에서만 동작한다.
4. **다크 모드는 1급 시민이다.** light/dark 두 색을 모두 디자인 토큰으로 관리하고 `.dark` 토글로 자동
   전환한다 (§8). 어느 한쪽이 "덤"이 아니다.
5. **메타가 같으면 화면도 같다.** (v1 유지) 동일 필드 타입은 항상 동일 컴포넌트·동일 토큰으로 렌더링된다.
6. **색·hex 는 절대 컴포넌트에 박지 않는다.** 모든 색·그림자·모션은 토큰(`--color-*`·`--shadow-*`·
   `--motion-*`)과 그에 매핑된 Tailwind 유틸로만 쓴다.

---

## 2. 금지 패턴 (안티패턴) — v2 갱신

| 금지 | 이유 |
|------|------|
| 색·그림자·모션 hex/px 값을 컴포넌트에 직접 하드코딩 | 토큰 우회 → 다크 모드·테마 전환 박살. 반드시 토큰만 |
| 모듈 컬러를 글로벌 액센트로 사용 | 모듈 컬러(§3)는 모듈 식별 표식 전용. 일반 CTA·링크·focus 는 `--color-primary` |
| 6번째 모듈 컬러·임의 보조 컬러 도입 | 모듈 컬러는 5종 고정. 상태색(시맨틱) 외 임의 컬러 추가 금지 |
| weight 500 본문 | 타입 래더는 400 / 600 / 700. 500 은 버튼 라벨 전용 |
| 그리드 zebra-stripe | 호버·선택·hairline 디바이더로 충분 |
| 컬럼별 다른 폰트·다른 보더 색 | 그리드 내 시각 노이즈는 정보 식별을 방해 |
| 무분별한 glow/scale 애니메이션 | 모션은 §7 카탈로그 안에서만. 크기 흔들림·네온 글로우 금지 |
| 장식용 gradient orb·blur 원 | 운영 대시보드와 결이 맞지 않음. blur 는 §10 의 허용 범위(frosted/overlay)만 |

> v1 의 "그라데이션 전면 금지 / 그림자 영상 한정 / 두 번째 컬러 금지"는 §10 에서 **완화·폐기**된다.
> 단 완화는 "토큰으로 정의된 범위 안에서"라는 조건이 붙는다.

---

## 3. 색상 — 모듈별 컬러 시스템

Tailwind CSS v4 의 `@theme` 블록에 CSS 변수로 정의한다 (`tokens.css`). 토큰명은 의미 기반, 값은 light/dark
각각 정의하고 `.dark` 토글로 전환한다 (§8).

### 3-1. 액센트 / 인터랙션 (글로벌 — v1 유지·다크 갱신)
| 용도 | 토큰 | 라이트 | 다크 |
|------|------|--------|------|
| 주 액션 (Primary CTA, 링크, focus ring) | `--color-primary` | `#0066cc` | `#6366f1` |
| Primary hover | `--color-primary-hover` | `#0057b3` | `#818cf8` |
| Primary 텍스트 위 (반전) | `--color-primary-foreground` | `#ffffff` | `#0b0d10` |

### 3-2. 모듈 컬러 (★ v2 신규)

각 `systemType` 은 고유 컬러·soft(배경) 토큰을 갖는다. 헬퍼 `lib/module-color.ts` 의 `moduleVisual()`
가 systemType → `{ primaryVar, softVar, textClass, bgSoftClass, borderClass, icon }` 를 반환한다.

| 모듈 (`systemType`) | 컬러 | 토큰 | light | light-soft | dark | dark-soft | lucide 아이콘 |
|---|---|---|---|---|---|---|---|
| ITSM | indigo | `--color-itsm` / `-soft` | `#4f46e5` | `#eef2ff` | `#818cf8` | `#1e1b4b` | `LifeBuoy` |
| ITAM | teal | `--color-itam` / `-soft` | `#0d9488` | `#ccfbf1` | `#5eead4` | `#134e4a` | `Boxes` |
| PMS | violet | `--color-pms` / `-soft` | `#7c3aed` | `#ede9fe` | `#a78bfa` | `#2e1065` | `FolderKanban` |
| COMMON | amber | `--color-common` / `-soft` | `#d97706` | `#fef3c7` | `#fbbf24` | `#451a03` | `Layers` |
| SYSTEM | slate | `--color-system` / `-soft` | `#475569` | `#f1f5f9` | `#94a3b8` | `#1e293b` | `Settings` |

**사용처 (모듈 컬러 한정)**: 페이지 헤더 띠, 모듈 아이콘 배경(soft), 모듈 강조 텍스트/뱃지, KPI 카드
액센트, 차트 시리즈 기본색. **사용 금지**: 일반 버튼·링크·focus ring(이것은 `--color-primary`), 본문 텍스트.

> soft 토큰은 v1 의 "시맨틱 10% alpha" 대신 명시 색을 쓴다 — 다크 모드에서 alpha 누적 발색이 무너지는
> 것을 막기 위함. `bg-itsm-soft text-itsm` 조합이 라이트/다크 모두에서 안정적인 대비를 보장한다.

### 3-3. 서피스 / 배경
| 용도 | 토큰 | 라이트 | 다크 |
|------|------|--------|------|
| 페이지 캔버스 | `--color-background` | `#ffffff` | `#0b0d10` |
| 카드·그리드·폼 영역 | `--color-surface` | `#ffffff` | `#15181d` |
| 보조 서피스 (사이드바, 필터바) | `--color-surface-muted` | `#f5f7fa` | `#1a1e24` |
| 호버 상태 행 | `--color-surface-hover` | `#eef2f7` | `#1f242b` |
| 선택된 행 | `--color-surface-selected` | `#e6f0fb` | `#13314f` |

### 3-4. 텍스트
| 용도 | 토큰 | 라이트 | 다크 |
|------|------|--------|------|
| 본문·헤드라인 | `--color-foreground` | `#1d1d1f` | `#f5f7fa` |
| 보조 텍스트 | `--color-foreground-muted` | `#5a6270` | `#a1a8b4` |
| 비활성 / 미세 문구 | `--color-foreground-subtle` | `#9097a3` | `#6b7280` |
| 링크 (인라인) | `--color-link` | `#0066cc` | `#818cf8` |

### 3-5. 보더 / 구분선
| 용도 | 토큰 | 라이트 | 다크 |
|------|------|--------|------|
| 카드·인풋 보더 | `--color-border` | `#e3e6ea` | `#2a2f37` |
| 그리드 행 디바이더 | `--color-border-subtle` | `#eef0f3` | `#1f242b` |
| Focus ring | `--color-ring` | `#0066cc` | `#6366f1` |

### 3-6. 시맨틱 (상태 전용)
| 용도 | 토큰 | 값 |
|------|------|------|
| 성공 (PUBLISHED, 정상, 완료) | `--color-success` | `#16a34a` |
| 경고 (DRAFT, 검토 필요) | `--color-warning` | `#d97706` |
| 위험 (ERROR, DEPRECATED 강조) | `--color-danger` | `#dc2626` |
| 정보 (안내) | `--color-info` | `#0284c7` |
| 중립 / 보관 (ARCHIVED) | `--color-neutral` | `#525252` |

> 시맨틱 색은 상태 뱃지·인라인 알림·토스트에만. 모듈 컬러와 역할이 다르다 — 시맨틱=상태, 모듈=소속.

---

## 4. 타이포그래피 — 데이터 typography 강화

### 4-1. 폰트 스택
```css
--font-sans: "Pretendard Variable", "Inter", system-ui, -apple-system, "Segoe UI",
             "Apple SD Gothic Neo", sans-serif;
--font-mono: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace;
```

### 4-2. 숫자 typography (★ v2 신규)
- `body` 에 `font-feature-settings: var(--font-feature-tabular)` (`"tnum" on, "lnum" on`) 를 건다 —
  모든 숫자가 **고정폭(tabular)**으로 정렬되어 KPI·그리드 수치가 자릿수 흔들림 없이 줄을 맞춘다.
- KPI 큰 숫자: **36px / 700 / tabular-nums / line-height 1.1**.
- trend indicator(증감): 13px / 600, 상승 `text-success` + ▲, 하락 `text-danger` + ▼, 보합 `text-foreground-muted`.

### 4-3. 위계
| 용도 | 사이즈 | weight | line-height | letter-spacing |
|------|--------|--------|-------------|----------------|
| 페이지 타이틀 (H1) | 28px | 700 | 1.25 | -0.2px |
| 섹션 타이틀 (H2, 카드 헤더) | 20px | 600 | 1.35 | -0.1px |
| 서브섹션 (H3) | 17px | 600 | 1.4 | -0.05px |
| KPI 숫자 | 36px | 700 | 1.1 | -0.5px |
| 본문 / 폼 라벨 | 14px | 400 | 1.5 | 0 |
| 그리드 셀 텍스트 | 13px | 400 | 1.4 | 0 |
| 그리드 헤더 | 13px | 600 | 1.4 | 0.2px |
| 캡션 / trend / 보조 | 12~13px | 400~600 | 1.4 | 0.1px |
| 버튼 라벨 | 14px | 500 | 1 | 0 |
| 메타 ID·코드 | 13px | 400 (mono) | 1.4 | 0 |

> 본문 14px, 그리드 13px. 헤드라인 weight 600/700. weight 500 은 버튼 라벨 전용.

---

## 5. 컴포넌트 규칙

### 5-1. 기본 컴포넌트 (v1 유지)

#### 버튼 (shadcn/vue `Button`)
| 변형 | 사용 | 토큰 |
|------|------|------|
| `default` (Primary) | 폼 submit, 주요 액션 | `bg-primary text-primary-foreground` |
| `secondary` | 보조 액션 | `bg-surface-muted text-foreground border-border` |
| `outline` | 취소·뒤로가기 | `border-border text-foreground` |
| `ghost` | 그리드 인라인 액션, 아이콘 버튼 | `text-foreground hover:bg-surface-hover` |
| `destructive` | 삭제·DEPRECATED 강제 전환 | `bg-danger text-white` |
| `link` | 인라인 텍스트 링크 | `text-link underline-offset-4 hover:underline` |

크기: `sm 32px` / `default 36px` / `lg 40px`. 버튼에 `shadow-card` 미세 그림자 허용(§6).

#### 입력 필드 (`Input`·`Textarea`·`Select`)
```
radius: 8px (rounded-md) · border 1px var(--color-border) · height 36px · padding-x 12px
focus: ring 2px var(--color-ring) · disabled: opacity 0.5
```

#### 카드 (`Card`)
```
bg var(--color-surface) · border 1px var(--color-border) · radius 12px · padding 24px
shadow: shadow-card (미세) — 호버 시 shadow-hover (§6)
```

#### 데이터 그리드 (`DynamicGrid`)
- shadcn DataTable (≤1000행): 헤더 `bg surface-muted` 13/600 sticky, 행 40px, 디바이더
  `border-subtle`, 호버 `surface-hover`, 선택 `surface-selected`, zebra 금지, 페이지당 20/50/100.
- AG Grid Vue3 (>1000행·인라인편집·export): `ag-theme-itg`(토큰 매핑) 만. `alpine` 직접 금지.

#### 상태 뱃지 (`StatusBadge`·`PriorityBadge`)
```
pill(9999px) · padding 2px 10px · 12px/600 · 배경 시맨틱 10% alpha · 텍스트 시맨틱 본색
```
`metaStatus`: DRAFT=warning / PUBLISHED=success / DEPRECATED=neutral / ARCHIVED=subtle.

#### 폼 레이아웃 (`DynamicForm`)
- `two-column`: ≥1024px 2열(col-gap 24·row-gap 16), `span:2` 전체 폭. 라벨 위 14/500, 필수 `*` danger.
- 액션: 폼 하단 우측, 취소(outline) → 저장(primary).

#### 다이얼로그 (`Dialog`)
- 폭 small 480 / default 640 / large 880 / fullscreen 1280. 오버레이 `bg-foreground/40`(다크 `/60`),
  overlay 는 `shadow-overlay` + backdrop-blur 허용(§10).

#### 토스트
- 우측 상단 stack, 자동 4초(에러 수동). 좌측 시맨틱 4px 보더. stagger 등장(§9).

### 5-2. 데이터 시각화·대시보드 컴포넌트 (★ v2 신규)

차트는 **ECharts 어댑터**로 감싸 컴포넌트로 제공한다(직접 ECharts API 노출 금지). 모든 시리즈 색은 모듈
컬러/시맨틱 토큰을 CSS var 로 주입한다.

| 컴포넌트 | 역할 | 핵심 props |
|---|---|---|
| **KpiCard** | 큰 숫자 + trend 화살표 + sparkline + label | `label`·`value`·`trend`(±%)·`series?`·`module?` |
| **DonutChart** | 비율(상태 분포 등) | `data: {name,value}[]`·`colors?` |
| **BarChart** | 범주별 수량 | `categories`·`series`·`stacked?` |
| **LineChart** | 시계열 추이 | `xAxis`·`series`·`area?` |
| **Sparkline** | KPI 내 인라인 미니 추이 | `points: number[]`·`color?` |

> 차트 텍스트·축·그리드선은 `--color-foreground-muted`·`--color-border-subtle` 토큰을 ECharts theme 에
> 매핑. light/dark 전환 시 차트도 함께 전환되어야 한다.

### 5-3. 보드·타임라인 컴포넌트 (★ v2 신규)

| 컴포넌트 | 역할 |
|---|---|
| **KanbanBoard / KanbanColumn / KanbanCard** | 드래그 가능한 칸반(상태별 컬럼). 드래그는 `--motion-slow` |
| **GanttChart / TimelineEvent** | 시간축 시각화(프로젝트·SLA). 가로 스크롤 `--motion-slow` |
| **TimelineEvent** | 단일 이벤트 막대/마커 (이벤트 타입별 색) |

### 5-4. 알림·피드·empty 컴포넌트 (★ v2 신규)

| 컴포넌트 | 역할 |
|---|---|
| **NotificationItem** | 알림 한 건(아이콘·제목·시각·읽음 상태) |
| **ActivityFeedItem** | 활동 피드 한 줄(행위자·동작·대상·시각) |
| **MentionTag** | `@사용자` 멘션 칩 (pill, `bg-primary/10 text-primary`) |
| **EmptyState** | lucide 아이콘(모듈 컬러 강조) + 안내 문구 + 행동 유도 버튼. raw 텍스트 대신 카탈로그(§아래) 사용 |

---

## 6. Elevation & Shadow v2

v1 의 "그림자는 떠 있는 오버레이 전용" 규칙을 폐기한다. 카드·버튼·뱃지에도 미세 그림자를 허용해 평면적
인상을 걷어낸다. 토큰만 사용한다.

| 토큰 | 값 (light) | 값 (dark) | 사용처 |
|------|-----------|----------|--------|
| `--shadow-card` | `0 1px 2px rgba(0,0,0,.05), 0 1px 3px rgba(0,0,0,.05)` | `…rgba(0,0,0,.4)` | 카드·KPI·버튼 기본 |
| `--shadow-hover` | `0 4px 12px rgba(0,0,0,.08)` | `…rgba(0,0,0,.5)` | 카드·칸반카드 호버 |
| `--shadow-overlay` | `0 8px 24px rgba(0,0,0,.12)` | `…rgba(0,0,0,.6)` | Dialog·Popover·Dropdown |

Tailwind 유틸: `shadow-card`·`shadow-hover`·`shadow-overlay`. 그 외 임의 box-shadow 금지.

---

## 7. Motion — 정식 시스템

모든 트랜지션·애니메이션은 세 토큰 중 하나를 쓴다. 자의적 duration·easing 금지.

| 토큰 | 값 | 사용처 |
|------|------|--------|
| `--motion-fast` | `120ms cubic-bezier(0.2,0,0,1)` | 버튼 press·hover·색 전환 |
| `--motion-base` | `200ms cubic-bezier(0.2,0,0,1)` | page transition·dialog open·toast |
| `--motion-slow` | `320ms cubic-bezier(0.2,0,0,1)` | kanban drag·gantt scroll·사이드패널 |

`@media (prefers-reduced-motion: reduce)` 에서 모든 animation/transition duration 을 `0.01ms` 로 강제
(`base.css`). 모션 민감 사용자 보호.

---

## 8. 다크 모드 정식 채택

- `.dark` 클래스를 루트(`<html>` 또는 `#app`)에 토글하면 **모든 토큰이 자동 전환**된다. 토큰은 light 를
  `@theme`, dark 를 `.dark { … }` 에 정의한다(`tokens.css`).
- **신규 토큰(모듈 컬러·elevation)도 `.dark` 에 빠짐없이 매핑**한다 — 하나라도 누락되면 전환 시 그 색만
  light 값으로 남아 화면이 깨진다. (모듈 컬러·shadow 다크 매핑 완료 — §3-2·§6)
- 컴포넌트는 `dark:` variant 를 직접 쓰지 않고 토큰에 의존한다. `bg-surface`·`text-foreground`·
  `shadow-card` 처럼 토큰 유틸을 쓰면 다크 전환이 공짜로 따라온다.
- 다크/라이트 모두 본문 텍스트 ↔ 배경 대비 WCAG AA(4.5:1) 이상 유지(§11).

> 토글 UI(테마 스위처)·`localStorage` 지속·시스템 선호 감지는 다음 step(1: dark-mode-and-theme-switcher)
> 에서 도입한다. 본 step 은 토큰 매핑까지 정착시킨다.

---

## 9. 마이크로 인터랙션 카탈로그 (★ v2 신규)

| 패턴 | 동작 | 모션 |
|------|------|------|
| **Skeleton 로딩** | 텍스트/카드 자리에 rectangle + shimmer. blink 금지 | shimmer 1.2s loop |
| **Optimistic UI** | mutation 직후 화면 즉시 반영 → 실패 시 rollback + 에러 토스트 | `--motion-fast` |
| **Hover detail** | 그리드 행 hover 시 우측 quick-action 아이콘 fade-in | `--motion-fast` |
| **Page transition** | route 전환 시 fade + slide-up 8px | `--motion-base` |
| **Toast stagger** | 다중 토스트 순차(50ms 간격) 등장 | `--motion-base` |
| **카드 hover lift** | `shadow-card → shadow-hover` 전환(이동 없음) | `--motion-fast` |

> 모든 패턴은 §7 motion 토큰 위에서만. `prefers-reduced-motion` 사용자에게는 즉시 상태 전환(애니메이션 제거).

---

## 10. 폐기·완화되는 v1 규칙

| v1 규칙 | v2 처리 | 조건 |
|---------|---------|------|
| "단일 액센트만 사용" | **완화** — 모듈별 컬러 5종 허용(§3-2) | 모듈 식별 표식 전용. 글로벌 CTA·링크·focus 는 여전히 `--color-primary` |
| "그림자는 오버레이 영상 한정" | **폐기** — elevation 시스템으로 카드·버튼·뱃지 미세 그림자 허용(§6) | `--shadow-*` 토큰만 |
| "그라데이션 전면 금지" | **완화** — KPI 카드·EmptyState 에 제한적 단색 톤 그라데이션 허용 | `from-X to-X/60` 같은 미묘한 동일 색 톤만. 무지개·다색 그라데이션·텍스트 그라데이션은 여전히 금지 |
| "장식 목적 blur 전면 금지" | **완화** — TopBar frosted 외에 NotificationCenter·CommandPalette overlay 의 `backdrop-blur` 허용 | 가독성/레이어 분리 목적만. 장식 blur 원·orb 는 여전히 금지(§2) |
| "weight 500 본문 금지" | **유지** | 버튼 라벨 전용 |
| "그리드 zebra-stripe 금지" | **유지** | 호버·선택·hairline 으로 식별 |

---

## 11. 접근성 (a11y) — v1 유지

- 모든 인터랙티브 요소 `:focus-visible` 시 `--color-ring` 2px outline(`base.css`). outline 제거 금지.
- 색상만으로 정보 전달 금지 — 상태는 뱃지 라벨·아이콘과 함께. 모듈 컬러도 아이콘·라벨과 병행.
- 폼 라벨은 `<label for>` / `aria-labelledby` 로 연결. placeholder 를 라벨로 대체 금지.
- light/dark 모두 본문 ↔ 배경 대비 WCAG AA(4.5:1) 이상.
- 그리드 행 선택·정렬·칸반 카드 이동은 키보드만으로 조작 가능.
- 아이콘 단독 버튼은 `aria-label` 필수.

---

## 12. 레이아웃·Radius·아이콘 (v1 유지)

- **간격**: 베이스 4px. 토큰 4/8/12/16/24/32/48/64. 페이지 좌우 `--spacing-page-x`(24px), 섹션 분리
  `--spacing-section-y`(32px), 위젯 거터 `--spacing-widget-gap`(16px).
- **페이지 골격**: TopBar(56px sticky frosted) · Sidebar(240px collapse) · PageHeader(64px, 모듈 컬러 띠)
  · Toolbar · 본문(그리드/폼/대시보드).
- **브레이크포인트**: Tailwind 기본(sm640/md768/lg1024/xl1280/2xl1536). `md` 이하 Sidebar 오프캔버스.
- **Radius**: `none 0 / sm 6 / md 8 / lg 12 / pill 9999 / full 50%`. 중간값 자의 생성 금지.
- **아이콘**: `@lucide/vue`(또는 `lucide-vue-next`), strokeWidth 1.5, `currentColor` 상속. 모듈 아이콘은
  `moduleVisual().icon` 으로 일관 매핑.

---

## 13. Do's / Don'ts (v2)

### Do
- 모듈 페이지는 `moduleVisual(systemType)` 으로 헤더 띠·아이콘·강조색을 일관 적용.
- 색·그림자·모션은 토큰(`--color-*`·`--shadow-*`·`--motion-*`)과 매핑 유틸로만.
- 카드·버튼에 `shadow-card`, 호버에 `shadow-hover`.
- KPI·그리드 숫자는 tabular-nums(이미 body 에 적용)로 자릿수 정렬.
- 다크 모드는 토큰 유틸(`bg-surface` 등)에 의존 — `dark:` variant 직접 사용 최소화.

### Don't
- hex/px 색·그림자·duration 을 컴포넌트에 직접 박지 마라.
- 모듈 컬러를 글로벌 액센트로 쓰지 마라(글로벌은 `--color-primary`).
- 6번째 모듈 컬러·임의 보조 컬러를 만들지 마라.
- weight 500 본문·그리드 zebra·장식 blur orb·다색/텍스트 그라데이션 금지.
- `.dark` 토큰 매핑을 누락하지 마라(전환 시 그 색만 깨진다).
- `ag-theme-itg` 외 AG Grid 기본 테마 직접 사용 금지.
