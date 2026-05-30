/**
 * ITSM 티켓 칸반 보드(phase 16 step 4)의 순수 로직.
 * 컴포넌트(DynamicKanban.vue)와 단위 테스트가 공유한다 — DOM 없이(`node` 환경) 검증 가능.
 *
 * 상태 전이는 백엔드의 changeStatus 가 단독 판정한다(금지사항: 프론트에서 전이 매트릭스 강제 금지).
 * 본 모듈은 표시·그룹핑·잔여시간 계산만 담당한다.
 */
import type { TicketStatus, TicketSummary } from '@/types/ticket';

export interface KanbanColumnDef {
  status: TicketStatus;
  label: string;
}

/** 보드 컬럼 4종 — 좌→우 진행 순서 (라벨은 badges.ts 와 동일 어휘). */
export const KANBAN_COLUMNS: readonly KanbanColumnDef[] = [
  { status: 'OPEN', label: '접수' },
  { status: 'IN_PROGRESS', label: '진행 중' },
  { status: 'RESOLVED', label: '해결됨' },
  { status: 'CLOSED', label: '종료' },
] as const;

/** 빈 컬럼 맵(모든 상태 키 포함). */
function emptyColumns(): Record<TicketStatus, TicketSummary[]> {
  return { OPEN: [], IN_PROGRESS: [], RESOLVED: [], CLOSED: [] };
}

/**
 * 티켓 배열을 상태별로 그룹핑한다. 정의되지 않은 상태는 어떤 컬럼에도 넣지 않는다(무시).
 * 입력 순서를 보존한다(목록 정렬 = 생성일 내림차순 그대로).
 */
export function groupByStatus(
  tickets: TicketSummary[],
): Record<TicketStatus, TicketSummary[]> {
  const out = emptyColumns();
  for (const t of tickets) {
    const bucket = out[t.status];
    if (bucket) bucket.push(t);
  }
  return out;
}

/** 다음 상태(키보드 Alt+→ 이동용). 마지막(CLOSED)이면 null. */
export function nextStatus(status: TicketStatus): TicketStatus | null {
  const idx = KANBAN_COLUMNS.findIndex((c) => c.status === status);
  const next = idx >= 0 ? KANBAN_COLUMNS[idx + 1] : undefined;
  return next ? next.status : null;
}

/**
 * 담당자 ID → 아바타 이니셜(최대 2글자, 대문자). 빈 값은 '?'.
 * 'assignee-sample-1' → 'AS', 'admin' → 'AD'.
 */
export function initials(name: string | null | undefined): string {
  const s = (name ?? '').trim();
  if (!s) return '?';
  const tokens = s.split(/[\s_\-.]+/).filter(Boolean);
  const first = tokens[0] ?? s;
  const second = tokens[1];
  if (second) {
    return (first.charAt(0) + second.charAt(0)).toUpperCase();
  }
  return first.slice(0, 2).toUpperCase();
}

export interface SlaRemaining {
  label: string;
  overdue: boolean;
}

/**
 * SLA 마감 시각 → 잔여/초과 표기 (nowMs 주입으로 순수·테스트 가능).
 * null/파싱불가 시 null 을 반환해 카드에서 표시를 생략한다.
 */
export function slaRemaining(
  iso: string | null | undefined,
  nowMs: number,
): SlaRemaining | null {
  if (!iso) return null;
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return null;
  const diffMs = t - nowMs;
  const overdue = diffMs < 0;
  const absMin = Math.floor(Math.abs(diffMs) / 60_000);
  const h = Math.floor(absMin / 60);
  const m = absMin % 60;
  const span = h > 0 ? `${h}시간 ${m}분` : `${m}분`;
  return { label: overdue ? `${span} 초과` : `${span} 남음`, overdue };
}
