import type { ActionMeta } from '@/types/meta-body';

/**
 * 액션 편집기(ActionEditor.vue) 의 순수 로직.
 * FormFieldEditor·GridColumnEditor 와 동일하게 UI 와 분리해 단위 테스트 가능하도록 둔다.
 * (ADR-016 1단계 — JSON 직접 편집 없이 GUI 로 meta.actions 를 다룬다.)
 */

/** 액션 타입 4종 — meta-body.ts ActionMeta.type 과 1:1 일치. */
export type ActionType = ActionMeta['type'];
export const ACTION_TYPES: readonly ActionType[] = [
  'dialog-form',
  'export',
  'navigate',
  'custom',
] as const;

/** 사용자에게 보여줄 타입 한글 라벨. */
export const ACTION_TYPE_LABELS: Record<ActionType, string> = {
  'dialog-form': '폼 다이얼로그',
  export: '내보내기',
  navigate: '페이지 이동',
  custom: '커스텀',
};

/** id 는 영문 소문자/언더스코어로 시작하는 식별자(하이픈 허용). */
const ID_RE = /^[a-z_][a-zA-Z0-9_-]*$/;
export function isValidActionId(id: string): boolean {
  return ID_RE.test(id);
}

/** navigate 만 라우트(to) 가 필요하다. */
export function needsTo(type: ActionType): boolean {
  return type === 'navigate';
}

/** to 가 외부 URL(http/https) 인지 — 외부 URL navigate 는 새 탭 강제(보안). */
export function isExternalUrl(to: string | undefined | null): boolean {
  if (!to) return false;
  return /^https?:\/\//i.test(to.trim());
}

/** 기존 액션과 충돌하지 않는 신규 액션(기본 type=dialog-form)을 만든다. */
export function createBlankAction(existing: ActionMeta[]): ActionMeta {
  const taken = new Set(existing.map((a) => a.id));
  let i = existing.length + 1;
  let id = `action_${i}`;
  while (taken.has(id)) {
    i += 1;
    id = `action_${i}`;
  }
  return { id, label: `액션 ${i}`, type: 'dialog-form' };
}

/** 중복된 id 목록(공백 id 는 제외). */
export function duplicateActionIds(actions: ActionMeta[]): string[] {
  const seen = new Set<string>();
  const dup = new Set<string>();
  for (const a of actions) {
    const id = a.id?.trim() ?? '';
    if (!id) continue;
    if (seen.has(id)) dup.add(id);
    seen.add(id);
  }
  return [...dup];
}

export interface ActionIssue {
  level: 'ERROR' | 'WARNING';
  message: string;
}

/**
 * 클라이언트 측 사전 검증.
 * - ERROR: id 필수·식별자 규칙·label 필수·id 중복 → 저장 차단.
 * - WARNING: navigate 인데 to 미지정 → 안내만(이동 대상 없음, 차단하지 않음).
 */
export function validateActions(actions: ActionMeta[]): ActionIssue[] {
  const issues: ActionIssue[] = [];
  actions.forEach((a, idx) => {
    const id = a.id?.trim() ?? '';
    const label = a.label?.trim() || id || `#${idx + 1}`;
    if (!id) {
      issues.push({ level: 'ERROR', message: `${label}: id 는 필수입니다.` });
    } else if (!isValidActionId(id)) {
      issues.push({
        level: 'ERROR',
        message: `${label}: id 는 영문 소문자로 시작하는 식별자여야 합니다.`,
      });
    }
    if (!a.label?.trim()) {
      issues.push({ level: 'ERROR', message: `${id || `#${idx + 1}`}: 라벨은 필수입니다.` });
    }
    if (needsTo(a.type) && !a.to?.trim()) {
      issues.push({ level: 'WARNING', message: `${label}: 이동(navigate) 액션에 to(라우트)가 없습니다.` });
    }
  });
  for (const id of duplicateActionIds(actions)) {
    issues.push({ level: 'ERROR', message: `id '${id}' 이(가) 중복됩니다.` });
  }
  return issues;
}

/** ERROR 가 하나라도 있으면 저장 불가(WARNING 은 저장 가능). */
export function hasBlockingActionIssues(actions: ActionMeta[]): boolean {
  return validateActions(actions).some((i) => i.level === 'ERROR');
}
