export type BillSyncStatus = 'NO_BILL_YET' | 'UPDATED' | 'SKIPPED_CONFIRMED' | 'SKIPPED_AMBIGUOUS_CONTRACT';

/** 1 metered category's reading state for 1 room+month in the "Nhập chỉ số điện nước" grid. */
export interface MeterReadingCellResponse {
  extraFeeCategoryId: number;
  categoryName: string;
  unit: string | null;
  previousReading: number | null;
  currentReading: number | null;
  consumption: number | null;
  unitPrice: number | null;
  amount: number | null;
  note: string | null;
  billLocked: boolean;
}

export interface MeterReadingGridRowResponse {
  roomId: number;
  roomCode: string;
  contractId: number;
  readings: MeterReadingCellResponse[];
}

export interface MeterReadingRequest {
  extraFeeCategoryId: number;
  billMonth: number;
  billYear: number;
  /** Only required for a room+category's first-ever reading; otherwise auto-chained from the previous reading. */
  oldReading: number | null;
  newReading: number;
  note: string | null;
}

export interface MeterReadingResponse {
  id: number;
  roomId: number;
  extraFeeCategoryId: number;
  billYear: number;
  billMonth: number;
  oldReading: number;
  newReading: number;
  consumption: number;
  unitPrice: number | null;
  amount: number;
  billSyncStatus: BillSyncStatus;
  note: string | null;
}
