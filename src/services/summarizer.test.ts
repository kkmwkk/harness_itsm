import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/gemini', () => {
  return {
    summarizeYoutubeUrl: vi.fn(),
    MissingApiKeyError: class MissingApiKeyError extends Error {},
    GeminiCallError: class GeminiCallError extends Error {},
    SUMMARY_PROMPT: 'TEST_PROMPT',
  };
});

import { summarizeYoutubeUrl } from '@/lib/gemini';
import {
  summarize,
  isValidYoutubeUrl,
  InvalidUrlError,
  InvalidSummaryError,
} from './summarizer';

const mockedSummarize = vi.mocked(summarizeYoutubeUrl);

const VALID_URL = 'https://www.youtube.com/watch?v=dQw4w9WgXcQ';

function buildMarkdown(opts: {
  title?: string;
  channel?: string;
  url?: string;
  duration?: number | string;
  oneLiner?: string;
  body?: string;
  skip?: Array<'title' | 'channel' | 'url' | 'duration' | 'one_liner'>;
}): string {
  const skip = new Set(opts.skip ?? []);
  const lines: string[] = ['---'];
  if (!skip.has('title')) lines.push(`title: ${opts.title ?? 'Sample Title'}`);
  if (!skip.has('channel'))
    lines.push(`channel: ${opts.channel ?? 'Sample Channel'}`);
  if (!skip.has('url')) lines.push(`url: ${opts.url ?? VALID_URL}`);
  if (!skip.has('duration')) {
    const dur = opts.duration ?? 600;
    const v = typeof dur === 'string' ? `"${dur}"` : `${dur}`;
    lines.push(`duration: ${v}`);
  }
  if (!skip.has('one_liner'))
    lines.push(`one_liner: ${opts.oneLiner ?? 'A short one-liner.'}`);
  lines.push('---', '', opts.body ?? '## 핵심 포인트\n\n- A\n- B\n');
  return lines.join('\n');
}

describe('isValidYoutubeUrl', () => {
  it('accepts canonical watch?v= URL', () => {
    expect(
      isValidYoutubeUrl('https://www.youtube.com/watch?v=dQw4w9WgXcQ'),
    ).toBe(true);
  });

  it('accepts watch?v= URL with extra params', () => {
    expect(
      isValidYoutubeUrl(
        'https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&feature=share',
      ),
    ).toBe(true);
  });

  it('accepts watch?v= URL without www', () => {
    expect(
      isValidYoutubeUrl('https://youtube.com/watch?v=dQw4w9WgXcQ'),
    ).toBe(true);
  });

  it('accepts youtu.be short URL', () => {
    expect(isValidYoutubeUrl('https://youtu.be/dQw4w9WgXcQ')).toBe(true);
  });

  it('accepts youtu.be short URL with query', () => {
    expect(isValidYoutubeUrl('https://youtu.be/dQw4w9WgXcQ?t=10')).toBe(true);
  });

  it('accepts shorts URL', () => {
    expect(
      isValidYoutubeUrl('https://www.youtube.com/shorts/dQw4w9WgXcQ'),
    ).toBe(true);
  });

  it('rejects non-youtube domain', () => {
    expect(
      isValidYoutubeUrl('https://www.example.com/watch?v=dQw4w9WgXcQ'),
    ).toBe(false);
  });

  it('rejects URL with wrong id length', () => {
    expect(isValidYoutubeUrl('https://www.youtube.com/watch?v=tooShort')).toBe(
      false,
    );
    expect(
      isValidYoutubeUrl('https://www.youtube.com/watch?v=tooLongIdValue123'),
    ).toBe(false);
  });

  it('rejects arbitrary path under youtube.com', () => {
    expect(isValidYoutubeUrl('https://www.youtube.com/channel/UCxxxx')).toBe(
      false,
    );
    expect(isValidYoutubeUrl('https://www.youtube.com/playlist?list=PLxxxx')).toBe(
      false,
    );
  });

  it('rejects plain garbage', () => {
    expect(isValidYoutubeUrl('not a url')).toBe(false);
    expect(isValidYoutubeUrl('')).toBe(false);
  });
});

describe('summarize', () => {
  beforeEach(() => {
    mockedSummarize.mockReset();
  });

  it('throws InvalidUrlError on invalid URL and does not call gemini', async () => {
    await expect(summarize('https://example.com/watch?v=abc')).rejects.toBeInstanceOf(
      InvalidUrlError,
    );
    expect(mockedSummarize).not.toHaveBeenCalled();
  });

  it('returns parsed meta + body when gemini responds with valid markdown', async () => {
    const md = buildMarkdown({
      title: 'How TypeScript Works',
      channel: 'Dev Talks',
      url: VALID_URL,
      duration: 1234,
      oneLiner: '한 줄 요약입니다.',
      body: '## 핵심 포인트\n\n- 첫 번째\n- 두 번째\n',
    });
    mockedSummarize.mockResolvedValueOnce({ markdown: md });

    const out = await summarize(VALID_URL);

    expect(out.meta).toEqual({
      title: 'How TypeScript Works',
      channel: 'Dev Talks',
      url: VALID_URL,
      duration: 1234,
      oneLiner: '한 줄 요약입니다.',
    });
    expect(out.body.trim()).toBe('## 핵심 포인트\n\n- 첫 번째\n- 두 번째');
    expect(mockedSummarize).toHaveBeenCalledWith(VALID_URL);
  });

  it('throws InvalidSummaryError when frontmatter is missing required keys', async () => {
    const md = buildMarkdown({ skip: ['one_liner'] });
    mockedSummarize.mockResolvedValueOnce({ markdown: md });
    await expect(summarize(VALID_URL)).rejects.toBeInstanceOf(InvalidSummaryError);
  });

  it('throws InvalidSummaryError when frontmatter is entirely absent', async () => {
    mockedSummarize.mockResolvedValueOnce({
      markdown: '# Just a body\n\nno frontmatter here\n',
    });
    await expect(summarize(VALID_URL)).rejects.toBeInstanceOf(InvalidSummaryError);
  });

  it('throws InvalidSummaryError when duration is a string', async () => {
    const md = buildMarkdown({ duration: '1234' });
    mockedSummarize.mockResolvedValueOnce({ markdown: md });
    await expect(summarize(VALID_URL)).rejects.toBeInstanceOf(InvalidSummaryError);
  });
});
