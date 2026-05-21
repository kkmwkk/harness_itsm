# UI 디자인 가이드

> 참고: `etc/appleDesign.md` (Apple의 웹 디자인 시스템 분석). TubeNote의 UI는 그 시스템을 1인용 요약 대시보드 맥락에 적응시킨 버전이다.

## 디자인 원칙
1. **콘텐츠가 주인공, UI는 뒤로 빠진다.** 화면의 주인공은 항상 마크다운 요약본이다. 입력 폼·리스트·네비는 콘텐츠를 받쳐주는 단일 베이스로 가라앉는다 — 마케팅 페이지가 아니라 매일 쓰는 도구.
2. **단일 액센트(Action Blue)로 모든 인터랙션을 표현한다.** "클릭 가능"의 신호는 한 가지 파란색뿐. 두 번째 브랜드 색상은 도입하지 않는다.
3. **여백이 곧 위계다.** 카드 보더·그라데이션·그림자로 구조를 표현하기보다 충분한 여백과 타이포 위계(weight 600 ↔ 400)로 표현한다.

## AI 슬롭 안티패턴 — 하지 마라
| 금지 사항 | 이유 |
|-----------|------|
| backdrop-filter: blur() (장식 목적) | glass morphism은 AI 템플릿의 가장 흔한 징후. 단, 스티키 바의 가독성 목적 frosted-glass는 예외 |
| gradient-text (배경 그라데이션 텍스트) | AI가 만든 SaaS 랜딩의 1번 특징. Apple 시스템에는 그라데이션 디자인 토큰 자체가 없다 |
| "Powered by AI" 배지 | 기능이 아니라 장식. 사용자에게 가치 없음 |
| box-shadow 글로우 애니메이션 | 네온 글로우 = AI 슬롭. shadow는 오직 product 이미지(여기선 요약 카드 썸네일)에만 |
| 보라/인디고 브랜드 색상 | "AI = 보라색" 클리셰. 액센트는 Action Blue #0066cc 단 하나 |
| 두 번째 액센트 컬러 도입 | 모든 "click me"는 단일 블루. 성공/에러 시맨틱 외의 색은 추가하지 않는다 |
| weight 500 본문 | 타입 래더는 300 / 400 / 600 / 700. 500은 의도적으로 비워둔다 |
| 모든 카드 동일 radius (rounded-2xl 남용) | 용도별로 radius 문법이 다르다. 유틸 카드 18px / 컴팩트 버튼 8px / pill CTA 9999px |
| 배경 gradient orb (blur-3xl 원형) | 모든 AI 랜딩에 있는 장식. 분위기는 콘텐츠(요약 본문) 자체로 만든다 |

## 색상

### 액센트 / 인터랙션
| 용도 | 토큰 | 값 |
|------|------|------|
| 주 액션 (링크, pill CTA) | `colors.primary` | `#0066cc` (Action Blue) |
| 포커스 링 | `colors.primary-focus` | `#0071e3` |
| 다크 배경 위 인라인 링크 | `colors.primary-on-dark` | `#2997ff` |

### 배경 (서피스)
| 용도 | 토큰 | 값 |
|------|------|------|
| 페이지 캔버스 (기본) | `colors.canvas` | `#ffffff` |
| 부드러운 캔버스 (요약 리스트 영역, 푸터) | `colors.canvas-parchment` | `#f5f5f7` |
| 보조 버튼 fill | `colors.surface-pearl` | `#fafafc` |
| 어두운 강조 타일 (예: 영상 메타 헤더) | `colors.surface-tile-1` | `#272729` |
| 비디오 임베드/플레이어 프레임 | `colors.surface-black` | `#000000` |

### 텍스트
| 용도 | 토큰 | 값 |
|------|------|------|
| 본문·헤드라인 (라이트 위) | `colors.ink` | `#1d1d1f` |
| 본문 (다크 위) | `colors.body-on-dark` | `#ffffff` |
| 다크 위 보조 텍스트 | `colors.body-muted` | `#cccccc` |
| 펄 버튼 위 본문 | `colors.ink-muted-80` | `#333333` |
| 비활성/법적 미세 문구 | `colors.ink-muted-48` | `#7a7a7a` |

### 데이터·시맨틱
| 용도 | 값 |
|------|------|
| 성공 (요약 완료, 노션 동기화 OK) | `#22c55e` |
| 에러 (영상 분석 실패) | `#ef4444` |
| 중립/대기 | `#525252` |

### 헤어라인
| 용도 | 토큰 | 값 |
|------|------|------|
| 카드 1px 보더 | `colors.hairline` | `#e0e0e0` |
| 더 부드러운 디바이더 (rgba 적용) | `colors.divider-soft` | `rgba(0,0,0,0.04)` |

> **그라데이션 없음.** 색의 변화는 알터네이션 (white ↔ parchment ↔ near-black tile) 으로 표현하지 그라데이션으로 표현하지 않는다.

## 타이포그래피

### 폰트 스택
- **Display**: `"SF Pro Display", system-ui, -apple-system, sans-serif` (≥19px 헤드라인)
- **Body/UI**: `"SF Pro Text", system-ui, -apple-system, sans-serif` (<20px)
- **오픈소스 대체**: Inter (variable). display 사이즈에 `letter-spacing: -0.01em` 추가, body line-height 를 1.47 → 1.44 로 살짝 줄여 SF Pro tight 감각 재현.

### 위계
| 용도 | 사이즈 | weight | line-height | letter-spacing |
|------|--------|--------|-------------|----------------|
| 페이지 히어로 (예: 첫 진입 빈 상태) | 56px | 600 | 1.07 | -0.28px |
| 요약 카드 제목 / 상세 페이지 H1 | 40px | 600 | 1.10 | 0 |
| 섹션 헤드 | 34px | 600 | 1.47 | -0.374px |
| 요약 리드 한 줄 | 28px | 400 | 1.14 | 0.196px |
| 본문 단락 | 17px | 400 | 1.47 | -0.374px |
| 강한 인라인 강조 | 17px | 600 | 1.24 | -0.374px |
| 캡션 / 버튼 라벨 | 14px | 400 | 1.29 | -0.224px |
| 푸터 dense 링크 컬럼 | 17px | 400 | 2.41 | 0 |
| 글로벌 nav 링크 | 12px | 400 | 1.0 | -0.12px |

### 원칙
- **본문은 17px** (16px 아님). 한 픽셀 차이가 "읽는" 페이스를 만든다.
- **헤드라인 weight는 600** (700 아님). 더 강조가 필요한 21px 태그라인에서만 700.
- **weight 300은 의도적으로 희소**. 큰 CTA(18px/300)나 환경 페이지 lead-airy(24px/300)에만 등장.
- **display 사이즈는 negative letter-spacing**. 17px 이상은 모두 -0.12 ~ -0.374px tight. 12px 이하는 건드리지 않는다.

## 컴포넌트

### 버튼
```
Primary pill (가장 자주 쓰는 CTA: "요약하기", "노션으로 보내기")
  bg: colors.primary (#0066cc)
  text: 흰색, SF Pro Text 17px / 400 / -0.374px
  radius: 9999px (full pill — 브랜드 액션 시그널)
  padding: 11px 22px
  active: transform: scale(0.95)
  focus: 2px solid colors.primary-focus

Ghost pill (보조 CTA: "취소", "다시 시도")
  bg: transparent
  text: colors.primary
  border: 1px solid colors.primary
  radius: 9999px
  padding: 11px 22px

Dark utility (글로벌 nav: 설정, 로그아웃 등)
  bg: colors.ink (#1d1d1f)
  text: 흰색, 14px / 400 / -0.224px
  radius: 8px
  padding: 8px 15px

Text link (인라인): colors.primary
Text link on dark: colors.primary-on-dark (#2997ff)
```

### 입력 필드
```
검색/유튜브 URL 입력
  bg: colors.canvas (#ffffff)
  text: colors.ink, 17px
  border: 1px solid rgba(0,0,0,0.08)
  radius: 9999px (full pill — 검색도 액션 grammar)
  padding: 12px 20px
  height: 44px
  leading icon: 14px, 뮤트 톤
```

### 카드 (요약 리스트의 한 항목)
```
요약 카드 (store-utility-card 패턴)
  bg: colors.canvas (#ffffff)
  border: 1px solid colors.hairline (#e0e0e0)
  radius: 18px
  padding: 24px
  내부 썸네일(영상 캡처)은 1:1 crop, 내부 이미지 radius 8px
  본문: 17px / 600 제목, 17px / 400 본문 한 줄, 'Buy/Learn more' 자리에 'MD 열기 / 노션으로'
  shadow: 없음. 단, 영상 썸네일에는 시스템 single shadow 한 번만 (rgba(0,0,0,0.22) 3px 5px 30px)
```

### 풀블리드 타일 (선택)
```
요약 상세 페이지의 헤더 영역에 product-tile-dark 적용 가능
  bg: colors.surface-tile-1 (#272729)
  text: 흰색
  padding: 80px 0
  radius: 0 (풀블리드)
  영상 메타(제목, 채널, 길이)를 띄울 때만 사용. 일반 카드 영역에는 적용 금지.
```

## 레이아웃
- **최대 콘텐츠 너비**: 텍스트 중심 영역 ~980px (상세 페이지), 카드 그리드 ~1440px (리스트), 풀블리드 타일은 100vw.
- **정렬**: 좌측 정렬 기본. 풀블리드 히어로 타일은 중앙 정렬 허용.
- **그리드**: 요약 리스트는 3-col (≥1068px) → 2-col (834~1067px) → 1-col (<834px).
- **카드 간 거터**: 20–24px.
- **섹션 간 수직 패딩**: 풀블리드 타일은 80px, 일반 섹션은 48–64px.
- **베이스 단위**: 8px. 토큰: 4 / 8 / 12 / 17 / 24 / 32 / 48 / 80px.
- **여백 철학**: 요약 카드 헤드라인 위로 최소 64px 공기. 본문과 카드 영상 썸네일 사이 최소 40px.

### 브레이크포인트
- ≤419 (small phone) / 420–640 (phone) / 641–833 (tablet portrait) / 834–1067 (tablet landscape) / 1068–1440 (desktop) / ≥1441 (wide, 1440 lock).
- 834px 이하에서 글로벌 nav는 햄버거로 collapse.
- 터치 타깃 최소 44 × 44px.

## 모양 (Radius)
| 토큰 | 값 | 용도 |
|------|------|------|
| `rounded.none` | 0 | 풀블리드 타일 |
| `rounded.sm` | 8px | 컴팩트 유틸 버튼, 카드 내부 이미지 |
| `rounded.md` | 11px | Pearl 보조 버튼 |
| `rounded.lg` | 18px | 요약 카드, 그리드 카드 |
| `rounded.pill` | 9999px | Primary CTA, 검색 인풋, 옵션 chip |
| `rounded.full` | 50% | 사진 위 떠있는 원형 컨트롤 |

> radius 문법을 섞지 마라. 카드는 18px, 액션 버튼은 9999px, 유틸 버튼은 8px. 그 중간(예: rounded-2xl 16px) 을 새로 만들지 않는다.

## Elevation & Shadow
| 레벨 | 처리 |
|------|------|
| Flat | 그림자·보더 없음 (풀블리드 타일, nav, 푸터, 본문 섹션) |
| Hairline | `1px solid colors.hairline` (요약 카드, 디바이더) |
| Backdrop blur | 스티키 바에서만 `backdrop-filter: blur(20px) saturate(180%)`, 배경 색 80% alpha |
| Product shadow | `rgba(0,0,0,0.22) 3px 5px 30px 0` — 영상 썸네일 한정. 카드·버튼·텍스트에는 절대 사용 금지 |

## 애니메이션
- **active/press**: 모든 버튼 `transform: scale(0.95)` 시스템 마이크로 인터랙션. 이게 기본이고 다른 press 효과는 금지.
- **fade-in (0.4s)** — 요약 카드 등장.
- **slide-up (0.5s)** — 진행 단계가 추가될 때.
- 위 3개 외 모든 애니메이션 금지. hover 효과는 문서화하지 않는다 (디폴트 / active 두 상태만).

## 아이콘
- SVG 인라인, strokeWidth 1.5.
- 색은 `colors.ink` 또는 `colors.body-on-dark`. 액세서리 컬러 금지.
- 아이콘 컨테이너(둥근 배경 박스)로 감싸지 않는다. 단, 사진 위 떠있는 컨트롤은 예외 — 44×44 원형 chip(`colors.surface-chip-translucent` 64% alpha)에 담는다.

## Do's
- `colors.primary` 단일로 모든 인터랙션 표현.
- 헤드라인은 SF Pro Display 600 + negative letter-spacing.
- 본문은 SF Pro Text 17px / 400 / 1.47 / -0.374px.
- 단일 product shadow는 영상 썸네일에만.
- 모든 버튼 press 상태는 `scale(0.95)`.

## Don'ts
- 두 번째 액센트 컬러 도입 금지.
- 카드·버튼·텍스트에 그림자 추가 금지.
- 그라데이션 배경 금지 (분위기는 콘텐츠로).
- weight 500 본문 금지.
- 풀블리드 타일에 radius 추가 금지.
- body line-height 1.47 미만 금지.
- radius 문법 혼용 금지 (`rounded.sm` / `rounded.lg` / `rounded.pill` 외 새 값 생성 금지).
