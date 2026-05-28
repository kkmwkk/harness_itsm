import type {
  FieldType, FieldMeta, FormMeta, GridColumnMeta, GridMeta,
  ActionMeta, PageMetaBody,
} from '@/types/meta-body';

const FIELD_TYPES: readonly FieldType[] = [
  'text','textarea','number','select','radio','checkbox',
  'date','date-range','user-picker','file','status','priority',
];

function isObject(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function isFieldType(v: unknown): v is FieldType {
  return typeof v === 'string' && (FIELD_TYPES as readonly string[]).includes(v);
}

export function isFieldMeta(v: unknown): v is FieldMeta {
  if (!isObject(v)) return false;
  if (typeof v.name !== 'string')   return false;
  if (typeof v.label !== 'string')  return false;
  if (!isFieldType(v.type))         return false;
  return true;
}

export function isGridColumnMeta(v: unknown): v is GridColumnMeta {
  if (!isObject(v)) return false;
  if (typeof v.field !== 'string') return false;
  if (typeof v.label !== 'string') return false;
  if (!isFieldType(v.type))        return false;
  return true;
}

export function isFormMeta(v: unknown): v is FormMeta {
  if (!isObject(v)) return false;
  const layout = v.layout;
  if (layout !== 'single-column' && layout !== 'two-column') return false;
  if (!Array.isArray(v.fields)) return false;
  return v.fields.every(isFieldMeta);
}

export function isGridMeta(v: unknown): v is GridMeta {
  if (!isObject(v)) return false;
  if (!Array.isArray(v.columns)) return false;
  return v.columns.every(isGridColumnMeta);
}

export function isActionMeta(v: unknown): v is ActionMeta {
  if (!isObject(v)) return false;
  if (typeof v.id !== 'string')    return false;
  if (typeof v.label !== 'string') return false;
  const types = ['dialog-form','export','navigate','custom'] as const;
  return typeof v.type === 'string' && (types as readonly string[]).includes(v.type);
}

export function isPageMetaBody(v: unknown): v is PageMetaBody {
  if (!isObject(v)) return false;
  if (typeof v.api !== 'string') return false;
  if (!isGridMeta(v.grid)) return false;
  if (!isFormMeta(v.form)) return false;
  if (v.actions !== undefined) {
    if (!Array.isArray(v.actions)) return false;
    if (!v.actions.every(isActionMeta)) return false;
  }
  return true;
}

/** 좁히기 실패 시 명시 에러 */
export class MetaBodyShapeError extends Error {
  constructor(public readonly metaId: string, message: string) {
    super(`[${metaId}] ${message}`);
    this.name = 'MetaBodyShapeError';
  }
}

/** 좁히기 + 실패 시 에러 */
export function asPageMetaBody(metaId: string, v: unknown): PageMetaBody {
  if (!isPageMetaBody(v)) {
    throw new MetaBodyShapeError(metaId, 'metaJson 형태가 PageMetaBody 와 일치하지 않습니다.');
  }
  return v;
}
