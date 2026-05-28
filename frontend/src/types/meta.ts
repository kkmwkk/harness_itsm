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

export interface ApiEnvelope<T> {
  success: boolean;
  data: T | null;
  message: string | null;
  errorCode: string | null;
}
