import { errorResponse, jsonResponse } from '@/lib/http';
import { MissingNotionTokenError } from '@/lib/notion';
import {
  NotFoundError,
  NotionSyncError,
  syncSummaryToNotion,
} from '@/services/notion';

function extractSummaryId(body: unknown): string | null {
  if (!body || typeof body !== 'object') return null;
  const value = (body as Record<string, unknown>).summaryId;
  return typeof value === 'string' && value.length > 0 ? value : null;
}

export async function POST(request: Request): Promise<Response> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return errorResponse(
      'INVALID_BODY',
      'Request body must be valid JSON with a string `summaryId`',
      400,
    );
  }

  const summaryId = extractSummaryId(body);
  if (summaryId === null) {
    return errorResponse(
      'INVALID_BODY',
      'Request body must contain a non-empty string `summaryId`',
      400,
    );
  }

  try {
    const result = await syncSummaryToNotion(summaryId);
    return jsonResponse(result, 200);
  } catch (err) {
    if (err instanceof MissingNotionTokenError) {
      return errorResponse('NOTION_NOT_CONFIGURED', err.message, 503);
    }
    if (err instanceof NotFoundError) {
      return errorResponse('NOT_FOUND', err.message, 404);
    }
    if (err instanceof NotionSyncError) {
      return errorResponse('NOTION_FAILED', err.message, 500);
    }
    return errorResponse(
      'NOTION_FAILED',
      err instanceof Error ? err.message : String(err),
      500,
    );
  }
}
