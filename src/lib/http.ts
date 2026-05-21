export interface ApiError {
  error: string;
  message: string;
}

export function jsonResponse<T>(data: T, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export function errorResponse(
  error: string,
  message: string,
  status: number,
): Response {
  return jsonResponse<ApiError>({ error, message }, status);
}

export interface ListParams {
  limit: number;
  offset: number;
}

export function parseListParams(
  searchParams: URLSearchParams,
): ListParams | ApiError {
  const rawLimit = searchParams.get('limit');
  const rawOffset = searchParams.get('offset');

  const limit = rawLimit === null ? 50 : Number(rawLimit);
  const offset = rawOffset === null ? 0 : Number(rawOffset);

  if (
    rawLimit !== null &&
    (!/^\d+$/.test(rawLimit) || !Number.isInteger(limit))
  ) {
    return {
      error: 'INVALID_PARAM',
      message: 'limit must be a non-negative integer',
    };
  }
  if (limit < 0 || limit > 200) {
    return {
      error: 'INVALID_PARAM',
      message: 'limit must be in [0, 200]',
    };
  }
  if (
    rawOffset !== null &&
    (!/^\d+$/.test(rawOffset) || !Number.isInteger(offset))
  ) {
    return {
      error: 'INVALID_PARAM',
      message: 'offset must be a non-negative integer',
    };
  }
  if (offset < 0) {
    return {
      error: 'INVALID_PARAM',
      message: 'offset must be non-negative',
    };
  }

  return { limit, offset };
}
