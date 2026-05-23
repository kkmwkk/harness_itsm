import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/services/notion', async () => {
  const actual =
    await vi.importActual<typeof import('@/services/notion')>(
      '@/services/notion',
    );
  return {
    ...actual,
    syncSummaryToNotion: vi.fn(),
  };
});

vi.mock('@/lib/notion', async () => {
  const actual =
    await vi.importActual<typeof import('@/lib/notion')>('@/lib/notion');
  return actual;
});

import {
  syncSummaryToNotion,
  NotFoundError,
  NotionSyncError,
} from '@/services/notion';
import { MissingNotionTokenError } from '@/lib/notion';
import { POST } from './route';

const mockedSync = vi.mocked(syncSummaryToNotion);

function makeRequest(body?: unknown): Request {
  const init: RequestInit = { method: 'POST' };
  if (body !== undefined) {
    init.headers = { 'Content-Type': 'application/json' };
    init.body = typeof body === 'string' ? body : JSON.stringify(body);
  }
  return new Request('http://localhost/api/notion', init);
}

describe('POST /api/notion', () => {
  beforeEach(() => {
    mockedSync.mockReset();
  });

  it('200: returns NotionSyncResult on success', async () => {
    mockedSync.mockResolvedValueOnce({
      pageId: 'page-1',
      pageUrl: 'https://www.notion.so/Sample-Title-page-1',
    });

    const res = await POST(makeRequest({ summaryId: 's-1' }));

    expect(res.status).toBe(200);
    const json = await res.json();
    expect(json).toEqual({
      pageId: 'page-1',
      pageUrl: 'https://www.notion.so/Sample-Title-page-1',
    });
    expect(mockedSync).toHaveBeenCalledWith('s-1');
  });

  it('400: missing summaryId', async () => {
    const res = await POST(makeRequest({}));
    expect(res.status).toBe(400);
    const json = await res.json();
    expect(json.error).toBe('INVALID_BODY');
    expect(mockedSync).not.toHaveBeenCalled();
  });

  it('400: missing body', async () => {
    const res = await POST(makeRequest());
    expect(res.status).toBe(400);
    const json = await res.json();
    expect(json.error).toBe('INVALID_BODY');
    expect(mockedSync).not.toHaveBeenCalled();
  });

  it('404: NotFoundError', async () => {
    mockedSync.mockRejectedValueOnce(new NotFoundError('no'));
    const res = await POST(makeRequest({ summaryId: 's-missing' }));
    expect(res.status).toBe(404);
    const json = await res.json();
    expect(json.error).toBe('NOT_FOUND');
  });

  it('500: NotionSyncError', async () => {
    mockedSync.mockRejectedValueOnce(new NotionSyncError('api blew up'));
    const res = await POST(makeRequest({ summaryId: 's-1' }));
    expect(res.status).toBe(500);
    const json = await res.json();
    expect(json.error).toBe('NOTION_FAILED');
    expect(json.message).toContain('api blew up');
  });

  it('503: MissingNotionTokenError', async () => {
    mockedSync.mockRejectedValueOnce(new MissingNotionTokenError());
    const res = await POST(makeRequest({ summaryId: 's-1' }));
    expect(res.status).toBe(503);
    const json = await res.json();
    expect(json.error).toBe('NOTION_NOT_CONFIGURED');
  });
});
