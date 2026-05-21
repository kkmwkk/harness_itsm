import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import {
  MissingApiKeyError,
  SUMMARY_PROMPT,
  summarizeYoutubeUrl,
} from './gemini';

describe('gemini', () => {
  const originalKey = process.env.GEMINI_API_KEY;

  beforeEach(() => {
    delete process.env.GEMINI_API_KEY;
  });

  afterEach(() => {
    if (originalKey === undefined) {
      delete process.env.GEMINI_API_KEY;
    } else {
      process.env.GEMINI_API_KEY = originalKey;
    }
  });

  it('SUMMARY_PROMPT is a non-empty string', () => {
    expect(typeof SUMMARY_PROMPT).toBe('string');
    expect(SUMMARY_PROMPT.length).toBeGreaterThan(0);
  });

  it('summarizeYoutubeUrl throws MissingApiKeyError when GEMINI_API_KEY is unset', async () => {
    await expect(
      summarizeYoutubeUrl('https://www.youtube.com/watch?v=abcdefghijk'),
    ).rejects.toBeInstanceOf(MissingApiKeyError);
  });
});
