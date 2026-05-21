import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import BetterSqlite3 from 'better-sqlite3';

let testDb: BetterSqlite3.Database;

vi.mock('@/lib/db', async () => {
  const actual =
    await vi.importActual<typeof import('@/lib/db')>('@/lib/db');
  return {
    ...actual,
    getDb: vi.fn(() => testDb),
  };
});

import { migrate, insertSummary } from '@/lib/db';
import { GET } from './route';

const VALID_URL = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';

function makeRequest(id: string): {
  request: Request;
  context: { params: Promise<{ id: string }> };
} {
  return {
    request: new Request(`http://localhost/api/summaries/${id}`),
    context: { params: Promise.resolve({ id }) },
  };
}

describe('GET /api/summaries/[id]', () => {
  let tmpDir: string;

  beforeEach(() => {
    testDb = new BetterSqlite3(':memory:');
    migrate(testDb);
    tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'tubenote-route-id-'));
  });

  afterEach(() => {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  });

  it('404: 존재하지 않는 id', async () => {
    const { request, context } = makeRequest('does-not-exist');
    const res = await GET(request, context);
    expect(res.status).toBe(404);
    const json = await res.json();
    expect(json.error).toBe('NOT_FOUND');
  });

  it('200: 존재하는 id + bodyMarkdown (frontmatter 제거)', async () => {
    const id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
    const mdPath = path.join(tmpDir, `${id}.md`);
    const document =
      '---\n' +
      'title: Sample Title\n' +
      'channel: Sample Channel\n' +
      `url: '${VALID_URL}'\n` +
      'duration: 600\n' +
      'one_liner: A one-line summary.\n' +
      '---\n\n' +
      '## 본문\n\n핵심 내용입니다.\n';
    fs.writeFileSync(mdPath, document, 'utf8');

    insertSummary(
      testDb,
      {
        url: VALID_URL,
        title: 'Sample Title',
        channel: 'Sample Channel',
        duration: 600,
        oneLiner: 'A one-line summary.',
        mdPath,
      },
      { id, createdAt: '2026-05-21T00:00:00.000Z' },
    );

    const { request, context } = makeRequest(id);
    const res = await GET(request, context);
    expect(res.status).toBe(200);
    const json = await res.json();
    expect(json.id).toBe(id);
    expect(json.title).toBe('Sample Title');
    expect(json.mdPath).toBe(mdPath);
    expect(json.bodyMarkdown).toContain('## 본문');
    expect(json.bodyMarkdown).toContain('핵심 내용입니다.');
    expect(json.bodyMarkdown).not.toContain('title:');
    expect(json.bodyMarkdown).not.toContain('---');
  });

  it('500 MD_MISSING: 파일이 사라진 경우', async () => {
    const id = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
    insertSummary(
      testDb,
      {
        url: VALID_URL,
        title: 'Lost',
        channel: 'C',
        duration: 0,
        oneLiner: 'L',
        mdPath: path.join(tmpDir, 'gone.md'),
      },
      { id, createdAt: '2026-05-21T00:00:00.000Z' },
    );

    const { request, context } = makeRequest(id);
    const res = await GET(request, context);
    expect(res.status).toBe(500);
    const json = await res.json();
    expect(json.error).toBe('MD_MISSING');
  });
});
