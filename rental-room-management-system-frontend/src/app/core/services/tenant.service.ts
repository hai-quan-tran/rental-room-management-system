import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ListQuery, PageResponse } from '../models/api-response.model';
import { TenantRentalHistoryResponse, TenantRequest, TenantResponse } from '../models/tenant.model';
import { buildListParams } from '../utils/http-params.util';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class TenantService {
  private readonly api = inject(ApiService);

  list(query: ListQuery, search?: string | null): Observable<PageResponse<TenantResponse>> {
    const params = buildListParams(query, { search });
    return this.api.get<PageResponse<TenantResponse>>(`${environment.apiUrl}/tenants`, { params });
  }

  get(id: number): Observable<TenantResponse> {
    return this.api.get<TenantResponse>(`${environment.apiUrl}/tenants/${id}`);
  }

  rentalHistory(id: number): Observable<TenantRentalHistoryResponse[]> {
    return this.api.get<TenantRentalHistoryResponse[]>(`${environment.apiUrl}/tenants/${id}/rental-history`);
  }

  create(request: TenantRequest): Observable<TenantResponse> {
    return this.api.post<TenantResponse>(`${environment.apiUrl}/tenants`, request);
  }

  update(id: number, request: TenantRequest): Observable<TenantResponse> {
    return this.api.put<TenantResponse>(`${environment.apiUrl}/tenants/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.api.delete<void>(`${environment.apiUrl}/tenants/${id}`);
  }
}
