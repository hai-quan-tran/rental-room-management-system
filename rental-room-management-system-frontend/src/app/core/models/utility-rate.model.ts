export interface UtilityRateResponse {
  id: number;
  branchId: number;
  extraFeeCategoryId: number;
  extraFeeCategoryName: string;
  unit: string | null;
  unitPrice: number;
  effectiveFrom: string;
  createdAt: string;
}

export interface UtilityRateRequest {
  extraFeeCategoryId: number;
  unitPrice: number;
  effectiveFrom: string;
}
