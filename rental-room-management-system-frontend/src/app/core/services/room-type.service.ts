import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ListQuery, PageResponse } from '../models/api-response.model';
import { RoomTypeOption } from '../models/room.model';
import { HandoverItemRequest, HandoverItemResponse, RoomTypeRequest, RoomTypeResponse } from '../models/room-type.model';
import { buildListParams } from '../utils/http-params.util';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class RoomTypeService {
  private readonly api = inject(ApiService);

  /** Room types belong to exactly 1 branch — every read/write is scoped under that branch. */
  listAll(branchId: number): Observable<RoomTypeOption[]> {
    return this.api.get<RoomTypeOption[]>(`${environment.apiUrl}/branches/${branchId}/room-types/all`);
  }

  list(branchId: number, query: ListQuery, search?: string | null): Observable<PageResponse<RoomTypeResponse>> {
    const params = buildListParams(query, { search });
    return this.api.get<PageResponse<RoomTypeResponse>>(`${environment.apiUrl}/branches/${branchId}/room-types`, {
      params,
    });
  }

  get(id: number): Observable<RoomTypeResponse> {
    return this.api.get<RoomTypeResponse>(`${environment.apiUrl}/room-types/${id}`);
  }

  create(branchId: number, request: RoomTypeRequest): Observable<RoomTypeResponse> {
    return this.api.post<RoomTypeResponse>(`${environment.apiUrl}/branches/${branchId}/room-types`, request);
  }

  update(id: number, request: RoomTypeRequest): Observable<RoomTypeResponse> {
    return this.api.put<RoomTypeResponse>(`${environment.apiUrl}/room-types/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.api.delete<void>(`${environment.apiUrl}/room-types/${id}`);
  }

  replaceHandoverItems(id: number, items: HandoverItemRequest[]): Observable<HandoverItemResponse[]> {
    return this.api.put<HandoverItemResponse[]>(`${environment.apiUrl}/room-types/${id}/handover-items`, items);
  }
}
