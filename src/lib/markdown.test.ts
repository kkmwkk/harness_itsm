import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {
  buildMarkdownDocument,
  writeSummaryFile,
  deleteSummaryFile,
} from './markdown';

describe('buildMarkdownDocument', () => {
  it('produces frontmatter-delimited document with the given body', () => {
    const doc = buildMarkdownDocument(
      {
        title: 't',
        channel: 'c',
        url: 'u',
        duration: 60,
        one_liner: 'l',
      },
      '## h\n\nbody',
    );
    expect(doc).toMatch(/^---\n[\s\S]*\n---\n\n## h\n\nbody/);
    expect(doc).toContain('title: t');
    expect(doc).toContain('channel: c');
    expect(doc).toContain('url: u');
    expect(doc).toContain('duration: 60');
    expect(doc).toContain('one_liner: l');
  });
});

describe('writeSummaryFile / deleteSummaryFile', () => {
  let tmpDir: string;

  beforeEach(() => {
    tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'tubenote-md-'));
  });

  afterEach(() => {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  });

  it('writes document to {outputDir}/{id}.md and returns the absolute path', () => {
    const id = 'abc-123';
    const doc = '---\ntitle: t\n---\n\nbody';
    const out = writeSummaryFile(id, doc, tmpDir);
    expect(path.isAbsolute(out)).toBe(true);
    expect(out).toBe(path.join(tmpDir, `${id}.md`));
    expect(fs.readFileSync(out, 'utf8')).toBe(doc);
  });

  it('overwrites when called twice with the same id', () => {
    const id = 'dup';
    writeSummaryFile(id, 'first', tmpDir);
    const out = writeSummaryFile(id, 'second', tmpDir);
    expect(fs.readFileSync(out, 'utf8')).toBe('second');
  });

  it('creates outputDir if it does not exist', () => {
    const nested = path.join(tmpDir, 'nested', 'deep');
    const out = writeSummaryFile('x', 'body', nested);
    expect(fs.existsSync(out)).toBe(true);
  });

  it('deleteSummaryFile removes the file', () => {
    const id = 'to-delete';
    const out = writeSummaryFile(id, 'body', tmpDir);
    expect(fs.existsSync(out)).toBe(true);
    deleteSummaryFile(id, tmpDir);
    expect(fs.existsSync(out)).toBe(false);
  });

  it('deleteSummaryFile does not throw for missing file', () => {
    expect(() => deleteSummaryFile('does-not-exist', tmpDir)).not.toThrow();
  });
});
