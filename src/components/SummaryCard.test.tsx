import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SummaryCard } from './SummaryCard';
import type { Summary } from '@/types/summary';

const summary: Summary = {
  id: 'abc-123',
  url: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
  title: 'Never Gonna Give You Up',
  channel: 'Rick Astley',
  duration: 213,
  oneLiner: '리듬과 보컬이 어우러진 80년대 팝의 정수.',
  mdPath: '/tmp/abc-123.md',
  createdAt: '2026-05-20T12:00:00.000Z',
};

describe('SummaryCard', () => {
  it('renders one-liner and channel name', () => {
    render(<SummaryCard summary={summary} />);
    expect(
      screen.getByText('리듬과 보컬이 어우러진 80년대 팝의 정수.'),
    ).toBeInTheDocument();
    expect(screen.getByText(/Rick Astley/)).toBeInTheDocument();
  });

  it('links to the summary detail page', () => {
    render(<SummaryCard summary={summary} />);
    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/abc-123');
  });

  it('formats duration as Xm Ys', () => {
    render(<SummaryCard summary={summary} />);
    expect(screen.getByText(/3m 33s/)).toBeInTheDocument();
  });
});
