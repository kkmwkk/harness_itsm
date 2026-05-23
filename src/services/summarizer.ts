import matter from 'gray-matter';
import { summarizeYoutubeUrl } from '@/lib/gemini';

export interface SummarizerOutput {
  meta: {
    title: string;
    channel: string;
    url: string;
    duration: number;
    oneLiner: string;
  };
  body: string;
}

export class InvalidUrlError extends Error {
  constructor(message = 'Invalid YouTube URL') {
    super(message);
    this.name = 'InvalidUrlError';
  }
}

export class InvalidSummaryError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'InvalidSummaryError';
  }
}

const YOUTUBE_URL_PATTERNS: RegExp[] = [
  /^https?:\/\/(www\.)?youtube\.com\/watch\?v=[A-Za-z0-9_-]{11}([&?].*)?$/,
  /^https?:\/\/(www\.)?youtube\.com\/shorts\/[A-Za-z0-9_-]{11}(\?.*)?$/,
  /^https?:\/\/youtu\.be\/[A-Za-z0-9_-]{11}(\?.*)?$/,
];

export function isValidYoutubeUrl(url: string): boolean {
  if (typeof url !== 'string' || url.length === 0) return false;
  return YOUTUBE_URL_PATTERNS.some((re) => re.test(url));
}

const VIDEO_ID_EXTRACTORS: RegExp[] = [
  /youtube\.com\/watch\?v=([A-Za-z0-9_-]{11})/,
  /youtube\.com\/shorts\/([A-Za-z0-9_-]{11})/,
  /youtu\.be\/([A-Za-z0-9_-]{11})/,
];

export function normalizeYoutubeUrl(url: string): string {
  for (const re of VIDEO_ID_EXTRACTORS) {
    const m = url.match(re);
    if (m && m[1]) return `https://www.youtube.com/watch?v=${m[1]}`;
  }
  return url;
}

function isPlainString(v: unknown): v is string {
  return typeof v === 'string' && v.length > 0;
}

function isPlainInteger(v: unknown): v is number {
  return typeof v === 'number' && Number.isInteger(v) && v >= 0;
}

export async function summarize(url: string): Promise<SummarizerOutput> {
  if (!isValidYoutubeUrl(url)) {
    throw new InvalidUrlError(`Invalid YouTube URL: ${url}`);
  }

  const raw = await summarizeYoutubeUrl(normalizeYoutubeUrl(url));

  let parsed: matter.GrayMatterFile<string>;
  try {
    parsed = matter(raw.markdown);
  } catch (err) {
    throw new InvalidSummaryError(
      `Failed to parse frontmatter: ${err instanceof Error ? err.message : String(err)}`,
    );
  }

  if (!parsed.matter || parsed.matter.trim().length === 0) {
    throw new InvalidSummaryError('Frontmatter is missing');
  }

  const data = parsed.data as Record<string, unknown>;

  if (!isPlainString(data.title)) {
    throw new InvalidSummaryError('frontmatter "title" must be a non-empty string');
  }
  if (!isPlainString(data.channel)) {
    throw new InvalidSummaryError('frontmatter "channel" must be a non-empty string');
  }
  if (!isPlainString(data.url)) {
    throw new InvalidSummaryError('frontmatter "url" must be a non-empty string');
  }
  if (!isPlainInteger(data.duration)) {
    throw new InvalidSummaryError('frontmatter "duration" must be a non-negative integer');
  }
  if (!isPlainString(data.one_liner)) {
    throw new InvalidSummaryError('frontmatter "one_liner" must be a non-empty string');
  }

  return {
    meta: {
      title: data.title,
      channel: data.channel,
      url: data.url,
      duration: data.duration,
      oneLiner: data.one_liner,
    },
    body: parsed.content,
  };
}
