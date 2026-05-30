/**
 * FieldMeta → 렌더링할 폼 위젯 종류 매핑(순수 함수).
 * DynamicForm 의 type 분기 로직을 한 곳에 모아 단위 테스트 가능하게 한다.
 * - number+widget:'slider' → slider, textarea+markdown:true → markdown
 * - status/priority 는 options 가 있으면 select, 없으면 input(badge 표시값 직접 입력)
 */
import type { FieldMeta } from '@/types/meta-body';

export type WidgetKind =
  | 'input'
  | 'slider'
  | 'markdown'
  | 'textarea'
  | 'date'
  | 'date-range'
  | 'user-picker'
  | 'file'
  | 'select'
  | 'radio'
  | 'checkbox';

export function widgetFor(f: FieldMeta): WidgetKind {
  const hasOptions = (f.options?.length ?? 0) > 0;
  switch (f.type) {
    case 'number':
      return f.widget === 'slider' ? 'slider' : 'input';
    case 'textarea':
      return f.markdown === true ? 'markdown' : 'textarea';
    case 'date':
      return 'date';
    case 'date-range':
      return 'date-range';
    case 'user-picker':
      return 'user-picker';
    case 'file':
      return 'file';
    case 'select':
      return 'select';
    case 'radio':
      return 'radio';
    case 'checkbox':
      return 'checkbox';
    case 'status':
    case 'priority':
      return hasOptions ? 'select' : 'input';
    case 'text':
    default:
      return 'input';
  }
}
