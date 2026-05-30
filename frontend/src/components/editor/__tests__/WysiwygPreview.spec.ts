import { describe, it, expect } from 'vitest';
import {
  setFieldLabel,
  removeFieldAt,
  setColumnLabel,
  setColumnWidth,
  removeColumnAt,
} from '@/composables/useWysiwygPreview';
import type { FieldMeta, GridColumnMeta } from '@/types/meta-body';

/**
 * WYSIWYG PoC(useWysiwygPreview) 의 인플레이스 편집 순수 로직 검증.
 * 컴포넌트가 위임하는 헬퍼를 직접 검증한다(editor 스위트의 Node 기반 전략과 동일).
 * 핵심: 인플레이스 라벨 편집 → body draft 갱신, 삭제 → 필드/컬럼 제거, 원본 불변.
 */

function fields(): FieldMeta[] {
  return [
    { name: 'title', label: '제목', type: 'text' },
    { name: 'priority', label: '우선순위', type: 'priority' },
    { name: 'content', label: '내용', type: 'textarea' },
  ];
}

function columns(): GridColumnMeta[] {
  return [
    { field: 'title', label: '제목', type: 'text', flex: 1 },
    { field: 'priority', label: '우선순위', type: 'priority', width: 110 },
  ];
}

describe('setFieldLabel — 폼 필드 라벨 인플레이스 편집', () => {
  it('지정 필드의 라벨만 새 값으로 바꾼 새 배열을 반환한다', () => {
    const [first, second] = setFieldLabel(fields(), 0, '요청 제목');
    expect(first?.label).toBe('요청 제목');
    // name·type 은 보존
    expect(first?.name).toBe('title');
    expect(first?.type).toBe('text');
    // 다른 필드는 그대로
    expect(second?.label).toBe('우선순위');
  });

  it('원본 배열·원본 객체를 변경하지 않는다(불변)', () => {
    const src = fields();
    setFieldLabel(src, 0, '바뀜');
    expect(src[0]?.label).toBe('제목');
  });

  it('범위를 벗어난 index 는 no-op 복사본을 반환한다', () => {
    const next = setFieldLabel(fields(), 9, 'x');
    expect(next.map((f) => f.label)).toEqual(['제목', '우선순위', '내용']);
  });
});

describe('removeFieldAt — 폼 필드 삭제', () => {
  it('지정 인덱스 필드를 제거한 새 배열을 반환한다', () => {
    const next = removeFieldAt(fields(), 1);
    expect(next.map((f) => f.name)).toEqual(['title', 'content']);
  });

  it('범위를 벗어난 index 는 그대로 둔다', () => {
    const next = removeFieldAt(fields(), 5);
    expect(next).toHaveLength(3);
  });
});

describe('setColumnLabel — 그리드 컬럼 헤더 라벨 인플레이스 편집', () => {
  it('지정 컬럼의 라벨만 바꾼 새 배열을 반환한다', () => {
    const [first] = setColumnLabel(columns(), 0, '티켓 제목');
    expect(first?.label).toBe('티켓 제목');
    expect(first?.field).toBe('title');
  });
});

describe('setColumnWidth — 그리드 컬럼 width 인플레이스 편집', () => {
  it('px 너비를 지정하면 width 설정·flex 제거(px 모드)', () => {
    const [first] = setColumnWidth(columns(), 0, 150);
    expect(first?.width).toBe(150);
    expect(first?.flex).toBeUndefined();
  });

  it('소수는 반올림한다', () => {
    const next = setColumnWidth(columns(), 1, 149.6);
    expect(next[1]?.width).toBe(150);
  });

  it('1 미만·NaN 은 무시(no-op 복사본)', () => {
    expect(setColumnWidth(columns(), 0, 0)[0]?.width).toBeUndefined();
    expect(setColumnWidth(columns(), 0, Number.NaN)[0]?.flex).toBe(1);
  });
});

describe('removeColumnAt — 그리드 컬럼 삭제', () => {
  it('지정 인덱스 컬럼을 제거한 새 배열을 반환한다', () => {
    const next = removeColumnAt(columns(), 0);
    expect(next.map((c) => c.field)).toEqual(['priority']);
  });
});
