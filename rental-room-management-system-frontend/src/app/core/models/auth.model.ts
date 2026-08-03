import { Role } from '../enums/role.enum';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

/** Matches JwtTokenProvider's claim set — userId is the standard `sub` claim, not a custom claim. */
export interface JwtPayload {
  sub: string;
  username: string;
  role: Role;
  branchIds: number[];
  exp: number;
  iat: number;
}

export interface CurrentUser {
  userId: number;
  username: string;
  fullName: string;
  role: Role;
  branchIds: number[];
}
