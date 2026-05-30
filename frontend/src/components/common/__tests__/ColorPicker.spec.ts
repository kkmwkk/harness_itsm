// @vitest-environment happy-dom
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ColorPicker from '@/components/common/ColorPicker.vue';

/**
 * ColorPicker — color swatch + hex 입력. swatch 정규화와 emit 검증.
 */
describe('ColorPicker', () => {
  it('유효 hex 는 swatch 에 반영', () => {
    const w = mount(ColorPicker, { props: { modelValue: '#0066cc' } });
    const color = w.get('input[type="color"]');
    expect((color.element as HTMLInputElement).value).toBe('#0066cc');
  });

  it('비정상 값은 swatch 가 #000000 으로 폴백(텍스트는 원본 유지)', () => {
    const w = mount(ColorPicker, { props: { modelValue: 'red' } });
    const color = w.get('input[type="color"]');
    expect((color.element as HTMLInputElement).value).toBe('#000000');
  });

  it('color input 변경 시 hex emit', async () => {
    const w = mount(ColorPicker, { props: { modelValue: '#000000' } });
    const color = w.get('input[type="color"]');
    (color.element as HTMLInputElement).value = '#16a34a';
    await color.trigger('input');
    expect(w.emitted('update:modelValue')?.[0]).toEqual(['#16a34a']);
  });
});
