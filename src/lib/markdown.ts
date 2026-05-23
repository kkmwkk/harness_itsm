import fs from 'node:fs';
import path from 'node:path';
import matter from 'gray-matter';

function resolveOutputDir(outputDir?: string): string {
  if (outputDir && outputDir.length > 0) return outputDir;
  return process.env.OUTPUT_DIR ?? path.join(process.cwd(), 'output');
}

export function buildMarkdownDocument(
  meta: Record<string, unknown>,
  body: string,
): string {
  const serialized = matter.stringify('', meta).replace(/\n+$/, '\n');
  return `${serialized}\n${body}`;
}

export function writeSummaryFile(
  id: string,
  document: string,
  outputDir?: string,
): string {
  const dir = resolveOutputDir(outputDir);
  fs.mkdirSync(dir, { recursive: true });
  const filePath = path.resolve(dir, `${id}.md`);
  fs.writeFileSync(filePath, document, 'utf8');
  return filePath;
}

export function deleteSummaryFile(id: string, outputDir?: string): void {
  const dir = resolveOutputDir(outputDir);
  const filePath = path.resolve(dir, `${id}.md`);
  try {
    fs.unlinkSync(filePath);
  } catch (err) {
    if ((err as NodeJS.ErrnoException).code === 'ENOENT') return;
    throw err;
  }
}
