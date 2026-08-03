import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ListQuery, PageResponse } from '../models/api-response.model';
import { BranchDetailResponse, BranchRequest, BranchResponse } from '../models/branch.model';
import { buildListParams } from '../utils/http-params.util';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class BranchService {
  private readonly api = inject(ApiService);

  list(query: ListQuery, search?: string | null): Observable<PageResponse<BranchResponse>> {
    const params = buildListParams(query, { search });
    return this.api.get<PageResponse<BranchResponse>>(`${environment.apiUrl}/branches`, { params });
  }

  /** Small options list for dropdowns (e.g. the branch picker on the Rooms screen). */
  listOptions(): Observable<BranchResponse[]> {
    return this.list({ page: 0, size: 100 }).pipe(map((page) => page.content));
  }

  get(id: number): Observable<BranchDetailResponse> {
    return this.api.get<BranchDetailResponse>(`${environment.apiUrl}/branches/${id}`);
  }

  create(request: BranchRequest): Observable<BranchResponse> {
    return this.api.post<BranchResponse>(`${environment.apiUrl}/branches`, request);
  }

  update(id: number, request: BranchRequest): Observable<BranchResponse> {
    return this.api.put<BranchResponse>(`${environment.apiUrl}/branches/${id}`, request);
  }
}
