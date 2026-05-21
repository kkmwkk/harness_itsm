import { getDb } from '@/lib/db';
import { errorResponse, jsonResponse } from '@/lib/http';
import { persistSummary } from '@/services/exporter';
import { InvalidUrlError, summarize } from '@/services/summarizer';

interface SummarizeRequestBody {
  url: string;
}

function extractUrl(body: unknown): string | null {
  if (!body || typeof body !== 'object') return null;
  const value = (body as Record<string, unknown>).url;
  return typeof value === 'string' && value.length > 0 ? value : null;
}

export async function POST(request: Request): Promise<Response> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return errorResponse(
      'INVALID_URL',
      'Request body must be valid JSON with a string `url`',
      400,
    );
  }

  const url = extractUrl(body);
  if (url === null) {
    return errorResponse(
      'INVALID_URL',
      'Request body must contain a non-empty string `url`',
      400,
    );
  }

  const input: SummarizeRequestBody = { url };

  let output;
  try {
    output = await summarize(input.url);
  } catch (err) {
    if (err instanceof InvalidUrlError) {
      return errorResponse('INVALID_URL', err.message, 400);
    }
    return errorResponse(
      'GEMINI_FAILED',
      err instanceof Error ? err.message : String(err),
      502,
    );
  }

  try {
    const db = getDb();
    const summary = await persistSummary(db, output);
    return jsonResponse(summary, 200);
  } catch (err) {
    return errorResponse(
      'INTERNAL',
      err instanceof Error ? err.message : String(err),
      500,
    );
  }
}
