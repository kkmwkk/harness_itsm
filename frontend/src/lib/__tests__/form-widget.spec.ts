import { describe, it, expect } from 'vitest';
import { widgetFor } from '@/lib/form-widget';
import type { FieldMeta } from '@/types/meta-body';

/**
 * DynamicForm 의 type → 위젯 분기 매트릭스(순수). step9 에서 placeholder 였던 6종이
 * 전용 위젯으로 매핑되는지, slider/markdown/status·priority 옵션 분기까지 검증한다.
 */
function f(partial: Partial<FieldMeta> & Pick<FieldMeta, 'type'>): FieldMeta {
  return { name: 'x', label: 'X', ...partial };
}

describe('widgetFor — 기본 타입 매핑', () => {
  it.each([
    ['text', 'input'],
    ['number', 'input'],
    ['textarea', 'textarea'],
    ['date', 'date'],
    ['date-range', 'date-range'],
    ['user-picker', 'user-picker'],
    ['file', 'file'],
    ['select', 'select'],
    ['radio', 'radio'],
    ['checkbox', 'checkbox'],
  ] as const)('%s → %s', (type, expected) => {
    expect(widgetFor(f({ type }))).toBe(expected);
  });
});

describe('widgetFor — 위젯 옵션 분기', () => {
  it('number + widget:slider → slider', () => {
    expect(widgetFor(f({ type: 'number', widget: 'slider' }))).toBe('slider');
  });
  it('textarea + markdown:true → markdown', () => {
    expect(widgetFor(f({ type: 'textarea', markdown: true }))).toBe('markdown');
  });
  it('textarea + markdown 미설정 → textarea', () => {
    expect(widgetFor(f({ type: 'textarea' }))).toBe('textarea');
  });
});

describe('widgetFor — status/priority', () => {
  it('options 있으면 select', () => {
    const opts = [{ value: 'HIGH', label: '높음' }];
    expect(widgetFor(f({ type: 'priority', options: opts }))).toBe('select');
    expect(widgetFor(f({ type: 'status', options: opts }))).toBe('select');
  });
  it('options 없으면 input', () => {
    expect(widgetFor(f({ type: 'priority' }))).toBe('input');
    expect(widgetFor(f({ type: 'status' }))).toBe('input');
  });
});
