import { Role } from '../enums/role.enum';

export interface EmployeeResponse {
  id: number;
  fullName: string;
  dateOfBirth: string;
  idCardNumber: string;
  phoneNumber: string;
  email: string;
  accountId: number;
  username: string;
  role: Role;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface EmployeeAccountRequest {
  username: string;
  password: string;
  role: Role;
}

export interface EmployeeCreateRequest {
  fullName: string;
  dateOfBirth: string;
  idCardNumber: string;
  phoneNumber: string;
  email: string;
  /** Exactly one of existingAccountId/newAccount must be set. */
  existingAccountId: number | null;
  newAccount: EmployeeAccountRequest | null;
}

export interface EmployeeUpdateRequest {
  fullName: string;
  dateOfBirth: string;
  idCardNumber: string;
  phoneNumber: string;
  email: string;
}
