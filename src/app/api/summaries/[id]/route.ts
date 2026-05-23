import fs from 'node:fs';
import matter from 'gray-matter';
import { getDb, getSummary } from '@/lib/db';
import { errorResponse, jsonResponse } from '@/lib/http';

interface RouteContext {
  params: Promise<{ id: string }>;
}

export async function GET(
  _request: Request,
  context: RouteContext,
): Promise<Response> {
  const { id } = await context.params;

  let summary;
  try {
    const db = getDb();
    summary = getSummary(db, id);
  } catch (err) {
    return errorResponse(
      'INTERNAL',
      err instanceof Error ? err.message : String(err),
      500,
    );
  }

  if (!summary) {
    return errorResponse('NOT_FOUND', `Summary ${id} not found`, 404);
  }

  let bodyMarkdown: string;
  try {
    const raw = fs.readFileSync(summary.mdPath, 'utf8');
    bodyMarkdown = matter(raw).content;
  } catch (err) {
    const code = (err as NodeJS.ErrnoException).code;
    if (code === 'ENOENT') {
      return errorResponse(
        'MD_MISSING',
        `Markdown file is missing for summary ${id}`,
        500,
      );
    }
    return errorResponse(
      'INTERNAL',
      err instanceof Error ? err.message : String(err),
      500,
    );
  }

  return jsonResponse({ ...summary, bodyMarkdown }, 200);
}
