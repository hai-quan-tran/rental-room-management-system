import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { RoomStatus } from '../enums/room-status.enum';
import { ListQuery, PageResponse } from '../models/api-response.model';
import { BranchOption, RoomDetailResponse, RoomRequest, RoomResponse } from '../models/room.model';
import { buildListParams } from '../utils/http-params.util';
import { ApiService } from './api.service';

export interface RoomListFilter {
  status?: RoomStatus | null;
  roomTypeId?: number | null;
  search?: string | null;
}

@Injectable({ providedIn: 'root' })
export class RoomService {
  private readonly api = inject(ApiService);

  list(branchId: number, query: ListQuery, filter: RoomListFilter = {}): Observable<PageResponse<RoomResponse>> {
    const params = buildListParams(query, filter);
    return this.api.get<PageResponse<RoomResponse>>(`${environment.apiUrl}/branches/${branchId}/rooms`, { params });
  }

  /** Branches the current user may operate rooms in — both roles (unlike the ADMIN_TONG-only Branch screen). */
  branchOptions(): Observable<BranchOption[]> {
    return this.api.get<BranchOption[]>(`${environment.apiUrl}/rooms/branch-options`);
  }

  get(id: number): Observable<RoomDetailResponse> {
    return this.api.get<RoomDetailResponse>(`${environment.apiUrl}/rooms/${id}`);
  }

  create(branchId: number, request: RoomRequest): Observable<RoomResponse> {
    return this.api.post<RoomResponse>(`${environment.apiUrl}/branches/${branchId}/rooms`, request);
  }

  update(id: number, request: RoomRequest): Observable<RoomResponse> {
    return this.api.put<RoomResponse>(`${environment.apiUrl}/rooms/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.api.delete<void>(`${environment.apiUrl}/rooms/${id}`);
  }
}
