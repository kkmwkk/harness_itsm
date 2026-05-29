/**
 * 동적 메뉴 트리 노드 (PRD §4-1 Menu · ARCHITECTURE §8-3).
 * `/api/menu` 가 현재 사용자 권한으로 이미 필터한 트리를 반환하므로,
 * 클라이언트는 추가 권한 필터 없이 그대로 렌더링한다.
 */
export interface MenuNode {
  id: number;
  code: string;
  label: string;
  icon: string | null; // lucide 아이콘명 (예: 'TicketCheck')
  route: string | null; // 예: '/itsm'
  groupId: string | null; // PageMeta 그룹 (DynamicPage 진입 키)
  permissionCode: string | null;
  sortOrder: number;
  children: MenuNode[];
}
