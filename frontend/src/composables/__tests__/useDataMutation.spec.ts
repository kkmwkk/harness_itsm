import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ref } from 'vue';

// useApiFetch 를 mock — 실제 네트워크 없이 submit 경로(성공/실패/로딩 토글)만 검증한다(ADR-011, 가상 경로).
const postSpy = vi.fn();
const jsonSpy = vi.fn();

vi.mock('@/lib/api', () => ({
  useApiFetch: vi.fn(() => ({ post: postSpy })),
}));

import { useDataMutation } from '@/composables/useDataMutation';

/** json() 의 await 결과(refs) 를 구성하는 헬퍼. */
function mockResponse(opts: {
  data?: unknown;
  message?: string | null;
  statusCode?: number;
  error?: Error | null;
}) {
  return {
    data: ref({ data: opts.data ?? null, message: opts.message ?? null }),
    statusCode: ref(opts.statusCode ?? 200),
    error: ref(opts.error ?? null),
  };
}

describe('useDataMutation', () => {
  beforeEach(() => {
    postSpy.mockReset();
    jsonSpy.mockReset();
    postSpy.mockReturnValue({ json: jsonSpy });
  });

  it('성공_응답이면_data_를_반환하고_error_는_null', async () => {
    jsonSpy.mockResolvedValue(mockResponse({ data: { id: 1 }, statusCode: 201 }));
    const { submit, error } = useDataMutation<Record<string, unknown>, { id: number }>();

    const result = await submit('/api/tickets', { title: '샘플-제목' });

    expect(result).toEqual({ id: 1 });
    expect(error.value).toBeNull();
  });

  it('isLoading_은_submit_시작_시_true_종료_후_false', async () => {
    jsonSpy.mockResolvedValue(mockResponse({ data: { id: 1 } }));
    const { submit, isLoading } = useDataMutation();

    expect(isLoading.value).toBe(false);
    const p = submit('/api/tickets', {});
    expect(isLoading.value).toBe(true);
    await p;
    expect(isLoading.value).toBe(false);
  });

  it('상태코드_400_이상이면_null_반환_및_message_를_error_에_적재', async () => {
    jsonSpy.mockResolvedValue(
      mockResponse({ statusCode: 400, message: '필수값 누락' }),
    );
    const { submit, error } = useDataMutation();

    const result = await submit('/api/tickets', {});

    expect(result).toBeNull();
    expect(error.value).toBe('필수값 누락');
  });

  it('fetchError_가_있으면_null_반환_및_에러_메시지_적재', async () => {
    jsonSpy.mockResolvedValue(
      mockResponse({ error: new Error('네트워크 오류') }),
    );
    const { submit, error } = useDataMutation();

    const result = await submit('/api/tickets', {});

    expect(result).toBeNull();
    expect(error.value).toBe('네트워크 오류');
  });
});
