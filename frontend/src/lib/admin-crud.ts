import type { MenuItem, MenuAdminNode } from '@/types/system';

/**
 * 시스템 관리 페이지 공통 helper.
 * 페이지마다 별도 useApiFetch 를 쓰되(스토어 도입은 별도 ADR), 페이지·검색 URL 빌드와
 * flat 메뉴 → 트리 조립처럼 순수 함수로 분리 가능한 로직만 모은다.
 */

export interface PageQuery {
  page: number;
  size: number;
  kw?: string;
}

/**
 * 목록 조회 URL 빌드 — page/size + kw(공백 제외) + extra 필터(빈 값 제외).
 * usePageData.buildUrl 과 같은 규칙(빈 값 제외)을 따르되, 관리자 검색 파라미터(kw)를 1급으로 둔다.
 */
export function buildAdminQueryUrl(
  base: string,
  q: PageQuery,
  extra: Record<string, string> = {},
): string {
  const p = new URLSearchParams();
  p.set('page', String(q.page));
  p.set('size', String(q.size));
  if (q.kw && q.kw.trim() !== '') p.set('kw', q.kw.trim());
  for (const [k, v] of Object.entries(extra)) {
    if (v !== '' && v !== undefined && v !== null) p.set(k, v);
  }
  return `${base}?${p.toString()}`;
}

/**
 * flat MenuItem 목록을 parentId 기준 트리로 조립 (sortOrder 오름차순).
 * 관리자 메뉴 화면은 /api/menus(flat)를 받아 화면에서 트리로 보여준다.
 */
export function buildMenuTree(items: MenuItem[]): MenuAdminNode[] {
  const byId = new Map<number, MenuAdminNode>();
  for (const m of items) byId.set(m.id, { ...m, children: [] });
  const roots: MenuAdminNode[] = [];
  for (const node of byId.values()) {
    const parent = node.parentId !== null ? byId.get(node.parentId) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  }
  const sortRec = (nodes: MenuAdminNode[]): void => {
    nodes.sort((a, b) => a.sortOrder - b.sortOrder);
    for (const n of nodes) sortRec(n.children);
  };
  sortRec(roots);
  return roots;
}

/**
 * 메뉴 이동 시 자기 자신·자손을 새 부모로 두는 순환을 클라이언트에서 사전 차단.
 * (백엔드도 거부하지만, 잘못된 선택지를 미리 가린다.)
 */
export function isDescendant(
  tree: MenuAdminNode[],
  nodeId: number,
  candidateParentId: number,
): boolean {
  const find = (nodes: MenuAdminNode[], id: number): MenuAdminNode | null => {
    for (const n of nodes) {
      if (n.id === id) return n;
      const found = find(n.children, id);
      if (found) return found;
    }
    return null;
  };
  const node = find(tree, nodeId);
  if (!node) return false;
  const walk = (n: MenuAdminNode): boolean => {
    if (n.id === candidateParentId) return true;
    return n.children.some(walk);
  };
  return node.children.some(walk) || node.id === candidateParentId;
}
