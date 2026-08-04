import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ListQuery, PageResponse } from '../models/api-response.model';
import { ItemOption, ItemRequest, ItemResponse } from '../models/item.model';
import { buildListParams } from '../utils/http-params.util';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class ItemService {
  private readonly api = inject(ApiService);

  list(branchId: number, query: ListQuery, search?: string | null): Observable<PageResponse<ItemResponse>> {
    const params = buildListParams(query, { search });
    return this.api.get<PageResponse<ItemResponse>>(`${environment.apiUrl}/branches/${branchId}/items`, { params });
  }

  /** Lightweight list for the Room Type handover-item picker. */
  listAll(branchId: number): Observable<ItemOption[]> {
    return this.api.get<ItemOption[]>(`${environment.apiUrl}/branches/${branchId}/items/all`);
  }

  get(id: number): Observable<ItemResponse> {
    return this.api.get<ItemResponse>(`${environment.apiUrl}/items/${id}`);
  }

  create(branchId: number, request: ItemRequest): Observable<ItemResponse> {
    return this.api.post<ItemResponse>(`${environment.apiUrl}/branches/${branchId}/items`, request);
  }

  update(id: number, request: ItemRequest): Observable<ItemResponse> {
    return this.api.put<ItemResponse>(`${environment.apiUrl}/items/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.api.delete<void>(`${environment.apiUrl}/items/${id}`);
  }
}
