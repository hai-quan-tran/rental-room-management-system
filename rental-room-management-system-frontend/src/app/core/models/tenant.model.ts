import { ContractStatus } from '../enums/contract-status.enum';

export interface TenantResponse {
  id: number;
  fullName: string;
  dateOfBirth: string;
  idCardNumber: string;
  phoneNumber: string;
  email: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TenantRequest {
  fullName: string;
  dateOfBirth: string;
  idCardNumber: string;
  phoneNumber: string;
  email: string | null;
}

export interface TenantRentalHistoryResponse {
  contractId: number;
  branchName: string;
  roomCode: string;
  startDate: string;
  endDate: string | null;
  status: ContractStatus;
  depositAmount: number;
  representative: boolean;
}
