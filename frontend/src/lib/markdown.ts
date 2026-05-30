/**
 * 마크다운 → 안전한 HTML 변환 헬퍼.
 * marked 로 파싱한 뒤 DOMPurify 로 새니타이즈한다 — raw HTML 렌더 금지(UI_GUIDE a11y/보안).
 * 순수 함수(입력 문자열 → 출력 문자열)이며 MarkdownEditor 미리보기에서 사용한다.
 */
import { marked } from 'marked';
import DOMPurify from 'dompurify';

/** 마크다운 문자열을 새니타이즈된 HTML 로 변환한다. 빈 입력은 빈 문자열. */
export function renderMarkdown(src?: string | null): string {
  if (!src) return '';
  const raw = marked.parse(src, { async: false });
  return DOMPurify.sanitize(raw);
}
