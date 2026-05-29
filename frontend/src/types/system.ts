import type { UserStatus } from './auth';

/**
 * 시스템 관리(사용자·부서·역할·권한·메뉴) 페이지 전용 타입.
 * 백엔드 com.nkia.itg.system.*.dto 레코드와 1:1 대응한다.
 * (인증 세션용 UserSummary 는 types/auth.ts 와 별개 — 목록·상세는 필드 구성이 다르다.)
 */

/** GET /api/users 목록 행 (백엔드 UserSummary). */
export interface UserListItem {
  id: number;
  username: string;
  name: string;
  email: string | null;
  status: UserStatus;
  departmentId: number | null;
  roleCodes: string[];
  lastLoginAt: string | null;
}

/** GET /api/users/{id} 상세 (백엔드 UserResponse). */
export interface UserDetail {
  id: number;
  username: string;
  name: string;
  email: string | null;
  phone: string | null;
  status: UserStatus;
  departmentId: number | null;
  departmentName: string | null;
  roleCodes: string[];
  permissionCodes: string[];
  passwordChangedAt: string | null;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** GET /api/departments/tree 노드 (백엔드 DepartmentTreeNode). */
export interface DeptTreeNode {
  id: number;
  code: string;
  name: string;
  parentId: number | null;
  path: string;
  active: boolean;
  children: DeptTreeNode[];
}

/** GET /api/roles 행 (백엔드 RoleResponse). */
export interface RoleItem {
  id: number;
  code: string;
  name: string;
  description: string | null;
  permissionCodes: string[];
}

/** GET /api/permissions 행 (백엔드 PermissionResponse). */
export interface PermissionItem {
  id: number;
  code: string;
  name: string;
  description: string | null;
}

/** GET /api/menus 행 (백엔드 MenuResponse, flat). 트리는 클라이언트에서 조립. */
export interface MenuItem {
  id: number;
  code: string;
  parentId: number | null;
  label: string;
  icon: string | null;
  sortOrder: number;
  route: string | null;
  groupId: string | null;
  permissionCode: string | null;
  active: boolean;
}

/** MenuItem 을 parentId 로 조립한 트리 노드 (관리자 화면 전용 — 권한 필터 없음). */
export interface MenuAdminNode extends MenuItem {
  children: MenuAdminNode[];
}
