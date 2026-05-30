// @vitest-environment happy-dom
import { describe, it, expect } from 'vitest';
import { renderMarkdown } from '@/lib/markdown';

/**
 * MarkdownEditor 미리보기 변환 — marked 파싱 + DOMPurify 새니타이즈.
 * DOMPurify 는 window 가 필요해 happy-dom 환경에서 실행한다.
 */
describe('renderMarkdown', () => {
  it('기본 마크다운을 HTML 로 변환', () => {
    const out = renderMarkdown('# 제목\n\n**굵게** 그리고 *기울임*');
    expect(out).toContain('<h1');
    expect(out).toContain('<strong>굵게</strong>');
    expect(out).toContain('<em>기울임</em>');
  });

  it('목록 렌더', () => {
    const out = renderMarkdown('- 하나\n- 둘');
    expect(out).toContain('<ul>');
    expect(out).toContain('<li>하나</li>');
  });

  it('악성 script 태그를 제거(raw HTML 렌더 금지)', () => {
    const out = renderMarkdown('정상<script>alert(1)</script>');
    expect(out).not.toContain('<script>');
    expect(out).not.toContain('alert(1)');
  });

  it('onerror 등 이벤트 핸들러 속성 제거', () => {
    const out = renderMarkdown('<img src=x onerror="alert(1)">');
    expect(out).not.toContain('onerror');
  });

  it('빈 입력은 빈 문자열', () => {
    expect(renderMarkdown('')).toBe('');
    expect(renderMarkdown(null)).toBe('');
    expect(renderMarkdown(undefined)).toBe('');
  });
});
