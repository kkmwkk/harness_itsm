import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { SubmitForm } from './SubmitForm';

const pushMock = vi.fn();
const refreshMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock, refresh: refreshMock }),
}));

describe('SubmitForm', () => {
  beforeEach(() => {
    pushMock.mockReset();
    refreshMock.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('disables submit button when input is empty', () => {
    render(<SubmitForm />);
    const button = screen.getByRole('button', { name: /요약/ });
    expect(button).toBeDisabled();
  });

  it('routes to the new summary on success', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        id: 'new-id',
        url: 'https://youtu.be/abcdefghijk',
        title: 't',
        channel: 'c',
        duration: 60,
        oneLiner: 'o',
        mdPath: '/tmp/new-id.md',
        createdAt: '2026-05-21T00:00:00.000Z',
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<SubmitForm />);
    const input = screen.getByPlaceholderText(/유튜브 URL/);
    fireEvent.change(input, {
      target: { value: 'https://youtu.be/abcdefghijk' },
    });
    const button = screen.getByRole('button', { name: /요약/ });
    fireEvent.click(button);

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith('/new-id'));
    expect(refreshMock).toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/summarize',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
        }),
      }),
    );
  });

  it('shows inline error when the request fails', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({
        error: 'INVALID_URL',
        message: 'bad url',
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    render(<SubmitForm />);
    const input = screen.getByPlaceholderText(/유튜브 URL/);
    fireEvent.change(input, { target: { value: 'not-a-url' } });
    fireEvent.click(screen.getByRole('button', { name: /요약/ }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('bad url'),
    );
    expect(pushMock).not.toHaveBeenCalled();
  });
});
