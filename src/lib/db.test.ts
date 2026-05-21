import { describe, it, expect, beforeEach } from 'vitest';
import {
  getDb,
  migrate,
  insertSummary,
  getSummary,
  listSummaries,
  deleteSummary,
  type Database,
} from './db';
import type { NewSummary } from '@/types/summary';

const UUID_V4_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

function fixture(overrides: Partial<NewSummary> = {}): NewSummary {
  return {
    url: 'https://www.youtube.com/watch?v=abc123',
    title: 'Sample Title',
    channel: 'Sample Channel',
    duration: 600,
    oneLiner: 'A one-line summary.',
    mdPath: 'output/sample.md',
    ...overrides,
  };
}

describe('db', () => {
  let db: Database;

  beforeEach(() => {
    db = getDb(':memory:');
    migrate(db);
  });

  it('migrate is idempotent', () => {
    expect(() => {
      migrate(db);
      migrate(db);
    }).not.toThrow();
  });

  it('insertSummary returns input + auto-generated id and createdAt', () => {
    const input = fixture();
    const out = insertSummary(db, input);
    expect(out.id).toMatch(UUID_V4_RE);
    expect(out.createdAt).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/);
    expect(out.url).toBe(input.url);
    expect(out.title).toBe(input.title);
    expect(out.channel).toBe(input.channel);
    expect(out.duration).toBe(input.duration);
    expect(out.oneLiner).toBe(input.oneLiner);
    expect(out.mdPath).toBe(input.mdPath);
  });

  it('getSummary returns the inserted row by id', () => {
    const inserted = insertSummary(db, fixture());
    const fetched = getSummary(db, inserted.id);
    expect(fetched).toEqual(inserted);
  });

  it('getSummary returns null for unknown id', () => {
    expect(getSummary(db, 'no-such-id')).toBeNull();
  });

  it('listSummaries orders by createdAt DESC', () => {
    const a = insertSummary(db, fixture({ title: 'A' }));
    const b = insertSummary(db, fixture({ title: 'B' }));
    const c = insertSummary(db, fixture({ title: 'C' }));
    db.prepare('UPDATE summaries SET created_at = ? WHERE id = ?').run(
      '2026-01-01T00:00:00.000Z',
      a.id,
    );
    db.prepare('UPDATE summaries SET created_at = ? WHERE id = ?').run(
      '2026-03-01T00:00:00.000Z',
      b.id,
    );
    db.prepare('UPDATE summaries SET created_at = ? WHERE id = ?').run(
      '2026-02-01T00:00:00.000Z',
      c.id,
    );
    const rows = listSummaries(db);
    expect(rows.map((r) => r.title)).toEqual(['B', 'C', 'A']);
  });

  it('listSummaries respects limit', () => {
    insertSummary(db, fixture({ title: 'A' }));
    insertSummary(db, fixture({ title: 'B' }));
    insertSummary(db, fixture({ title: 'C' }));
    const rows = listSummaries(db, { limit: 2 });
    expect(rows).toHaveLength(2);
  });

  it('deleteSummary returns 1 when deleting, 0 when missing, and getSummary returns null after', () => {
    const inserted = insertSummary(db, fixture());
    expect(deleteSummary(db, inserted.id)).toBe(1);
    expect(getSummary(db, inserted.id)).toBeNull();
    expect(deleteSummary(db, inserted.id)).toBe(0);
  });

  it('generated id matches UUID v4 pattern', () => {
    const out = insertSummary(db, fixture());
    expect(out.id).toMatch(UUID_V4_RE);
  });
});
