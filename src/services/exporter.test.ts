import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { getDb, migrate, listSummaries, type Database } from '@/lib/db';
import { persistSummary } from './exporter';
import type { SummarizerOutput } from './summarizer';

const UUID_V4_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

function fixtureOutput(): SummarizerOutput {
  return {
    meta: {
      title: 'Sample Title',
      channel: 'Sample Channel',
      url: 'https://www.youtube.com/watch?v=abcdefghijk',
      duration: 600,
      oneLiner: 'A one-line summary.',
    },
    body: '## 본문\n\n핵심 내용입니다.',
  };
}

describe('persistSummary', () => {
  let db: Database;
  let tmpDir: string;

  beforeEach(() => {
    db = getDb(':memory:');
    migrate(db);
    tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'tubenote-exp-'));
  });

  afterEach(() => {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  });

  it('inserts DB row, writes markdown file, returns Summary with matching mdPath', async () => {
    const out = await persistSummary(db, fixtureOutput(), { outputDir: tmpDir });

    expect(out.id).toMatch(UUID_V4_RE);
    expect(out.mdPath).toBe(path.resolve(tmpDir, `${out.id}.md`));
    expect(fs.existsSync(out.mdPath)).toBe(true);

    const rows = listSummaries(db);
    expect(rows).toHaveLength(1);
    expect(rows[0].id).toBe(out.id);
    expect(rows[0].mdPath).toBe(out.mdPath);
    expect(rows[0].title).toBe('Sample Title');

    const content = fs.readFileSync(out.mdPath, 'utf8');
    expect(content).toMatch(/^---\n[\s\S]*\n---\n\n/);
    expect(content).toContain('title: Sample Title');
    expect(content).toContain('channel: Sample Channel');
    expect(content).toMatch(
      /url:\s*['"]?https:\/\/www\.youtube\.com\/watch\?v=abcdefghijk['"]?/,
    );
    expect(content).toContain('duration: 600');
    expect(content).toContain('one_liner: A one-line summary.');
    expect(content).toContain('## 본문');
  });

  it('rolls back DB row when file write fails', async () => {
    const forbiddenDir = '/proc/forbidden/tubenote';
    await expect(
      persistSummary(db, fixtureOutput(), { outputDir: forbiddenDir }),
    ).rejects.toThrow();

    const rows = listSummaries(db);
    expect(rows).toHaveLength(0);
  });
});
