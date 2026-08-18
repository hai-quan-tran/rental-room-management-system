import { ContractStatus } from '../enums/contract-status.enum';

export interface ContractResponse {
  id: number;
  roomId: number;
  roomCode: string;
  branchId: number;
  branchName: string;
  startDate: string;
  endDate: string | null;
  monthlyRent: number;
  depositAmount: number;
  status: ContractStatus;
}

export interface TenantInContractResponse {
  tenantId: number;
  fullName: string;
  idCardNumber: string | null;
  phoneNumber: string;
  representative: boolean;
}

export interface ContractDetailResponse {
  contract: ContractResponse;
  tenants: TenantInContractResponse[];
}

export interface ContractTenantRequest {
  tenantId: number;
  representative: boolean;
}

export interface ContractCreateRequest {
  startDate: string;
  endDate: string | null;
  depositAmount: number;
  monthlyRent: number | null;
  tenants: ContractTenantRequest[];
}

export interface ContractEndDateRequest {
  endDate: string | null;
}
