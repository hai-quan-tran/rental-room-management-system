import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ListQuery, PageResponse } from '../models/api-response.model';
import {
  BulkMonthlyBillCreateRequest,
  BulkMonthlyBillCreateResult,
  ExtraFeeItemRequest,
  ExtraFeeItemResponse,
  MonthlyBillCreateRequest,
  MonthlyBillDetailResponse,
  MonthlyBillListItem,
  MonthlyBillResponse,
  PaymentRequest,
} from '../models/billing.model';
import { buildListParams } from '../utils/http-params.util';
import { ApiService } from './api.service';

export interface MonthlyBillListFilter {
  billYear: number;
  billMonth: number;
  branchId?: number | null;
}

@Injectable({ providedIn: 'root' })
export class BillingService {
  private readonly api = inject(ApiService);

  listByContract(contractId: number): Observable<MonthlyBillResponse[]> {
    return this.api.get<MonthlyBillResponse[]>(`${environment.apiUrl}/contracts/${contractId}/monthly-bills`);
  }

  listAll(query: ListQuery, filter: MonthlyBillListFilter): Observable<PageResponse<MonthlyBillListItem>> {
    const params = buildListParams(query, filter);
    return this.api.get<PageResponse<MonthlyBillListItem>>(`${environment.apiUrl}/monthly-bills`, { params });
  }

  createBill(contractId: number, request: MonthlyBillCreateRequest): Observable<MonthlyBillResponse> {
    return this.api.post<MonthlyBillResponse>(`${environment.apiUrl}/contracts/${contractId}/monthly-bills`, request);
  }

  bulkCreate(request: BulkMonthlyBillCreateRequest): Observable<BulkMonthlyBillCreateResult> {
    return this.api.post<BulkMonthlyBillCreateResult>(`${environment.apiUrl}/monthly-bills/bulk`, request);
  }

  get(id: number): Observable<MonthlyBillDetailResponse> {
    return this.api.get<MonthlyBillDetailResponse>(`${environment.apiUrl}/monthly-bills/${id}`);
  }

  addExtraFeeItem(id: number, request: ExtraFeeItemRequest): Observable<ExtraFeeItemResponse> {
    return this.api.post<ExtraFeeItemResponse>(`${environment.apiUrl}/monthly-bills/${id}/extra-fee-items`, request);
  }

  deleteExtraFeeItem(id: number, itemId: number): Observable<void> {
    return this.api.delete<void>(`${environment.apiUrl}/monthly-bills/${id}/extra-fee-items/${itemId}`);
  }

  recordPayment(id: number, request: PaymentRequest): Observable<MonthlyBillResponse> {
    return this.api.post<MonthlyBillResponse>(`${environment.apiUrl}/monthly-bills/${id}/payments`, request);
  }
}
