import { getDb, listSummaries } from '@/lib/db';
import { errorResponse, jsonResponse, parseListParams } from '@/lib/http';

export async function GET(request: Request): Promise<Response> {
  const { searchParams } = new URL(request.url);
  const parsed = parseListParams(searchParams);
  if ('error' in parsed) {
    return errorResponse(parsed.error, parsed.message, 400);
  }

  try {
    const db = getDb();
    const rows = listSummaries(db, { limit: parsed.limit, offset: parsed.offset });
    return jsonResponse(rows, 200);
  } catch (err) {
    return errorResponse(
      'INTERNAL',
      err instanceof Error ? err.message : String(err),
      500,
    );
  }
}
