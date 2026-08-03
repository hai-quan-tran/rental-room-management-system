import { Role } from '../enums/role.enum';

export interface AccountResponse {
  id: number;
  fullName: string;
  username: string;
  role: Role;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AccountCreateRequest {
  fullName: string;
  username: string;
  password: string;
  role: Role;
}

export interface AccountUpdateRequest {
  fullName: string;
  username: string;
  role: Role;
}
