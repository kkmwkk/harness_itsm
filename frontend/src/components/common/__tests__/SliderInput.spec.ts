// @vitest-environment happy-dom
import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import SliderInput from '@/components/common/SliderInput.vue';

/**
 * SliderInput — number 위젯(widget:'slider'). props 반영과 input → number emit 검증.
 */
describe('SliderInput', () => {
  it('range input 에 min/max/step/value 반영', () => {
    const w = mount(SliderInput, {
      props: { modelValue: 30, min: 0, max: 50, step: 5 },
    });
    const input = w.get('input[type="range"]');
    expect(input.attributes('min')).toBe('0');
    expect(input.attributes('max')).toBe('50');
    expect(input.attributes('step')).toBe('5');
    expect((input.element as HTMLInputElement).value).toBe('30');
    expect(w.text()).toContain('30');
  });

  it('modelValue 미설정 시 min 으로 표시', () => {
    const w = mount(SliderInput, { props: { min: 10, max: 20 } });
    expect(w.text()).toContain('10');
  });

  it('input 시 number 값을 emit', async () => {
    const w = mount(SliderInput, { props: { modelValue: 0, max: 100 } });
    const input = w.get('input[type="range"]');
    (input.element as HTMLInputElement).value = '42';
    await input.trigger('input');
    const emitted = w.emitted('update:modelValue');
    expect(emitted).toBeTruthy();
    expect(emitted?.[0]).toEqual([42]);
  });
});
