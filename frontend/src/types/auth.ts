export type UserStatus = 'ACTIVE' | 'LOCKED' | 'RETIRED';

export interface UserSummary {
  id: number;
  username: string;
  name: string;
  email: string | null;
  departmentName: string | null;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  accessExpiresInSec: number;
  user: UserSummary;
  roles: string[];
  permissions: string[];
}

export interface MeResponse {
  user: UserSummary;
  roles: string[];
  permissions: string[];
}
