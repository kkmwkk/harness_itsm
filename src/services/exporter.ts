import path from 'node:path';
import { randomUUID } from 'node:crypto';
import type Database from 'better-sqlite3';
import { deleteSummary, insertSummary } from '@/lib/db';
import {
  buildMarkdownDocument,
  deleteSummaryFile,
  writeSummaryFile,
} from '@/lib/markdown';
import type { Summary } from '@/types/summary';
import type { SummarizerOutput } from './summarizer';

function resolveOutputDir(outputDir?: string): string {
  if (outputDir && outputDir.length > 0) return outputDir;
  return process.env.OUTPUT_DIR ?? path.join(process.cwd(), 'output');
}

export async function persistSummary(
  db: Database.Database,
  output: SummarizerOutput,
  opts: { outputDir?: string } = {},
): Promise<Summary> {
  const dir = resolveOutputDir(opts.outputDir);
  const id = randomUUID();
  const mdPath = path.resolve(dir, `${id}.md`);

  const document = buildMarkdownDocument(
    {
      title: output.meta.title,
      channel: output.meta.channel,
      url: output.meta.url,
      duration: output.meta.duration,
      one_liner: output.meta.oneLiner,
    },
    output.body,
  );

  const summary = insertSummary(
    db,
    {
      url: output.meta.url,
      title: output.meta.title,
      channel: output.meta.channel,
      duration: output.meta.duration,
      oneLiner: output.meta.oneLiner,
      mdPath,
    },
    { id },
  );

  try {
    writeSummaryFile(id, document, dir);
  } catch (err) {
    try {
      deleteSummaryFile(id, dir);
    } catch {
      // best-effort cleanup; ignore secondary failures
    }
    deleteSummary(db, id);
    throw err;
  }

  return summary;
}
