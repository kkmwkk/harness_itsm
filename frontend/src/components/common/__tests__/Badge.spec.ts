import { describe, it, expect } from 'vitest';
import { statusSpec, prioritySpec } from '@/lib/badges';

describe('statusSpec', () => {
  it('StatusBadge_DRAFT_은_작성_중_라벨', () => {
    const s = statusSpec('DRAFT');
    expect(s.label).toBe('작성 중');
    expect(s.color).toBe('text-warning');
    expect(s.bg).toBe('bg-warning/10');
  });

  it('StatusBadge_undefined_은_-_라벨_fallback', () => {
    const s = statusSpec(undefined);
    expect(s.label).toBe('-');
    expect(s.color).toBe('text-foreground-muted');
  });

  it('StatusBadge_CLOSED_은_종료_라벨', () => {
    expect(statusSpec('CLOSED').label).toBe('종료');
  });

  it('StatusBadge_정의되지_않은_값은_원본_value_fallback', () => {
    const s = statusSpec('CUSTOM');
    expect(s.label).toBe('CUSTOM');
    expect(s.color).toBe('text-foreground-muted');
  });
});

describe('prioritySpec', () => {
  it('PriorityBadge_CRITICAL_은_긴급_라벨_과_danger_클래스', () => {
    const s = prioritySpec('CRITICAL');
    expect(s.label).toBe('긴급');
    expect(s.color).toBe('text-danger');
    expect(s.bg).toBe('bg-danger/10');
  });

  it('PriorityBadge_LOW_은_낮음_라벨_무채색', () => {
    const s = prioritySpec('LOW');
    expect(s.label).toBe('낮음');
    expect(s.color).toBe('text-foreground-subtle');
  });

  it('PriorityBadge_HIGH_은_무채색_danger_아님', () => {
    const s = prioritySpec('HIGH');
    expect(s.label).toBe('높음');
    expect(s.color).not.toContain('danger');
  });
});
