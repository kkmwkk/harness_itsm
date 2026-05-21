import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MarkdownRenderer } from './MarkdownRenderer';

describe('MarkdownRenderer', () => {
  it('renders ## Heading as <h2>', () => {
    render(<MarkdownRenderer body={'## 안녕하세요'} />);
    const heading = screen.getByRole('heading', { level: 2 });
    expect(heading).toHaveTextContent('안녕하세요');
  });

  it('renders paragraphs', () => {
    render(<MarkdownRenderer body={'본문 한 줄.'} />);
    expect(screen.getByText('본문 한 줄.')).toBeInTheDocument();
  });
});
