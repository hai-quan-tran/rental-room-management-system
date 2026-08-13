import { Role } from '../enums/role.enum';

export interface AccountResponse {
  id: number;
  /** Not collected on the Account screen anymore — set only via the linked Employee record, if any. */
  fullName: string | null;
  username: string;
  role: Role;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AccountCreateRequest {
  username: string;
  password: string;
  role: Role;
}

export interface AccountUpdateRequest {
  username: string;
  role: Role;
}
