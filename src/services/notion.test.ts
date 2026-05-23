import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import BetterSqlite3 from 'better-sqlite3';

vi.mock('@/lib/notion', async () => {
  const actual =
    await vi.importActual<typeof import('@/lib/notion')>('@/lib/notion');
  return {
    ...actual,
    getNotionClient: vi.fn(),
  };
});

vi.mock('@/lib/db', async () => {
  const actual = await vi.importActual<typeof import('@/lib/db')>('@/lib/db');
  return {
    ...actual,
    getDb: vi.fn(),
  };
});

import {
  getDb,
  migrate,
  insertSummary,
  type Database,
} from '@/lib/db';
import { getNotionClient } from '@/lib/notion';
import {
  NotFoundError,
  syncSummaryToNotion,
} from './notion';

const mockedGetDb = vi.mocked(getDb);
const mockedGetNotionClient = vi.mocked(getNotionClient);

interface FakeNotionClient {
  databases: { retrieve: ReturnType<typeof vi.fn> };
  dataSources: { query: ReturnType<typeof vi.fn> };
  pages: { create: ReturnType<typeof vi.fn> };
}

function makeFakeNotion(): FakeNotionClient {
  return {
    databases: { retrieve: vi.fn() },
    dataSources: { query: vi.fn() },
    pages: { create: vi.fn() },
  };
}

const ORIGINAL_ENV = process.env;

describe('syncSummaryToNotion', () => {
  let memDb: Database;
  let tmpDir: string;
  let notion: FakeNotionClient;

  beforeEach(() => {
    process.env = { ...ORIGINAL_ENV, NOTION_DATABASE_ID: 'db-123' };

    memDb = new BetterSqlite3(':memory:') as unknown as Database;
    migrate(memDb);
    mockedGetDb.mockReset();
    mockedGetDb.mockReturnValue(memDb);

    tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'tubenote-notion-'));
    notion = makeFakeNotion();
    mockedGetNotionClient.mockReset();
    mockedGetNotionClient.mockReturnValue(
      notion as unknown as ReturnType<typeof getNotionClient>,
    );
  });

  afterEach(() => {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    process.env = ORIGINAL_ENV;
  });

  function seedSummary(
    id = '11111111-1111-4111-8111-111111111111',
    body = '## 본문\n\n핵심 내용.',
  ) {
    const mdPath = path.join(tmpDir, `${id}.md`);
    const document =
      '---\n' +
      'title: Sample Title\n' +
      'channel: Sample Channel\n' +
      'url: https://www.youtube.com/watch?v=abcdefghijk\n' +
      'duration: 600\n' +
      "one_liner: A one-line summary.\n" +
      '---\n\n' +
      body;
    fs.writeFileSync(mdPath, document, 'utf8');
    insertSummary(
      memDb,
      {
        url: 'https://www.youtube.com/watch?v=abcdefghijk',
        title: 'Sample Title',
        channel: 'Sample Channel',
        duration: 600,
        oneLiner: 'A one-line summary.',
        mdPath,
      },
      { id, createdAt: '2026-05-21T00:00:00.000Z' },
    );
    return { id, mdPath };
  }

  function mockDataSourceResolution() {
    notion.databases.retrieve.mockResolvedValueOnce({
      object: 'database',
      id: 'db-123',
      data_sources: [{ id: 'ds-1', name: 'Default' }],
    });
  }

  it('creates a new Notion page with the expected properties', async () => {
    const { id } = seedSummary();
    mockDataSourceResolution();
    notion.dataSources.query.mockResolvedValueOnce({ results: [] });
    notion.pages.create.mockResolvedValueOnce({
      id: 'page-1',
      url: 'https://www.notion.so/Sample-Title-page-1',
    });

    const result = await syncSummaryToNotion(id);

    expect(result).toEqual({
      pageId: 'page-1',
      pageUrl: 'https://www.notion.so/Sample-Title-page-1',
    });
    expect(notion.databases.retrieve).toHaveBeenCalledWith({
      database_id: 'db-123',
    });
    expect(notion.dataSources.query).toHaveBeenCalledWith(
      expect.objectContaining({
        data_source_id: 'ds-1',
        filter: {
          property: 'TubenoteId',
          rich_text: { equals: id },
        },
      }),
    );
    expect(notion.pages.create).toHaveBeenCalledTimes(1);
    const call = notion.pages.create.mock.calls[0][0];
    expect(call.parent).toEqual({ database_id: 'db-123' });
    expect(call.properties.Title.title[0].text.content).toBe('Sample Title');
    expect(call.properties.URL.url).toBe(
      'https://www.youtube.com/watch?v=abcdefghijk',
    );
    expect(call.properties.OneLiner.rich_text[0].text.content).toBe(
      'A one-line summary.',
    );
    expect(call.properties.Channel.rich_text[0].text.content).toBe(
      'Sample Channel',
    );
    expect(call.properties.TubenoteId.rich_text[0].text.content).toBe(id);
    expect(Array.isArray(call.children)).toBe(true);
    expect(call.children.length).toBeGreaterThan(0);
  });

  it('is idempotent: returns existing page without creating a new one', async () => {
    const { id } = seedSummary();
    mockDataSourceResolution();
    notion.dataSources.query.mockResolvedValueOnce({
      results: [
        {
          id: 'page-existing',
          url: 'https://www.notion.so/Existing-page-existing',
        },
      ],
    });

    const result = await syncSummaryToNotion(id);

    expect(result).toEqual({
      pageId: 'page-existing',
      pageUrl: 'https://www.notion.so/Existing-page-existing',
    });
    expect(notion.pages.create).not.toHaveBeenCalled();
  });

  it('throws NotFoundError when the summary id does not exist', async () => {
    await expect(syncSummaryToNotion('does-not-exist')).rejects.toBeInstanceOf(
      NotFoundError,
    );
    expect(notion.databases.retrieve).not.toHaveBeenCalled();
    expect(notion.dataSources.query).not.toHaveBeenCalled();
    expect(notion.pages.create).not.toHaveBeenCalled();
  });
});
