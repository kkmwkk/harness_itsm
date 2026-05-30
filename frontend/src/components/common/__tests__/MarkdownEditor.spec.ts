// @vitest-environment happy-dom
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import MarkdownEditor from '@/components/common/MarkdownEditor.vue';

/**
 * MarkdownEditor — modelValue 가 우측 미리보기에 새니타이즈된 HTML 로 렌더되는지 검증.
 */
describe('MarkdownEditor', () => {
  it('modelValue 를 미리보기 HTML 로 렌더', () => {
    const w = mount(MarkdownEditor, { props: { modelValue: '# 헤더' } });
    const preview = w.get('.markdown-preview');
    expect(preview.html()).toContain('<h1');
    expect(preview.text()).toContain('헤더');
  });

  it('빈 값이면 안내 문구', () => {
    const w = mount(MarkdownEditor, { props: { modelValue: '' } });
    expect(w.get('.markdown-preview').text()).toContain('미리볼 내용이 없습니다');
  });

  it('script 는 미리보기에서 제거', () => {
    const w = mount(MarkdownEditor, { props: { modelValue: 'x<script>bad()</script>' } });
    expect(w.get('.markdown-preview').html()).not.toContain('<script>');
  });
});
