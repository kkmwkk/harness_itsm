/**
 * 상태/우선순위 뱃지의 라벨·색 매핑 (UI_GUIDE §5-5).
 * 컴포넌트(StatusBadge·PriorityBadge)와 단위 테스트가 공유한다 — 매핑 로직만 분리해
 * DOM 없이(`node` 환경) 검증할 수 있게 한다.
 *
 * 시맨틱 색은 상태/우선순위 표현 전용 (UI_GUIDE §3-5). pill 규격은 컴포넌트가 책임진다.
 */

export interface BadgeSpec {
  label: string;
  /** Tailwind text 색 유틸 (시맨틱 토큰) */
  color: string;
  /** Tailwind 배경 유틸 (시맨틱 토큰 10% alpha 또는 중립 서피스) */
  bg: string;
}

/** metaStatus 4종(UI_GUIDE §5-5) + ITSM 티켓 상태 4종 */
const STATUS_MAP: Record<string, BadgeSpec> = {
  DRAFT: { label: '작성 중', color: 'text-warning', bg: 'bg-warning/10' },
  PUBLISHED: { label: '배포 중', color: 'text-success', bg: 'bg-success/10' },
  DEPRECATED: { label: '구버전', color: 'text-neutral', bg: 'bg-neutral/10' },
  ARCHIVED: { label: '보관', color: 'text-foreground-subtle', bg: 'bg-surface-muted' },
  OPEN: { label: '접수', color: 'text-info', bg: 'bg-info/10' },
  IN_PROGRESS: { label: '진행 중', color: 'text-warning', bg: 'bg-warning/10' },
  RESOLVED: { label: '해결됨', color: 'text-success', bg: 'bg-success/10' },
  CLOSED: { label: '종료', color: 'text-neutral', bg: 'bg-neutral/10' },
};

/** 티켓 우선순위 — 무채색 4단계, CRITICAL 만 danger (UI_GUIDE §5-5) */
const PRIORITY_MAP: Record<string, BadgeSpec> = {
  LOW: { label: '낮음', color: 'text-foreground-subtle', bg: 'bg-surface-muted' },
  MEDIUM: { label: '보통', color: 'text-foreground-muted', bg: 'bg-surface-muted' },
  HIGH: { label: '높음', color: 'text-foreground', bg: 'bg-surface-hover' },
  CRITICAL: { label: '긴급', color: 'text-danger', bg: 'bg-danger/10' },
};

/** 정의되지 않은 값의 fallback — 중립색 + 원본 value(없으면 '-') */
function fallback(value: string | null | undefined): BadgeSpec {
  const v = value ?? '';
  return { label: v || '-', color: 'text-foreground-muted', bg: 'bg-surface-muted' };
}

export function statusSpec(value: string | null | undefined): BadgeSpec {
  return (value != null && STATUS_MAP[value]) || fallback(value);
}

export function prioritySpec(value: string | null | undefined): BadgeSpec {
  return (value != null && PRIORITY_MAP[value]) || fallback(value);
}
