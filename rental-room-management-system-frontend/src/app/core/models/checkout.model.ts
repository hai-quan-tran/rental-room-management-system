import { ChecklistItemStatus } from '../enums/checklist-item-status.enum';
import { DebtRecordResponse } from './debt-record.model';

export interface CheckoutItemRequest {
  roomTypeHandoverItemId: number;
  status: ChecklistItemStatus;
  deductionAmount: number;
  note: string | null;
}

export interface CheckoutRequest {
  checkoutDate: string;
  note: string | null;
  items: CheckoutItemRequest[];
}

export interface CheckoutChecklistItemResponse {
  id: number;
  roomTypeHandoverItemId: number;
  itemName: string;
  status: ChecklistItemStatus;
  deductionAmount: number;
  note: string | null;
}

export interface CheckoutResponse {
  checklistId: number;
  contractId: number;
  checkedAt: string;
  depositAmount: number;
  deductionAmount: number;
  depositRefundAmount: number;
  note: string | null;
  items: CheckoutChecklistItemResponse[];
  debtRecord: DebtRecordResponse | null;
}
