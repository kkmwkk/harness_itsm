import { describe, it, expect, vi, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { SyncToNotionButton } from './SyncToNotionButton';

describe('SyncToNotionButton', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('posts to /api/notion with the summaryId', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        pageId: 'page-1',
        pageUrl: 'https://www.notion.so/Sample-Title-page-1',
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<SyncToNotionButton summaryId="s-1" />);
    fireEvent.click(screen.getByRole('button', { name: /노션으로 보내기/ }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/notion',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
          }),
          body: JSON.stringify({ summaryId: 's-1' }),
        }),
      ),
    );
  });

  it('shows "노션에서 열기" link pointing at pageUrl on success', async () => {
    const pageUrl = 'https://www.notion.so/Sample-Title-page-1';
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ pageId: 'page-1', pageUrl }),
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<SyncToNotionButton summaryId="s-1" />);
    fireEvent.click(screen.getByRole('button', { name: /노션으로 보내기/ }));

    const link = await screen.findByRole('link', { name: /노션에서 열기/ });
    expect(link).toHaveAttribute('href', pageUrl);
  });

  it('shows "노션 미연결" inline message on 503', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      json: async () => ({
        error: 'NOTION_NOT_CONFIGURED',
        message: 'NOTION_TOKEN is not set',
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<SyncToNotionButton summaryId="s-1" />);
    fireEvent.click(screen.getByRole('button', { name: /노션으로 보내기/ }));

    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent('노션 미연결'),
    );
  });

  it('shows inline error on other failures', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({
        error: 'NOTION_FAILED',
        message: 'something broke',
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<SyncToNotionButton summaryId="s-1" />);
    fireEvent.click(screen.getByRole('button', { name: /노션으로 보내기/ }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('something broke'),
    );
  });
});
