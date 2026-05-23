import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { Summary } from '@/types/summary';
import type { SummarizerOutput } from '@/services/summarizer';

vi.mock('@/services/summarizer', async () => {
  const actual =
    await vi.importActual<typeof import('@/services/summarizer')>(
      '@/services/summarizer',
    );
  return {
    ...actual,
    summarize: vi.fn(),
  };
});

vi.mock('@/services/exporter', () => ({
  persistSummary: vi.fn(),
}));

vi.mock('@/lib/db', () => ({
  getDb: vi.fn(() => ({})),
}));

import { summarize, InvalidUrlError } from '@/services/summarizer';
import { persistSummary } from '@/services/exporter';
import { POST } from './route';

const mockedSummarize = vi.mocked(summarize);
const mockedPersist = vi.mocked(persistSummary);

const VALID_URL = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';

const fixtureOutput: SummarizerOutput = {
  meta: {
    title: 'Sample Title',
    channel: 'Sample Channel',
    url: VALID_URL,
    duration: 600,
    oneLiner: 'A one-line summary.',
  },
  body: '## 본문\n\n핵심.',
};

const fixtureSummary: Summary = {
  id: '11111111-1111-4111-8111-111111111111',
  url: VALID_URL,
  title: 'Sample Title',
  channel: 'Sample Channel',
  duration: 600,
  oneLiner: 'A one-line summary.',
  mdPath: '/tmp/output/11111111-1111-4111-8111-111111111111.md',
  createdAt: '2026-05-21T00:00:00.000Z',
};

function makeRequest(body?: unknown): Request {
  const init: RequestInit = { method: 'POST' };
  if (body !== undefined) {
    init.headers = { 'Content-Type': 'application/json' };
    init.body = typeof body === 'string' ? body : JSON.stringify(body);
  }
  return new Request('http://localhost/api/summarize', init);
}

describe('POST /api/summarize', () => {
  beforeEach(() => {
    mockedSummarize.mockReset();
    mockedPersist.mockReset();
  });

  it('200: 정상 URL → Summary 반환', async () => {
    mockedSummarize.mockResolvedValueOnce(fixtureOutput);
    mockedPersist.mockResolvedValueOnce(fixtureSummary);

    const res = await POST(makeRequest({ url: VALID_URL }));

    expect(res.status).toBe(200);
    const json = await res.json();
    expect(json).toEqual(fixtureSummary);
    expect(mockedSummarize).toHaveBeenCalledWith(VALID_URL);
    expect(mockedPersist).toHaveBeenCalledTimes(1);
  });

  it('400: body 누락', async () => {
    const res = await POST(makeRequest());
    expect(res.status).toBe(400);
    const json = await res.json();
    expect(json.error).toBe('INVALID_URL');
    expect(mockedSummarize).not.toHaveBeenCalled();
  });

  it('400: url 누락', async () => {
    const res = await POST(makeRequest({}));
    expect(res.status).toBe(400);
    const json = await res.json();
    expect(json.error).toBe('INVALID_URL');
    expect(mockedSummarize).not.toHaveBeenCalled();
  });

  it('400: InvalidUrlError', async () => {
    mockedSummarize.mockRejectedValueOnce(new InvalidUrlError('bad url'));
    const res = await POST(makeRequest({ url: 'https://example.com' }));
    expect(res.status).toBe(400);
    const json = await res.json();
    expect(json.error).toBe('INVALID_URL');
    expect(json.message).toContain('bad url');
  });

  it('502: Gemini 에러', async () => {
    mockedSummarize.mockRejectedValueOnce(new Error('gemini boom'));
    const res = await POST(makeRequest({ url: VALID_URL }));
    expect(res.status).toBe(502);
    const json = await res.json();
    expect(json.error).toBe('GEMINI_FAILED');
    expect(json.message).toContain('gemini boom');
  });

  it('500: persistSummary 실패', async () => {
    mockedSummarize.mockResolvedValueOnce(fixtureOutput);
    mockedPersist.mockRejectedValueOnce(new Error('disk full'));
    const res = await POST(makeRequest({ url: VALID_URL }));
    expect(res.status).toBe(500);
    const json = await res.json();
    expect(json.error).toBe('INTERNAL');
  });
});
