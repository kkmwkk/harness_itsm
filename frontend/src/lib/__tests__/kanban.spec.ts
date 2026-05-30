import { describe, it, expect } from 'vitest';
import {
  KANBAN_COLUMNS,
  groupByStatus,
  nextStatus,
  initials,
  slaRemaining,
} from '@/lib/kanban';
import type { TicketSummary, TicketStatus } from '@/types/ticket';

function ticket(id: number, status: TicketStatus, over: Partial<TicketSummary> = {}): TicketSummary {
  return {
    id,
    ticketNo: `ITSM-${String(id).padStart(5, '0')}`,
    title: `티켓 ${id}`,
    priority: 'MEDIUM',
    status,
    assigneeId: null,
    createdAt: '2026-05-30T10:00:00',
    ...over,
  };
}

describe('KANBAN_COLUMNS', () => {
  it('상태 4종을 진행 순서대로 정의한다', () => {
    expect(KANBAN_COLUMNS.map((c) => c.status)).toEqual([
      'OPEN',
      'IN_PROGRESS',
      'RESOLVED',
      'CLOSED',
    ]);
  });
});

describe('groupByStatus', () => {
  it('상태별로 그룹핑하고 모든 컬럼 키를 포함한다', () => {
    const grouped = groupByStatus([
      ticket(1, 'OPEN'),
      ticket(2, 'IN_PROGRESS'),
      ticket(3, 'OPEN'),
      ticket(4, 'CLOSED'),
    ]);
    expect(grouped.OPEN.map((t) => t.id)).toEqual([1, 3]);
    expect(grouped.IN_PROGRESS.map((t) => t.id)).toEqual([2]);
    expect(grouped.RESOLVED).toEqual([]);
    expect(grouped.CLOSED.map((t) => t.id)).toEqual([4]);
  });

  it('정의되지 않은 상태는 무시한다', () => {
    const grouped = groupByStatus([
      ticket(1, 'OPEN'),
      ticket(2, 'WEIRD' as TicketStatus),
    ]);
    expect(grouped.OPEN.map((t) => t.id)).toEqual([1]);
    const total =
      grouped.OPEN.length +
      grouped.IN_PROGRESS.length +
      grouped.RESOLVED.length +
      grouped.CLOSED.length;
    expect(total).toBe(1);
  });

  it('빈 입력은 빈 컬럼 맵을 반환한다', () => {
    const grouped = groupByStatus([]);
    expect(grouped.OPEN).toEqual([]);
    expect(grouped.CLOSED).toEqual([]);
  });
});

describe('nextStatus', () => {
  it('진행 순서의 다음 상태를 반환한다', () => {
    expect(nextStatus('OPEN')).toBe('IN_PROGRESS');
    expect(nextStatus('IN_PROGRESS')).toBe('RESOLVED');
    expect(nextStatus('RESOLVED')).toBe('CLOSED');
  });
  it('마지막 상태(CLOSED)는 null', () => {
    expect(nextStatus('CLOSED')).toBeNull();
  });
});

describe('initials', () => {
  it('구분자로 분리된 토큰의 첫 글자 2개를 대문자로', () => {
    expect(initials('assignee-sample-1')).toBe('AS');
    expect(initials('hong gildong')).toBe('HG');
  });
  it('단일 토큰은 앞 2글자', () => {
    expect(initials('admin')).toBe('AD');
  });
  it('빈 값은 물음표', () => {
    expect(initials('')).toBe('?');
    expect(initials(null)).toBe('?');
    expect(initials(undefined)).toBe('?');
  });
});

describe('slaRemaining', () => {
  const now = Date.parse('2026-05-30T12:00:00');
  it('미래 마감은 남은 시간', () => {
    const r = slaRemaining('2026-05-30T14:30:00', now);
    expect(r).toEqual({ label: '2시간 30분 남음', overdue: false });
  });
  it('과거 마감은 초과', () => {
    const r = slaRemaining('2026-05-30T11:15:00', now);
    expect(r).toEqual({ label: '45분 초과', overdue: true });
  });
  it('null·파싱불가는 null', () => {
    expect(slaRemaining(null, now)).toBeNull();
    expect(slaRemaining(undefined, now)).toBeNull();
    expect(slaRemaining('not-a-date', now)).toBeNull();
  });
});
