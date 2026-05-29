/**
 * 자산 분류 트리 + 라이프사이클 이벤트 타입 (PRD §4-3 · ARCHITECTURE §14-3).
 * 백엔드 AssetCategoryTreeNode / AssetLifecycleEventResponse 응답과 1:1 대응한다.
 */

/** 자산 분류 트리 노드 — code 식별, parent_code 자기참조, children 재귀. */
export interface AssetCategoryNode {
  code: string;
  label: string;
  parentCode: string | null;
  path: string;
  formMetaGroupId: string | null;
  sortOrder: number;
  active: boolean;
  children: AssetCategoryNode[];
}

/** 자산 라이프사이클 이벤트 타입(백엔드 AssetLifecycleEventType Enum 과 동일). */
export type LifecycleEventType =
  | 'ACQUIRED'
  | 'TRANSFERRED'
  | 'REPAIRED'
  | 'DISPOSED'
  | 'RENEWED';

/** 자산 라이프사이클 이벤트 — payload 는 이벤트별 부가 정보(JSONB). */
export interface LifecycleEvent {
  id: number;
  assetId: number;
  eventType: LifecycleEventType;
  eventDate: string;
  byUserId: number | null;
  payload: Record<string, unknown> | null;
  createdAt: string;
}
