# UI 디자인 가이드 — Polestar10 ITG v2

> 엔터프라이즈 운영 대시보드(ITSM·ITAM·PMS) UI 가이드. shadcn/vue + Tailwind CSS v4 의 CSS 변수 기반 테마로 구현한다. 모든 화면은 `page_meta` 한 건에서 동적으로 생성되므로, 본 가이드는 **메타가 만들어내는 화면들의 시각적 일관성**을 보장하기 위한 토큰·컴포넌트 규칙이다.

## 1. 디자인 원칙

1. **데이터가 주인공, 크롬은 뒤로 빠진다.** 그리드·폼·상세 영역의 정보 밀도가 우선. 헤더·네비·툴바는 콘텐츠를 받치는 단일 베이스로 가라앉는다.
2. **단일 액센트로 모든 인터랙션을 표현한다.** "클릭 가능" 신호는 한 가지 파란색뿐. 두 번째 브랜드 컬러는 도입하지 않는다.
3. **여백과 위계가 구조를 만든다.** 카드 보더·그림자·그라데이션 대신 여백·구분선·타이포 위계로 정보 구조를 표현한다.
4. **시맨틱 색은 상태 전용.** 빨강·초록·노랑은 오직 상태(에러·성공·경고)에만 쓰고 장식으로는 쓰지 않는다.
5. **메타가 같으면 화면도 같다.** 동일 필드 타입은 항상 동일 컴포넌트·동일 토큰으로 렌더링된다.

## 2. 금지 패턴 (안티패턴)

| 금지 | 이유 |
|------|------|
| 그라데이션 배경·그라데이션 텍스트 | 엔터프라이즈 운영 화면의 가독성을 해친다. 토큰 자체에 정의하지 않는다 |
| glass morphism (장식용 `backdrop-filter: blur`) | 정보 밀도 화면에는 부적합. 단, 스티키 헤더의 가독성 목적 frosted 는 예외 |
| gradient orb / blur 장식 원 | 운영 대시보드와 결이 맞지 않는다 |
| 보라/인디고 액센트 | "AI = 보라색" 클리셰 회피. 액센트는 Action Blue 단 하나 |
| 두 번째 액센트 컬러 도입 | 모든 인터랙션은 단일 블루. 상태/시맨틱 색 외 추가 금지 |
| weight 500 본문 | 타입 래더는 400 / 600 / 700. 500 은 비워둔다 |
| 모든 카드 동일 radius 남용 | 카드 12px / 인풋·버튼 8px / chip 9999px. 중간값 자의 생성 금지 |
| 컬럼별 다른 폰트·다른 보더 색 | 그리드 내 시각 노이즈는 정보 식별을 방해한다 |
| 박스섀도 글로우 애니메이션 | 운영 화면 어디에도 사용하지 않는다 |

## 3. 색상 토큰

Tailwind CSS v4 의 `@theme` 블록에 CSS 변수로 정의한다. 토큰명은 의미 기반(`--color-surface-*`)으로, 값은 라이트/다크 테마 모두 동일 토큰을 매핑.

### 3-1. 액센트 / 인터랙션
| 용도 | 토큰 | 라이트 | 다크 |
|------|------|--------|------|
| 주 액션 (Primary CTA, 링크, focus ring) | `--color-primary` | `#0066cc` | `#3b9eff` |
| Primary hover | `--color-primary-hover` | `#0057b3` | `#5cb0ff` |
| Primary 텍스트 위 (반전) | `--color-primary-foreground` | `#ffffff` | `#0b0d10` |

### 3-2. 서피스 / 배경
| 용도 | 토큰 | 라이트 | 다크 |
|------|------|--------|------|
| 페이지 캔버스 | `--color-background` | `#ffffff` | `#0b0d10` |
| 카드·그리드·폼 영역 | `--color-surface` | `#ffffff` | `#15181d` |
| 보조 서피스 (사이드바, 필터바) | `--color-surface-muted` | `#f5f7fa` | `#1a1e24` |
| 호버 상태 행 | `--color-surface-hover` | `#eef2f7` | `#1f242b` |
| 선택된 행 | `--color-surface-selected` | `#e6f0fb` | `#13314f` |

### 3-3. 텍스트
| 용도 | 토큰 | 라이트 | 다크 |
|------|------|--------|------|
| 본문·헤드라인 | `--color-foreground` | `#1d1d1f` | `#f5f7fa` |
| 보조 텍스트 (캡션, placeholder) | `--color-foreground-muted` | `#5a6270` | `#a1a8b4` |
| 비활성 / 미세 문구 | `--color-foreground-subtle` | `#9097a3` | `#6b7280` |
| 링크 (인라인) | `--color-link` | `#0066cc` | `#3b9eff` |

### 3-4. 보더 / 구분선
| 용도 | 토큰 | 라이트 | 다크 |
|------|------|--------|------|
| 카드·인풋 보더 | `--color-border` | `#e3e6ea` | `#2a2f37` |
| 그리드 행 디바이더 | `--color-border-subtle` | `#eef0f3` | `#1f242b` |
| Focus ring | `--color-ring` | `#0066cc` | `#3b9eff` |

### 3-5. 시맨틱 (상태 전용)
| 용도 | 토큰 | 값 |
|------|------|------|
| 성공 (PUBLISHED, 정상, 완료) | `--color-success` | `#16a34a` |
| 경고 (DRAFT, 검토 필요) | `--color-warning` | `#d97706` |
| 위험 (ERROR, DEPRECATED 강조) | `--color-danger` | `#dc2626` |
| 정보 (안내) | `--color-info` | `#0284c7` |
| 중립 / 보관 (ARCHIVED) | `--color-neutral` | `#525252` |

> 시맨틱 색은 상태 뱃지(`StatusBadge`)·인라인 알림·토스트에만 사용한다. 버튼·헤더·테이블 헤드 등 일반 UI 에는 단일 액센트(`--color-primary`)와 무채색만 사용.

## 4. 타이포그래피

### 4-1. 폰트 스택
```css
--font-sans: "Pretendard Variable", "Inter", system-ui, -apple-system, "Segoe UI",
             "Apple SD Gothic Neo", sans-serif;
--font-mono: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace;
```
- 한글 가독성 확보를 위해 Pretendard 우선. 미설치 환경에선 Inter → 시스템 폰트로 폴백.
- 코드·JSON 미리보기·메타 ID 표시는 mono 폰트.

### 4-2. 위계
| 용도 | 사이즈 | weight | line-height | letter-spacing |
|------|--------|--------|-------------|----------------|
| 페이지 타이틀 (H1) | 28px | 700 | 1.25 | -0.2px |
| 섹션 타이틀 (H2, 카드 헤더) | 20px | 600 | 1.35 | -0.1px |
| 서브섹션 (H3) | 17px | 600 | 1.4 | -0.05px |
| 본문 / 폼 라벨 | 14px | 400 | 1.5 | 0 |
| 그리드 셀 텍스트 | 13px | 400 | 1.4 | 0 |
| 그리드 헤더 | 13px | 600 | 1.4 | 0.2px |
| 캡션 / 보조 설명 | 12px | 400 | 1.4 | 0.1px |
| 버튼 라벨 | 14px | 500 | 1 | 0 |
| 메타 ID·코드 | 13px | 400 (mono) | 1.4 | 0 |

> 본문은 14px. 그리드는 13px (정보 밀도 확보). 헤드라인 weight 는 600/700 만 사용. weight 500 은 버튼 라벨 전용으로 비워둔다.

## 5. 컴포넌트 규칙

### 5-1. 버튼 (shadcn/vue `Button`)
| 변형 | 사용 | 토큰 |
|------|------|------|
| `default` (Primary) | 폼 submit, 주요 액션 | `bg-primary text-primary-foreground` |
| `secondary` | 보조 액션 | `bg-surface-muted text-foreground border-border` |
| `outline` | 취소·뒤로가기 | `border-border text-foreground` |
| `ghost` | 그리드 인라인 액션, 아이콘 버튼 | `text-foreground hover:bg-surface-hover` |
| `destructive` | 삭제·DEPRECATED 강제 전환 | `bg-danger text-white` |
| `link` | 인라인 텍스트 링크 | `text-link underline-offset-4 hover:underline` |

크기: `sm 32px` / `default 36px` / `lg 40px`. 터치 타깃 44×44 확보 영역(모바일)은 시각 크기와 무관하게 패딩 영역으로 확보.

### 5-2. 입력 필드 (shadcn/vue `Input`, `Textarea`, `Select`)
```
radius: 8px (rounded-md)
border: 1px solid var(--color-border)
height (default): 36px
padding-x: 12px
focus: ring 2px var(--color-ring), border-color transparent
disabled: opacity 0.5, cursor not-allowed
```

`DynamicForm` 의 필드 타입 → 컴포넌트 매핑은 ARCHITECTURE 문서의 매핑표를 따른다.

### 5-3. 카드 (shadcn/vue `Card`)
```
bg:     var(--color-surface)
border: 1px solid var(--color-border)
radius: 12px (rounded-xl)
padding: 24px
shadow: 없음 (Hairline 만 사용)
```

카드 헤더에 액션을 배치할 때는 `ghost` 버튼만 사용. 카드 외곽선과 그림자를 동시에 쓰지 않는다.

### 5-4. 데이터 그리드 (`DynamicGrid`)

#### shadcn DataTable (≤1000행)
```
헤더:    bg var(--color-surface-muted), 13px / 600, sticky top
행 높이: 40px (default), 36px (compact)
행 디바이더: 1px solid var(--color-border-subtle)
호버:    bg var(--color-surface-hover)
선택:    bg var(--color-surface-selected)
zebra:   사용 안 함 (호버·선택만으로 식별)
페이지네이션: 우측 하단, 페이지당 20/50/100 선택
```

#### AG Grid Vue3 (>1000행 또는 인라인 편집/엑셀 export)
- 테마: 직접 만든 `ag-theme-itg` (위 토큰을 AG Grid CSS 변수에 매핑). 기본 `alpine` 테마 직접 사용 금지.
- 헤더·셀 타이포는 위 DataTable 과 동일 규격.
- 인라인 편집 셀: focus 시 `--color-ring` 2px, 저장 성공 시 `--color-success` 1.5초 페이드.
- 행 그룹·트리 그리드 사용 시 들여쓰기는 16px 단위.

### 5-5. 상태 뱃지 (`StatusBadge`, `PriorityBadge`)
```
radius: 9999px (pill)
padding: 2px 10px
font: 12px / 600
배경: 시맨틱 토큰의 10% alpha
텍스트: 시맨틱 토큰 본색
```

`metaStatus` 매핑:
| 상태 | 토큰 | 표시 |
|------|------|------|
| `DRAFT` | `--color-warning` | 작성 중 |
| `PUBLISHED` | `--color-success` | 배포 중 |
| `DEPRECATED` | `--color-neutral` | 구버전 |
| `ARCHIVED` | `--color-foreground-subtle` | 보관 |

티켓 우선순위(`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`)도 동일한 pill 규격, 무채색 4단계 + `CRITICAL` 만 `--color-danger`.

### 5-6. 폼 레이아웃 (`DynamicForm`)
- 단일 컬럼: 모바일·간단 폼 기본.
- `layout: two-column`: 데스크탑 ≥1024px 에서 2열 (column-gap 24px, row-gap 16px). 필드의 `span: 2` 는 전체 폭.
- 라벨은 필드 위에 배치 (14px/500). 필수 표시는 라벨 우측 `*` (`--color-danger`).
- 검증 메시지는 필드 하단, 12px/400, `--color-danger`. 도움말은 같은 위치 `--color-foreground-muted`.
- 액션 버튼은 폼 하단 우측 정렬, 취소(outline) → 저장(primary) 순.

### 5-7. 다이얼로그 / 모달 (shadcn/vue `Dialog`)
- 최대 폭: small 480px / default 640px / large 880px / fullscreen 1280px.
- 헤더(20px/600) · 본문 · 푸터(액션) 세 영역. 푸터 액션은 폼 액션 규칙과 동일.
- 오버레이는 `bg-foreground/40` (라이트), `bg-foreground/60` (다크). blur 사용하지 않음.

### 5-8. 토스트 / 알림
- 우측 상단 stack. 자동 사라짐 4초 (에러는 수동 닫기).
- 좌측에 시맨틱 토큰 4px 보더로 카테고리 식별.
- 본문 14px/400, 액션 링크는 우측 정렬.

## 6. 레이아웃

### 6-1. 그리드·간격
- 베이스 단위: **4px**. 토큰: 4 / 8 / 12 / 16 / 24 / 32 / 48 / 64.
- 페이지 좌우 여백: 데스크탑 24px / 와이드 32px / 모바일 16px.
- 카드 간 거터: 16px (그리드 레이아웃), 24px (섹션 분리).

### 6-2. 페이지 골격
```
┌──────────────────────────────────────────────────────────────┐
│  TopBar (h 56px, sticky)  — 모듈명 · 검색 · 사용자 메뉴     │
├──────────┬───────────────────────────────────────────────────┤
│ Sidebar  │  PageHeader (h 64px) — 타이틀 · 액션 · 버전 뱃지  │
│ (w 240px,│ ─────────────────────────────────────────────────│
│ collapse)│  Toolbar  — 필터 · 컬럼 선택 · 내보내기            │
│          │  DynamicGrid 또는 DynamicForm 본문                │
│          │                                                   │
└──────────┴───────────────────────────────────────────────────┘
```

### 6-3. 브레이크포인트 (Tailwind 기본 유지)
- `sm 640` / `md 768` / `lg 1024` / `xl 1280` / `2xl 1536`.
- `md` 이하에서 Sidebar 는 오프캔버스 (햄버거 토글).
- 그리드는 `lg` 이상에서 컬럼 풀세트, 이하에서는 우선순위 낮은 컬럼 자동 숨김 (`responsive: { hideAt: 'md' }` 메타 옵션).

## 7. Radius 토큰

| 토큰 | 값 | 용도 |
|------|------|------|
| `--radius-none` | 0 | 풀블리드 헤더 |
| `--radius-sm` | 6px | 작은 칩, 토스트 아이콘 컨테이너 |
| `--radius-md` | 8px | 인풋, 버튼, ghost 액션 |
| `--radius-lg` | 12px | 카드, 다이얼로그 |
| `--radius-pill` | 9999px | 상태 뱃지, 필터 칩, 페이지네이션 |
| `--radius-full` | 50% | 아바타, 원형 아이콘 버튼 |

> 카드 12px / 액션 8px / 뱃지 9999px. 중간값(예: 10px, 14px) 을 새로 만들지 않는다.

## 8. Elevation & Shadow

| 레벨 | 처리 | 사용처 |
|------|------|--------|
| Flat | 보더·그림자 없음 | 페이지 캔버스, 본문 섹션 |
| Hairline | `1px solid var(--color-border)` | 카드, 그리드, 인풋 |
| Sticky frosted | `backdrop-filter: blur(12px)`, 배경 80% alpha | TopBar 만 |
| Overlay shadow | `0 8px 24px rgba(0,0,0,0.12)` | Dialog, Popover, Dropdown |

> 본문 카드·버튼·뱃지에는 그림자를 추가하지 않는다. Overlay 그림자는 떠 있는 컴포넌트 전용.

## 9. 모션·애니메이션

- 모든 트랜지션 기본 `150ms ease-out`. 다이얼로그 등장 `200ms ease-out`, 슬라이드 사이드패널 `250ms ease-out`.
- 버튼 press 상태 `transform: scale(0.97)` 시스템 마이크로 인터랙션.
- 그리드 행 추가/삭제 fade 200ms. 정렬 시 행 위치 이동 애니메이션은 사용하지 않음 (대용량에서 비용 큼).
- Hover 상태는 색상 전환만 (`bg-surface-hover`). 크기·그림자 변경 금지.
- `prefers-reduced-motion: reduce` 사용자에게는 트랜지션 0.01ms.

## 10. 아이콘

- 라이브러리: `lucide-vue-next` (24px 기본, 16px·20px 변형 허용).
- strokeWidth: 1.5.
- 색은 `currentColor` 상속. 액세서리 컬러 금지.
- 아이콘 단독 버튼(`Button variant="ghost" size="icon"`)은 항상 `aria-label` 필수.

## 11. 접근성 (a11y)

- 모든 인터랙티브 요소는 `:focus-visible` 시 `--color-ring` 2px outline 표시. outline 제거 금지.
- 색상만으로 정보 전달 금지 — 상태는 뱃지 라벨·아이콘과 함께 표시.
- 폼 라벨은 시맨틱 `<label for>` 또는 `aria-labelledby` 로 연결. placeholder 를 라벨로 대체하지 않는다.
- 라이트/다크 모두 본문 텍스트 ↔ 배경 명도 대비 WCAG AA(4.5:1) 이상.
- 그리드 행 선택·정렬은 키보드만으로 모두 조작 가능해야 한다.

## 12. Do's / Don'ts

### Do
- `--color-primary` 단일로 모든 인터랙션 표현.
- 카드는 Hairline + 12px radius, 그림자 없음.
- 본문 14px / 400, 헤드라인 600·700.
- 시맨틱 색은 상태 표현에만.
- 메타에서 정의된 필드 타입은 항상 동일 컴포넌트로 렌더링.

### Don't
- 두 번째 액센트 컬러 도입 금지.
- 카드·버튼·뱃지에 그림자 추가 금지.
- 그라데이션 배경·텍스트 금지.
- weight 500 본문 금지 (버튼 라벨 전용).
- radius 문법 자의 추가 금지 (`sm 6 / md 8 / lg 12 / pill 9999` 외 새 값 금지).
- 그리드 zebra-stripe 금지 (호버·선택만으로 충분).
- 그리드 내 컬럼별 다른 폰트/보더/색상 사용 금지.
- AG Grid `alpine` 기본 테마 직접 사용 금지 (`ag-theme-itg` 만 사용).
