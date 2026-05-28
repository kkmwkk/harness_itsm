/**
 * 메타 본문(PageMeta.metaJson) 의 강타입 정의.
 * ARCHITECTURE §3-4(본문 예시) · §5(필드 타입 매핑) 와 1:1 대응한다.
 * 모든 정의는 직렬화 가능(string·number·boolean·단순 객체)해야 한다 — 함수 참조 금지.
 */

/** 필드 타입 — ARCHITECTURE §5 와 1:1 일치 */
export type FieldType =
  | 'text' | 'textarea' | 'number'
  | 'select' | 'radio' | 'checkbox'
  | 'date' | 'date-range'
  | 'user-picker' | 'file'
  | 'status' | 'priority';

/** 폼 필드 한 칸 정의 */
export interface FieldMeta {
  name:        string;            // form 모델의 key
  label:       string;
  type:        FieldType;
  required?:   boolean;
  /** 폼 그리드의 column span (예: 2-column 폼에서 span:2 면 전체 폭) */
  span?:       1 | 2;
  /** placeholder · helpText 등 부가 정보 */
  placeholder?: string;
  helpText?:    string;
  /** select/radio 의 정적 옵션 또는 동적 옵션 API */
  options?:    Array<{ value: string; label: string }>;
  optionsApi?: string;            // 예: '/api/codes/category'
  /** number/text 의 검증 */
  maxLength?:  number;
  min?:        number;
  max?:        number;
  pattern?:    string;            // RegExp 문자열
}

/** 폼 전체 정의 */
export interface FormMeta {
  layout: 'single-column' | 'two-column';
  fields: FieldMeta[];
}

/** 그리드 컬럼 한 개 */
export interface GridColumnMeta {
  field:   string;                // 행 데이터의 key
  label:   string;
  type:    FieldType;             // 셀 렌더링도 동일 매핑 사용
  width?:  number;                // px
  flex?:   number;                // flex 사용 시 width 무시
  pinned?: 'left' | 'right';
  /** md 이하에서 자동 숨김 (UI_GUIDE §6-3) */
  hideAt?: 'sm' | 'md';
}

/** 그리드 전체 정의 */
export interface GridMeta {
  columns:    GridColumnMeta[];
  /** 인라인 편집 활성화 (true 면 AG Grid 강제) */
  inlineEdit?: boolean;
  /** 엑셀 export 활성화 (true 면 AG Grid 강제) */
  export?:     boolean;
}

/** 액션 버튼 (PageHeader 우측, 그리드 툴바 등) */
export interface ActionMeta {
  id:    string;
  label: string;
  type:  'dialog-form' | 'export' | 'navigate' | 'custom';
  /** type='navigate' 일 때 라우트, type='dialog-form' 일 때 form 메타 참조 */
  to?:   string;
}

/** PageMeta.metaJson 의 강타입 형태 */
export interface PageMetaBody {
  /** 데이터 API 베이스 (예: '/api/tickets') */
  api:      string;
  grid:     GridMeta;
  form:     FormMeta;
  /** 상세 페이지 메타 (옵션) */
  detail?:  { fields: FieldMeta[] };
  actions?: ActionMeta[];
}
