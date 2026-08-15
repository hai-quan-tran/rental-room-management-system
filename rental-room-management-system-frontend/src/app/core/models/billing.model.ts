import { PaymentStatus } from '../enums/payment-status.enum';

export interface MonthlyBillResponse {
  id: number;
  contractId: number;
  billMonth: number;
  billYear: number;
  rentAmount: number;
  totalExtraFee: number;
  totalAmount: number;
  paidAmount: number;
  remainingAmount: number;
  paymentStatus: PaymentStatus;
}

export interface MonthlyBillCreateRequest {
  billMonth: number;
  billYear: number;
}

export interface MonthlyBillListItem {
  id: number;
  contractId: number;
  roomId: number;
  roomCode: string;
  branchId: number;
  branchName: string;
  billMonth: number;
  billYear: number;
  rentAmount: number;
  totalExtraFee: number;
  totalAmount: number;
  paidAmount: number;
  remainingAmount: number;
  paymentStatus: PaymentStatus;
}

export interface ExtraFeeItemResponse {
  id: number;
  extraFeeCategoryId: number;
  extraFeeCategoryName: string;
  amount: number;
  note: string | null;
}

export interface ExtraFeeItemRequest {
  extraFeeCategoryId: number;
  amount: number;
  note: string | null;
}

export interface PaymentResponse {
  id: number;
  amount: number;
  paymentDate: string;
  method: string | null;
  note: string | null;
  createdAt: string;
}

export interface PaymentRequest {
  amount: number;
  paymentDate: string;
  method: string | null;
  note: string | null;
}

export interface MonthlyBillDetailResponse {
  bill: MonthlyBillResponse;
  extraFeeItems: ExtraFeeItemResponse[];
  payments: PaymentResponse[];
}

export interface BulkMonthlyBillCreateRequest {
  billMonth: number;
  billYear: number;
  branchId: number | null;
}

export interface BulkMonthlyBillCreateResult {
  createdBills: MonthlyBillListItem[];
  alreadyExistsCount: number;
  notApplicableCount: number;
}

export interface BulkBillConfirmResult {
  confirmedBills: MonthlyBillListItem[];
  skippedCount: number;
}
