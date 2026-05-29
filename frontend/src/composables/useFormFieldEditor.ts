import type { FieldMeta, FieldType, FormMeta } from '@/types/meta-body';

/**
 * 폼 필드 편집기(FormFieldEditor.vue) 의 순수 로직.
 * UI 와 분리해 단위 테스트 가능하도록 두며, 컴포넌트는 이 헬퍼만 호출한다.
 * (ADR-016 1단계 — JSON 직접 편집 없이 GUI 로 form.fields 를 다룬다.)
 */

/** FieldType 12종 — meta-body.ts·ARCHITECTURE §5 와 1:1 일치. */
export const FIELD_TYPES: readonly FieldType[] = [
  'text',
  'textarea',
  'number',
  'select',
  'radio',
  'checkbox',
  'date',
  'date-range',
  'user-picker',
  'file',
  'status',
  'priority',
] as const;

/** 정적 옵션(또는 optionsApi)이 필요한 타입. */
export const OPTION_TYPES: readonly FieldType[] = ['select', 'radio'] as const;

/** 사용자에게 보여줄 타입 한글 라벨. */
export const FIELD_TYPE_LABELS: Record<FieldType, string> = {
  text: '텍스트',
  textarea: '여러 줄 텍스트',
  number: '숫자',
  select: '선택(드롭다운)',
  radio: '라디오',
  checkbox: '체크박스',
  date: '날짜',
  'date-range': '날짜 범위',
  'user-picker': '사용자 선택',
  file: '파일',
  status: '상태',
  priority: '우선순위',
};

/** select/radio 처럼 옵션이 필요한 타입인지. */
export function needsOptions(type: FieldType): boolean {
  return OPTION_TYPES.includes(type);
}

/** name 은 영문 소문자/언더스코어로 시작하는 식별자(snake_case·camelCase 허용). */
const NAME_RE = /^[a-z_][a-zA-Z0-9_]*$/;
export function isValidFieldName(name: string): boolean {
  return NAME_RE.test(name);
}

/** 기존 필드와 충돌하지 않는 신규 필드(기본 type=text)를 만든다. */
export function createBlankField(existing: FieldMeta[]): FieldMeta {
  const taken = new Set(existing.map((f) => f.name));
  let i = existing.length + 1;
  let name = `field_${i}`;
  while (taken.has(name)) {
    i += 1;
    name = `field_${i}`;
  }
  return { name, label: `필드 ${i}`, type: 'text', required: false, span: 1 };
}

/** 중복된 name 목록(공백 name 은 제외). */
export function duplicateNames(fields: FieldMeta[]): string[] {
  const seen = new Set<string>();
  const dup = new Set<string>();
  for (const f of fields) {
    const n = f.name.trim();
    if (!n) continue;
    if (seen.has(n)) dup.add(n);
    seen.add(n);
  }
  return [...dup];
}

export interface FieldIssue {
  level: 'ERROR' | 'WARNING';
  message: string;
}

/**
 * 클라이언트 측 사전 검증 — 저장 가능 여부(ERROR)와 정보성 경고(WARNING)를 구분한다.
 * 백엔드 dry-run 과는 별개로, 저장 버튼 활성화·즉각 피드백에 사용.
 */
export function validateFormFields(fields: FieldMeta[]): FieldIssue[] {
  const issues: FieldIssue[] = [];
  fields.forEach((f, idx) => {
    const name = f.name?.trim() ?? '';
    const label = f.label?.trim() || name || `#${idx + 1}`;
    if (!name) {
      issues.push({ level: 'ERROR', message: `${label}: name 은 필수입니다.` });
    } else if (!isValidFieldName(name)) {
      issues.push({
        level: 'ERROR',
        message: `${label}: name 은 영문 소문자로 시작하는 식별자여야 합니다.`,
      });
    }
    if (!f.label?.trim()) {
      issues.push({ level: 'ERROR', message: `${name || `#${idx + 1}`}: 라벨은 필수입니다.` });
    }
    if (!f.type) {
      issues.push({ level: 'ERROR', message: `${label}: 타입은 필수입니다.` });
    }
    if (needsOptions(f.type) && !(f.options && f.options.length > 0) && !f.optionsApi) {
      issues.push({
        level: 'WARNING',
        message: `${label}: ${FIELD_TYPE_LABELS[f.type]} 필드에 옵션이 없습니다.`,
      });
    }
  });
  for (const n of duplicateNames(fields)) {
    issues.push({ level: 'ERROR', message: `name '${n}' 이(가) 중복됩니다.` });
  }
  return issues;
}

/** ERROR 가 하나라도 있으면 저장 불가. */
export function hasBlockingIssues(fields: FieldMeta[]): boolean {
  return validateFormFields(fields).some((i) => i.level === 'ERROR');
}

/** 빈 form 골격(편집 진입 시 form 이 없을 때 기본값). */
export function emptyForm(): FormMeta {
  return { layout: 'two-column', fields: [] };
}
