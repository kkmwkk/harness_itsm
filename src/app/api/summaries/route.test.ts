import { describe, it, expect, vi, beforeEach } from 'vitest';
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

function makeRequest(query = ''): Request {
  return new Request(`http://localhost/api/summaries${query}`);
}

describe('GET /api/summaries', () => {
  beforeEach(() => {
    testDb = new BetterSqlite3(':memory:');
    migrate(testDb);
  });

  it('200: 빈 배열', async () => {
    const res = await GET(makeRequest());
    expect(res.status).toBe(200);
    const json = await res.json();
    expect(json).toEqual([]);
  });

  it('200: 입력된 요약 목록 반환 (최신순)', async () => {
    insertSummary(
      testDb,
      {
        url: VALID_URL,
        title: 'Older',
        channel: 'C',
        duration: 60,
        oneLiner: 'L',
        mdPath: '/tmp/old.md',
      },
      { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', createdAt: '2026-01-01T00:00:00.000Z' },
    );
    insertSummary(
      testDb,
      {
        url: VALID_URL,
        title: 'Newer',
        channel: 'C',
        duration: 90,
        oneLiner: 'L',
        mdPath: '/tmp/new.md',
      },
      { id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', createdAt: '2026-05-01T00:00:00.000Z' },
    );

    const res = await GET(makeRequest());
    expect(res.status).toBe(200);
    const json = await res.json();
    expect(json).toHaveLength(2);
    expect(json[0].title).toBe('Newer');
    expect(json[1].title).toBe('Older');
  });

  it('400: limit=999 (상한 초과)', async () => {
    const res = await GET(makeRequest('?limit=999'));
    expect(res.status).toBe(400);
    const json = await res.json();
    expect(json.error).toBe('INVALID_PARAM');
  });

  it('400: limit=-1 (음수)', async () => {
    const res = await GET(makeRequest('?limit=-1'));
    expect(res.status).toBe(400);
  });

  it('400: limit=abc (문자열)', async () => {
    const res = await GET(makeRequest('?limit=abc'));
    expect(res.status).toBe(400);
  });

  it('400: offset=-5', async () => {
    const res = await GET(makeRequest('?offset=-5'));
    expect(res.status).toBe(400);
  });
});
