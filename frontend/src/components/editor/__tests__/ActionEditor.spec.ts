import { describe, it, expect } from 'vitest';
import {
  ACTION_TYPES,
  ACTION_TYPE_LABELS,
  isValidActionId,
  needsTo,
  isExternalUrl,
  createBlankAction,
  duplicateActionIds,
  validateActions,
  hasBlockingActionIssues,
} from '@/composables/useActionEditor';
import type { ActionMeta } from '@/types/meta-body';

/**
 * ActionEditor 의 순수 로직(useActionEditor) 검증.
 * vitest 환경이 node 라 DOM 마운트 대신 컴포넌트가 위임하는 로직을 직접 검증한다
 * (FormFieldEditor.spec·GridColumnEditor.spec 와 동일 전략).
 */

describe('ACTION_TYPES — 액션 타입 4종', () => {
  it('dialog-form/export/navigate/custom 4종이며 한글 라벨이 있다', () => {
    expect(ACTION_TYPES).toEqual(['dialog-form', 'export', 'navigate', 'custom']);
    expect(ACTION_TYPE_LABELS['dialog-form']).toBeTruthy();
    expect(ACTION_TYPE_LABELS.navigate).toBeTruthy();
  });
});

describe('createBlankAction — 액션 추가', () => {
  it('빈 목록에서 기본 type=dialog-form 액션을 만든다', () => {
    const a = createBlankAction([]);
    expect(a.type).toBe('dialog-form');
    expect(a.id).toBe('action_1');
  });

  it('기존 id 와 충돌하지 않는 id 를 생성한다', () => {
    const existing: ActionMeta[] = [
      { id: 'action_1', label: 'A', type: 'dialog-form' },
      { id: 'action_2', label: 'B', type: 'export' },
    ];
    const a = createBlankAction(existing);
    expect(existing.map((e) => e.id)).not.toContain(a.id);
  });
});

describe('액션 삭제 — filter 로 인덱스 제거', () => {
  it('지정 인덱스를 제외한 배열을 만든다', () => {
    const actions: ActionMeta[] = [
      { id: 'a', label: 'A', type: 'dialog-form' },
      { id: 'b', label: 'B', type: 'export' },
      { id: 'c', label: 'C', type: 'navigate' },
    ];
    const next = actions.filter((_, i) => i !== 1);
    expect(next.map((a) => a.id)).toEqual(['a', 'c']);
  });
});

describe('isValidActionId — id 식별자 규칙', () => {
  it('영문 소문자 시작 식별자(하이픈 허용)를 통과시킨다', () => {
    expect(isValidActionId('create')).toBe(true);
    expect(isValidActionId('export-csv')).toBe(true);
  });
  it('숫자 시작·공백·빈 문자열은 거부한다', () => {
    expect(isValidActionId('1action')).toBe(false);
    expect(isValidActionId('my action')).toBe(false);
    expect(isValidActionId('')).toBe(false);
  });
});

describe('needsTo / isExternalUrl', () => {
  it('navigate 만 to 가 필요하다', () => {
    expect(needsTo('navigate')).toBe(true);
    expect(needsTo('dialog-form')).toBe(false);
  });
  it('http/https 만 외부 URL 로 본다(새 탭 강제 대상)', () => {
    expect(isExternalUrl('https://example.com')).toBe(true);
    expect(isExternalUrl('http://example.com')).toBe(true);
    expect(isExternalUrl('/itsm')).toBe(false);
    expect(isExternalUrl(undefined)).toBe(false);
  });
});

describe('duplicateActionIds — 중복 id 검증', () => {
  it('중복된 id 를 집어낸다', () => {
    const actions: ActionMeta[] = [
      { id: 'create', label: 'A', type: 'dialog-form' },
      { id: 'create', label: 'B', type: 'dialog-form' },
      { id: 'export', label: 'C', type: 'export' },
    ];
    expect(duplicateActionIds(actions)).toEqual(['create']);
  });

  it('공백 id 는 중복 판정에서 제외한다', () => {
    const actions: ActionMeta[] = [
      { id: '', label: 'A', type: 'dialog-form' },
      { id: '', label: 'B', type: 'export' },
    ];
    expect(duplicateActionIds(actions)).toEqual([]);
  });
});

describe('validateActions — 검증 이슈', () => {
  it('정상 액션은 이슈가 없다', () => {
    const actions: ActionMeta[] = [{ id: 'create', label: '등록', type: 'dialog-form' }];
    expect(validateActions(actions)).toHaveLength(0);
  });

  it('id 누락·라벨 누락은 ERROR', () => {
    const actions: ActionMeta[] = [{ id: '', label: '', type: 'dialog-form' }];
    expect(validateActions(actions).some((i) => i.level === 'ERROR')).toBe(true);
  });

  it('중복 id 는 ERROR 로 저장을 막는다', () => {
    const actions: ActionMeta[] = [
      { id: 'dup', label: 'A', type: 'dialog-form' },
      { id: 'dup', label: 'B', type: 'export' },
    ];
    expect(hasBlockingActionIssues(actions)).toBe(true);
  });

  it('navigate 인데 to 미지정은 WARNING (저장은 가능)', () => {
    const actions: ActionMeta[] = [{ id: 'go', label: '이동', type: 'navigate' }];
    const issues = validateActions(actions);
    expect(issues.some((i) => i.level === 'WARNING')).toBe(true);
    expect(hasBlockingActionIssues(actions)).toBe(false);
  });
});
