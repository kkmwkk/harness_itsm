export type SystemType = 'ITSM' | 'ITAM' | 'PMS' | 'COMMON' | 'SYSTEM';
export type PackageType = 'PACKAGE' | 'CUSTOM';
export type MetaStatus = 'DRAFT' | 'PUBLISHED' | 'DEPRECATED' | 'ARCHIVED';

export type PageMetaBody = Record<string, unknown>;

export interface PageMeta {
  id: string;
  title: string;
  systemType: SystemType;
  packageType: PackageType;
  groupId: string;
  majorVersion: number;
  minorVersion: number;
  metaStatus: MetaStatus;
  metaJson: PageMetaBody;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PageMetaVersion {
  id: string;
  groupId: string;
  majorVersion: number;
  minorVersion: number;
  metaStatus: MetaStatus;
  createdAt: string;
  updatedAt: string;
}

/** group_id 단위 요약 — No-code 편집기(M9) 좌측 목록. `GET /api/meta/groups` */
export interface PageMetaGroup {
  groupId: string;
  title: string;
  systemType: SystemType;
  packageType: PackageType;
  /** 최신 PUBLISHED 메타 ID (없으면 null) */
  publishedId: string | null;
  /** 최신 PUBLISHED 버전 라벨 v{major}.{minor} (없으면 null) */
  publishedVersion: string | null;
  /** 편집 가능한 DRAFT 버전 존재 여부 */
  hasDraft: boolean;
  /** 그룹 내 전체 버전 수 */
  versionCount: number;
}

export interface ApiEnvelope<T> {
  success: boolean;
  data: T | null;
  message: string | null;
  errorCode: string | null;
}
